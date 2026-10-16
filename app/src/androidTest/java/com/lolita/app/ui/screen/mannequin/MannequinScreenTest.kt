package com.lolita.app.ui.screen.mannequin

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Compose state transitions; external image service and credentials are the only controlled boundaries. */
@RunWith(AndroidJUnit4::class)
class MannequinScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var store: ViewModelStore
    private lateinit var directory: File
    private lateinit var vm: MannequinViewModel
    private lateinit var photos: List<String>
    private var key: String? = null
    private var provider = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI
    private var generated = CompletableDeferred<String>()
    private var calls = 0
    private var appended: String? = null
    private var discarded: String? = null
    private var navigated = 0
    private var blocked = false
    private val presentation = mutableStateOf(Presentation())

    @Before fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "mannequin-ui-${UUID.randomUUID()}").apply { mkdirs() }
        photos = (1..3).map { index -> File(directory, "$index.png").also { file ->
            Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(if (index == 1) android.graphics.Color.MAGENTA else android.graphics.Color.BLUE)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }.absolutePath }
        store = ViewModelStore()
        instrumentation.runOnMainSync {
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 11L)),
                item = flowOf(Item(id = 11, name = "月光心愿裙", brandId = 1, categoryId = 1, description = "", status = ItemStatus.WISHED, imageUrls = photos.take(2))),
                categories = flowOf(listOf(Category(id = 1, name = "OP"))),
                readService = { com.lolita.app.data.tryon.ImageServiceStatus(provider, key != null) }, selectService = { provider = it }, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value -> key = value }, clearApiKey = { _ -> key = null },
                tasks = flowOf(emptyList()), submitTask = { _, _ -> calls++; generated.await() })
            store.put("vm", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; directory.deleteRecursively() }
    private fun show() {
        compose.setContent {
            val display = presentation.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = display.width
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (display.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, display.scale)) {
                LolitaTheme(skinType = display.skin, darkTheme = display.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(display.width.dp, 640.dp)) {
                            MannequinScreen(onNavigateBack = { navigated++ }, viewModel = vm)
                        }
                    }
                }
            }
        }
    }
    private fun scroll(text: String) {
        compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo()
    }
    private fun configure() {
        compose.onNodeWithText("配置 API Key").performClick()
        compose.onNodeWithText("API Key", substring = false).performScrollTo().performTextInput("sk-ui-private")
        compose.onNodeWithText("保存密钥").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }
    }
    @Test fun seedreamSelectionUsesMaskedKeyCorrectRecipientAndSevenSkinControls() {
        show()
        scroll("即梦 5.0 Pro")
        compose.onNodeWithTag("imageService-SEEDREAM").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.provider == com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM && !vm.uiState.value.busy }
        compose.onNodeWithTag("imageService-SEEDREAM").assertIsSelected()
        compose.onNodeWithTag("imageService-OPENAI").assertIsNotSelected()
        compose.onNodeWithText("配置 API Key").performClick()
        compose.onNodeWithText("API Key", substring = false).performTextInput("private-seed-test")
        compose.onNodeWithText("API Key", substring = false).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("保存密钥").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.busy }
        compose.onNodeWithText("上传所选原图、固定模板及补充说明至 火山方舟，共2张图片；API 单独计费。").assertIsDisplayed()
        val output = File(instrumentation.targetContext.filesDir, "seedream-service-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            scroll("即梦 5.0 Pro")
            compose.onNodeWithTag("imageService-SEEDREAM").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithTag("imageService-OPENAI").assertIsDisplayed().assertIsNotSelected()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun sharedKeyDialogMasksInputNeverRefillsAndClearUpdatesConfiguration() {
        show()
        compose.onNodeWithText("配置 API Key").performClick()
        compose.onNodeWithText("API Key", substring = false).performScrollTo().performTextInput("sk-secret")
        compose.onNodeWithText("API Key", substring = false).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("保存密钥").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }
        scroll("服务配置"); compose.onNodeWithText("服务配置").performClick()
        compose.onNodeWithText("API Key", substring = false).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("删除本机密钥").performScrollTo().performClick()
        compose.waitUntil(10_000) { !vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }; assertNull(key)
    }
    @Test fun selectionAndInstructionsSubmitOnlyAfterPaidConfirmationAndBackRemainsAvailable() {
        show(); configure()
        scroll("参考图片 2"); compose.onNodeWithText("参考图片 2").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.selectedImage == photos[1] }
        scroll("补充说明（可选）"); compose.onNodeWithText("补充说明（可选）").performTextInput("排除独立手袖")
        compose.onNodeWithText("生成正面人台图").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.isSubmitting }
        generated.complete("task-uuid")
        compose.waitUntil(10_000) { vm.uiState.value.latestTaskId == "task-uuid" && !vm.uiState.value.isSubmitting }
        assertEquals(1, calls)
        scroll("已加入任务，可离开页面")
        compose.onNodeWithText("查看任务").assertHasClickAction()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, navigated)
    }
    @Test fun legacyPreviewCanBeZoomedAndExplicitlySavedWithoutNetworkOrLeavingCleanup() {
        instrumentation.runOnMainSync {
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 11L, "mannequinPreview" to photos[2], "mannequinPreviewItem" to 11L)),
                item = flowOf(Item(id = 11, name = "旧版裙子", brandId = 1, categoryId = 1, description = "", status = ItemStatus.OWNED, imageUrls = photos.take(2))),
                categories = flowOf(listOf(Category(id = 1, name = "OP"))), readService = { com.lolita.app.data.tryon.ImageServiceStatus(provider, false) },
                selectService = {}, serviceChanges = flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = flowOf(emptyList()),
                validateLegacy = { true }, appendLegacy = { _, path -> appended = path }, discardLegacy = { discarded = it }, submitTask = { _, _ -> error("legacy must not pay") })
            store.put("legacy", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading }
        show(); scroll("添加旧版预览到裙子图片")
        compose.onNodeWithContentDescription("放大旧版预览").performClick()
        compose.onNodeWithContentDescription("关闭图片预览").performClick()
        compose.onNodeWithText("添加旧版预览到裙子图片").performClick()
        compose.waitUntil(10_000) { appended == photos[2] && !vm.uiState.value.savingLegacy }
        assertNull(discarded); assertEquals(0, calls)
    }

    @Test fun sevenSkinsLightDarkLargeFontKeepTaskAndSubmitControlsReachable() {
        show(); configure(); generated.complete("task-uuid")
        compose.onNodeWithText("生成正面人台图").performClick(); compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.latestTaskId != null && !vm.uiState.value.isSubmitting }
        val output = File(instrumentation.targetContext.filesDir, "mannequin-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            scroll("已加入任务，可离开页面")
            compose.onNodeWithText("查看任务").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithContentDescription("任务中心").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("生成正面人台图").assertIsDisplayed().assertIsEnabled()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private data class Presentation(val width: Int = 320, val scale: Float = 2f, val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
