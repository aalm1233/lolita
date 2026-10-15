package com.lolita.app.ui.screen.tryon

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Compose controls at 320dp/2x text and all seven light/dark skins. Never calls the paid service. */
@RunWith(AndroidJUnit4::class)
class TryOnScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var directory: File
    private lateinit var store: ViewModelStore
    private lateinit var vm: TryOnViewModel
    private val presentation = mutableStateOf(Presentation())
    private var key: String? = null
    private lateinit var photos: List<String>

    @Before fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "tryon-ui-${UUID.randomUUID()}").apply { mkdirs() }
        photos = listOf("first.png", "second.png").mapIndexed { index, name ->
            File(directory, name).also { file ->
                Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(if (index == 0) android.graphics.Color.MAGENTA else android.graphics.Color.BLUE)
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }.absolutePath
        }
        store = ViewModelStore()
        instrumentation.runOnMainSync {
            vm = TryOnViewModel(savedStateHandle = SavedStateHandle(),
                items = flowOf(listOf(
                    Item(id = 11, brandId = 1, categoryId = 1, name = "月光裙", description = "", imageUrls = photos, status = ItemStatus.OWNED),
                    Item(id = 12, brandId = 1, categoryId = 2, name = "蝴蝶结", description = "", imageUrls = photos.take(1), status = ItemStatus.OWNED)
                )),
                categories = flowOf(listOf(Category(id = 1, name = "JSK"), Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY))),
                results = flowOf(emptyList()), hasApiKey = { key != null }, saveApiKey = { key = it }, clearApiKey = { key = null },
                globalGenerating = MutableStateFlow(false), generateImage = { _, _ -> error("UI测试不应发出请求") },
                renameResult = { _, _ -> }, deleteResult = {})
            store.put("ui", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
    }

    @After fun teardown() {
        compose.runOnIdle { store.clear() }
        directory.deleteRecursively()
    }

    private fun show() {
        compose.setContent {
            val display = presentation.value
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = display.width
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (display.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, display.scale)) {
                LolitaTheme(skinType = display.skin, darkTheme = display.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(display.width.dp, 640.dp)) { TryOnScreen({}, vm) }
                    }
                }
            }
        }
    }

    private fun workspace(text: String) {
        compose.onNodeWithTag("tryOnWorkspace").performScrollToNode(hasText(text))
    }

    @Test fun pickerFiltersKeepSelectionAndImageAndRoleControlsChangeActualReferences() {
        show()
        workspace("从衣橱选择")
        compose.onNodeWithText("从衣橱选择").performClick()
        compose.onNodeWithContentDescription("选择服饰：月光裙").performClick()
        compose.onNodeWithText("搜索服饰名称").performTextInput("蝴蝶")
        compose.onNodeWithText("头饰", substring = false).performClick()
        compose.onNodeWithContentDescription("选择服饰：蝴蝶结").assertIsDisplayed()
        compose.onNodeWithText("完成选择（1/8）").performClick()
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        workspace("更换参考图片")
        compose.onNodeWithText("更换参考图片").performClick()
        compose.onNodeWithText("参考图片 2").performScrollTo().performClick()
        assertEquals(photos[1], vm.uiState.value.selected.single().imagePath)
        workspace("穿戴位置：主件")
        compose.onNodeWithText("穿戴位置：主件").performClick()
        compose.onNodeWithText("外搭", substring = false).performScrollTo().performClick()
        assertEquals(TryOnRole.OUTER, vm.uiState.value.selected.single().role)
    }

    @Test fun personalKeyIsMaskedAndNeverRefilledAndCanBeDeleted() {
        show()
        workspace("服务配置")
        compose.onNodeWithText("服务配置").performClick()
        compose.onNodeWithText("API Key", substring = false).performScrollTo().performTextInput("sk-test-private-value")
        compose.onNodeWithText("API Key", substring = false).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("保存密钥").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }
        compose.onNodeWithText("服务配置").performClick()
        compose.onNodeWithText("API Key", substring = false).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("删除本机密钥").performScrollTo().performClick()
        compose.waitUntil(10_000) { !vm.uiState.value.hasKey && !vm.uiState.value.isConfiguring }
        assertNull(key)
    }

    @Test fun sevenSkinsLightDarkAndLargeTextHaveReachableControlsAndExportActualScreenshots() {
        compose.runOnIdle { vm.toggleItem(11) }
        show()
        val output = File(instrumentation.targetContext.filesDir, "try-on-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            workspace("从衣橱选择")
            compose.onNodeWithText("从衣橱选择").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("配置 API Key 后生成").assertIsDisplayed().assertIsEnabled()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            workspace("更换参考图片")
            compose.onNodeWithText("更换参考图片").assertIsDisplayed()
            workspace("服务配置")
            compose.onNodeWithText("服务配置").performClick()
            compose.onNodeWithText("API Key", substring = false).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("返回", substring = false).performClick()
        }
    }

    @Test fun fullScreenResultPreviewActuallyZoomsTheImage() {
        compose.setContent { LolitaTheme { FullScreenImageViewer(photos.take(1), onDismiss = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("图片预览 1").fetchSemanticsNodes().isNotEmpty()
        }
        val preview = compose.onNodeWithContentDescription("图片预览 1")
        fun colorPixels(): Int {
            val bitmap = preview.captureToImage().asAndroidBitmap()
            var colored = 0
            for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 200 && android.graphics.Color.blue(pixel) > 200 && android.graphics.Color.green(pixel) < 50) colored++
            }
            return colored
        }
        compose.waitUntil(10_000) { colorPixels() > 100 }
        val before = colorPixels()
        preview.performTouchInput {
            pinch(start0 = center + Offset(-25f, -40f), start1 = center + Offset(25f, 40f),
                end0 = center + Offset(-100f, -160f), end1 = center + Offset(100f, 160f))
        }
        compose.waitForIdle()
        assertTrue("Pinch must enlarge actual photo pixels", colorPixels() > before * 1.15)
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed().assertHasClickAction()
    }

    private data class Presentation(val width: Int = 320, val scale: Float = 2f,
        val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
