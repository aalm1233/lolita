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
                hasApiKey = { key != null }, saveApiKey = { key = it }, clearApiKey = { key = null },
                globalGenerating = MutableStateFlow(false), generateImage = { _, phase -> calls++; phase(com.lolita.app.data.tryon.TryOnGenerationPhase.GENERATING); generated.await() },
                appendImage = { _, path -> appended = path }, validatePreview = { true }, discardImage = { discarded = it })
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
                            MannequinScreen(onNavigateBack = { navigated++ }, viewModel = vm, onNavigationBlockedChanged = { blocked = it })
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
    @Test fun selectionGenerationPreviewZoomAndExplicitSaveUseTheSamePendingPhoto() {
        show(); configure()
        scroll("参考图片 2"); compose.onNodeWithText("参考图片 2").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.uiState.value.selectedImage == photos[1] }
        assertEquals(photos[1], vm.uiState.value.selectedImage)
        scroll("补充说明（可选）"); compose.onNodeWithText("补充说明（可选）").performTextInput("排除独立手袖")
        compose.onNodeWithText("生成正面人台图").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.isGenerating && blocked }
        generated.complete(photos[2]); compose.waitUntil(10_000) { vm.uiState.value.previewPath != null }
        assertEquals(1, calls); assertNull(appended)
        compose.onNodeWithText("添加到裙子图片").assertIsDisplayed().assertIsEnabled()
        scroll("点击放大核对人台图"); compose.onNodeWithContentDescription("放大正面人台图").performClick()
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed().performClick()
        compose.onNodeWithText("添加到裙子图片").performClick()
        compose.waitUntil(10_000) { appended != null && navigated == 1 }
        assertEquals(photos[2], appended); assertNull(discarded)
    }
    @Test fun backDuringGenerationAndPreviewRequiresExplicitCancelOrDiscard() {
        show(); configure(); compose.onNodeWithText("生成正面人台图").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.isGenerating }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续等待").performClick(); assertEquals(0, navigated)
        compose.onNodeWithText("取消等待").performClick()
        compose.onAllNodesWithText("取消等待").onLast().performClick()
        compose.waitUntil(10_000) { !vm.uiState.value.isGenerating }; assertEquals(1, calls)
        generated = CompletableDeferred(); compose.onNodeWithText("生成正面人台图").performClick(); generated.complete(photos[2])
        compose.waitUntil(10_000) { vm.uiState.value.previewPath != null }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续核对").performClick(); assertEquals(0, navigated)
        compose.onNodeWithContentDescription("返回").performClick(); compose.onNodeWithText("放弃并返回").performClick()
        compose.waitUntil(10_000) { navigated == 1 }; assertEquals(photos[2], discarded); assertNull(appended)
    }
    @Test fun sevenSkinsLightDarkLargeFontKeepPreviewActionsReachableAndExportRealScreens() {
        show(); configure(); compose.onNodeWithText("生成正面人台图").performClick(); generated.complete(photos[2])
        compose.waitUntil(10_000) { vm.uiState.value.previewPath != null }
        val output = File(instrumentation.targetContext.filesDir, "mannequin-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            compose.onNodeWithText("添加到裙子图片").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("放弃预览").assertIsDisplayed().assertHasClickAction()
            scroll("点击放大核对人台图")
            compose.onNodeWithContentDescription("放大正面人台图").assertIsDisplayed().assertHasClickAction()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private data class Presentation(val width: Int = 320, val scale: Float = 2f, val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
