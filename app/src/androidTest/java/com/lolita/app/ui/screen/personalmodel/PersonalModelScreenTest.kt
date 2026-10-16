package com.lolita.app.ui.screen.personalmodel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.PersonalModelDraft
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import com.lolita.app.data.local.entity.PersonalModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Bitmap

/** Real screens with in-memory draft boundaries; no paid service is called. */
@RunWith(AndroidJUnit4::class)
class PersonalModelScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private lateinit var editor: PersonalModelEditViewModel
    private var draft: PersonalModelDraft? = null
    private var submitted = 0
    private var destination: String? = null
    private val display = mutableStateOf(Display())
    private data class Display(val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false, val width: Int = 360, val scale: Float = 1f)
    @Before fun setup() {
        instrumentation.runOnMainSync {
            editor = PersonalModelEditViewModel(SavedStateHandle(), readDraft = { draft }, saveDraft = { draft = it }, deleteDraft = { draft = null },
                readModel = { null }, readRetryTask = { null }, readService = { ImageServiceStatus(ImageGenerationProvider.SEEDREAM, true) },
                serviceChanges = flowOf(0), selectService = {}, saveApiKey = { _, _ -> }, clearApiKey = {},
                submitTask = { request, paths, _ -> assertEquals(PersonalModelMode.PARAMETERS, request.mode); assertTrue(paths.isEmpty()); submitted++; "new-personal-task" },
                imageReadable = { true }, deleteImage = {})
            store.put("editor", editor)
        }
        compose.waitUntil(10_000) { !editor.uiState.value.isLoading && editor.uiState.value.keyLoaded }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() } }
    private fun show() {
        compose.setContent {
            val d = display.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = d.width
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (d.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, d.scale)) {
                LolitaTheme(skinType = d.skin, darkTheme = d.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.size(d.width.dp, 760.dp)) {
                        PersonalModelEditScreen(onBack = {}, onTask = { destination = it }, viewModel = editor)
                    } }
                }
            }
        }
    }
    private fun scroll(text: String) { compose.onNodeWithTag("personalModelEditor").performScrollToNode(hasText(text)) }
    @Test fun parameterEntryRequiresValidMeasurementsThenConfirmsBeforeSingleTaskSubmission() {
        show()
        compose.onNodeWithTag("generatePersonalModel").assertIsNotEnabled()
        compose.onNodeWithText("仅用参数").performClick()
        scroll("身高（必填）")
        compose.onNodeWithText("身高（必填）").performTextInput("163")
        scroll("体重（必填）")
        compose.onNodeWithText("体重（必填）").performTextInput("52")
        scroll("模特名称（1至60字）")
        compose.onNodeWithText("模特名称（1至60字）").performTextInput("我的日常模特")
        compose.waitUntil(10_000) { editor.uiState.value.canGenerate }
        compose.onNodeWithTag("generatePersonalModel").performClick()
        assertEquals(0, submitted)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { destination != null }
        assertEquals("new-personal-task", destination)
        assertEquals(1, submitted)
    }
    @Test fun libraryKeepsBuiltinChoiceAndCanSelectRenameAndDeleteConfirmedModel() {
        val request = PersonalModelRequest("我的日常模特", PersonalModelMode.PARAMETERS, BodyMeasurements(163.0, 52.0))
        val rows = MutableStateFlow(listOf(PersonalModel(id = 1, name = request.name, requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = "/test-fixture-not-a-generated-result", provider = ImageGenerationProvider.SEEDREAM, model = "model")))
        val preferred = MutableStateFlow<Long?>(null)
        lateinit var library: PersonalModelViewModel
        compose.runOnIdle {
            library = PersonalModelViewModel(rows, preferred, setDefault = { preferred.value = it },
                renameModel = { id, name -> rows.value = rows.value.map { if (it.id == id) it.copy(name = name) else it } },
                deleteModel = { id -> rows.value = rows.value.filterNot { it.id == id } })
            store.put("library", library)
        }
        compose.setContent { LolitaTheme { PersonalModelScreen(onBack = {}, onCreate = {}, onAdjust = {}, viewModel = library) } }
        compose.waitUntil(10_000) { !library.uiState.value.isLoading }
        compose.onNodeWithText("正在使用内置模特").assertExists()
        compose.onNodeWithTag("personalModelLibrary").performScrollToNode(hasText("设为试衣模特"))
        compose.onNodeWithText("设为试衣模特").performClick()
        compose.waitUntil(10_000) { preferred.value == 1L }
        compose.onNodeWithTag("personalModelLibrary").performScrollToNode(hasText("重命名"))
        compose.onNodeWithText("重命名").performClick()
        compose.onNodeWithText("名称（1至60字）").performTextClearance()
        compose.onNodeWithText("名称（1至60字）").performTextInput("新的名字")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { rows.value.single().name == "新的名字" }
        compose.onNodeWithTag("personalModelLibrary").performScrollToNode(hasText("删除"))
        compose.onNodeWithText("删除").performClick()
        compose.onAllNodesWithText("删除").onLast().performClick()
        compose.waitUntil(10_000) { rows.value.isEmpty() && !library.uiState.value.mutating }
        assertNull(preferred.value)
    }

    @Test fun allSevenSkinsRenderEditorInLightDarkAnd320dpLargeFont() {
        show()
        compose.runOnIdle { editor.update { it.copy(mode = PersonalModelMode.PARAMETERS, name = "我的日常模特", heightCm = "163", weightKg = "52") } }
        compose.waitUntil(10_000) { editor.uiState.value.canGenerate }
        val output = File(instrumentation.targetContext.filesDir, "personal-model-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) for (compact in listOf(false, true)) {
            compose.runOnIdle { display.value = Display(skin, dark, if (compact) 320 else 360, if (compact) 1.5f else 1f) }
            compose.waitForIdle()
            compose.onNodeWithTag("generatePersonalModel").assertIsDisplayed().assertIsEnabled()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "editor-${skin.name.lowercase()}-${if (dark) "dark" else "light"}-${if (compact) "320-large" else "360"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
