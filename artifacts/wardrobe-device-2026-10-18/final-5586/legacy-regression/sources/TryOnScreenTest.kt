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
import com.lolita.app.domain.model.*
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
    private val acceptedModels = MutableStateFlow<List<PersonalModel>>(emptyList())
    private val defaultModelId = MutableStateFlow<Long?>(null)
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
                categories = flowOf(listOf(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK), Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC))),
                results = flowOf(emptyList()), readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, key != null) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value -> key = value }, clearApiKey = { _ -> key = null },
                tasks = flowOf(emptyList()), submitTask = { _, _ -> error("UI测试不应发出请求") },
                renameResult = { _, _ -> }, deleteResult = {}, personalModels = acceptedModels, defaultPersonalModelId = defaultModelId)
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
            compose.onNodeWithText("配置 API Key").assertIsDisplayed().assertIsEnabled()
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
        for ((skin, dark) in listOf(SkinType.DEFAULT to false, SkinType.VICTORIAN to true)) {
            compose.runOnIdle { presentation.value = Presentation(width = 360, scale = 1f, skin = skin, dark = dark) }
            workspace("本次试衣人物")
            compose.onNodeWithText("配置 API Key").assertIsDisplayed()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "normal-${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun personalPickerRetainsGarmentsAcrossSevenSkinsAndExportsActualModelScreens() {
        val request = PersonalModelRequest("已确认的小月", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
        acceptedModels.value = listOf(PersonalModel(id = 91, name = request.name, requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = photos.first(), provider = ImageGenerationProvider.OPENAI, model = "test"))
        compose.runOnIdle { vm.toggleItem(11) }
        show()
        val output = File(instrumentation.targetContext.filesDir, "personal-try-on-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            workspace("更换试衣人物")
            compose.onNodeWithText("更换试衣人物").performClick()
            compose.onNodeWithText("已确认的小月").performClick()
            compose.onNodeWithTag("tryOnSelectedModel").assertTextEquals("已确认的小月")
            assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
            workspace("本次试衣人物")
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}-model.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            workspace("更换试衣人物")
            compose.onNodeWithText("更换试衣人物").performClick()
            compose.onNodeWithText("搜索个人模特").performTextInput("小月")
            compose.onNodeWithText("已确认的小月 · 已选择").assertExists()
            val picker = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "人物选择弹窗截图失败" }
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}-picker.png").outputStream().use { picker.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.onNodeWithText("内置虚拟模特").performClick()
            assertNull(vm.uiState.value.selectedPersonalModelId)
            assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
        }
    }

    @Test fun fullScreenResultPreviewActuallyZoomsTheImage() {
        compose.setContent { LolitaTheme { FullScreenImageViewer(photos.take(1), onDismiss = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("图片预览 1").fetchSemanticsNodes().isNotEmpty()
        }
        val preview = compose.onAllNodesWithContentDescription("图片预览 1").onFirst()
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

    @Test fun configuredEightPieceSelectionShowsUploadAndCostBesideTheActionAtLargeText() {
        compose.runOnIdle {
            val semantics=listOf(CategorySemantic.JSK,CategorySemantic.KC,CategorySemantic.WRIST_CUFFS,CategorySemantic.MARY_JANE,
                CategorySemantic.FULL_WIG,CategorySemantic.TIGHTS,CategorySemantic.HANDBAG,CategorySemantic.PARASOL)
            vm = TryOnViewModel(items = flowOf((1L..8L).map { id ->
                Item(id = id, name = "第${id}件物品", brandId = 1, categoryId = id, description = "",
                    status = ItemStatus.OWNED, imageUrls = photos.take(1))
            }), categories = flowOf(semantics.mapIndexed { index,semantic -> Category(id=index+1L,name=semantic.label,categorySemantic=semantic) }), results = flowOf(emptyList()),
                readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value ->  }, clearApiKey = { _ ->  }, tasks = flowOf(emptyList()),
                submitTask = { _, _ -> error("不能付费调用") }, renameResult = { _, _ -> }, deleteResult = {})
            store.put("eight-pieces", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
        compose.runOnIdle { (1L..8L).forEach(vm::toggleItem) }
        show()
        compose.onNodeWithTag("tryOnUploadSummary").assertIsDisplayed().assertTextContains("9 张", substring = true)
        compose.onNodeWithTag("tryOnUploadSummary").assertTextContains("计费", substring = true)
        compose.onNodeWithText("生成一张试衣图").assertIsDisplayed().assertIsEnabled()
    }

    private data class Presentation(val width: Int = 320, val scale: Float = 2f,
        val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
