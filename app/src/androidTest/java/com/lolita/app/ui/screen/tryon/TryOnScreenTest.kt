package com.lolita.app.ui.screen.tryon

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
    private val history = MutableStateFlow<List<TryOnResult>>(emptyList())
    private var configured = 0
    private var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
    private var key: String? = null
    private lateinit var photos: List<String>
    private lateinit var itemRows: MutableStateFlow<List<Item>>
    private lateinit var categoryRows: MutableStateFlow<List<Category>>
    private var originalAutomationFlags: Int? = null

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
        itemRows = MutableStateFlow(listOf(
            Item(id = 11, brandId = 1, categoryId = 1, name = "月光裙", description = "", imageUrls = photos, status = ItemStatus.OWNED),
            Item(id = 12, brandId = 1, categoryId = 2, name = "蝴蝶结", description = "", imageUrls = photos.take(1), status = ItemStatus.OWNED)))
        categoryRows = MutableStateFlow(listOf(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK),
            Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC)))
        instrumentation.runOnMainSync {
            vm = TryOnViewModel(savedStateHandle = SavedStateHandle(),
                items = itemRows, categories = categoryRows,
                results = history, readService = { com.lolita.app.data.tryon.ImageServiceStatus(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, key != null) }, selectService = {}, serviceChanges = kotlinx.coroutines.flow.flowOf(0), saveApiKey = { _, value -> key = value }, clearApiKey = { _ -> key = null },
                tasks = flowOf(emptyList()), submitTask = { _, _, _ -> error("UI测试不应发出请求") },
                renameResult = { _, _ -> }, deleteResult = {}, personalModels = acceptedModels, defaultPersonalModelId = defaultModelId)
            store.put("ui", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
    }

    @After fun teardown() {
        compose.runOnIdle { store.clear() }
        originalAutomationFlags?.let { flags ->
            val info = instrumentation.uiAutomation.serviceInfo
            info.flags = flags
            instrumentation.uiAutomation.serviceInfo = info
        }
        directory.deleteRecursively()
    }

    private fun show(fullHeight: Boolean = false) {
        compose.setContent {
            keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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
                        Box(if (fullHeight) Modifier.width(display.width.dp).fillMaxHeight() else Modifier.size(display.width.dp, 640.dp)) { TryOnScreen({}, vm, onConfigureApi = { configured++ }) }
                    }
                }
            }
        }
    }

    private fun openService() {
        compose.onNodeWithTag("tryOnWorkspace").performScrollToNode(hasText("更换服务") or hasText("收起"))
        if (compose.onAllNodesWithText("更换服务").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("更换服务").performClick()
        }
    }

    private fun workspace(text: String) {
        compose.onNodeWithTag("tryOnWorkspace").performScrollToNode(hasText(text))
    }

    private fun illustratedProduct(name: String, socks: Boolean): String {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9C4266.toInt() }
        val path = android.graphics.Path()
        if (socks) {
            for (shift in listOf(0f, 90f)) {
                path.reset(); path.moveTo(30+shift, 30f); path.lineTo(75+shift, 30f); path.lineTo(75+shift, 215f)
                path.lineTo(118+shift, 260f); path.lineTo(108+shift, 280f); path.lineTo(50+shift, 280f)
                path.lineTo(30+shift, 230f); path.close(); canvas.drawPath(path, paint)
            }
            paint.color = android.graphics.Color.WHITE
            for (shift in listOf(0f, 90f)) for (y in listOf(75f, 115f, 155f)) canvas.drawCircle(52+shift,y,7f,paint)
        } else {
            path.moveTo(20f,215f); path.lineTo(55f,135f); path.lineTo(93f,155f); path.lineTo(135f,210f)
            path.lineTo(205f,205f); path.quadTo(230f,220f,218f,250f); path.lineTo(25f,250f); path.close();canvas.drawPath(path,paint)
            paint.style=android.graphics.Paint.Style.STROKE;paint.strokeWidth=12f
            canvas.drawArc(55f,120f,135f,230f,0f,180f,false,paint)
        }
        return File(directory,name).also { file ->
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
        }.absolutePath
    }

    @Test fun wishedShoesAndHosieryAcrossSevenSkinsKeepTheirSelectionWhenStatusIsHidden() {
        val info = instrumentation.uiAutomation.serviceInfo
        originalAutomationFlags = info.flags
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
        val shoes = illustratedProduct("controlled-shoes.png", false)
        val socks = illustratedProduct("controlled-hosiery.png", true)
        compose.runOnIdle {
            categoryRows.value += listOf(Category(id=3,name="鞋履",categorySemantic=CategorySemantic.MARY_JANE),
                Category(id=4,name="袜装",categorySemantic=CategorySemantic.KNEE_SOCKS))
            itemRows.value += listOf(
                Item(id=13,name="心愿鞋",brandId=1,categoryId=3,description="",imageUrls=listOf(shoes),status=ItemStatus.WISHED),
                Item(id=14,name="心愿袜",brandId=1,categoryId=4,description="",imageUrls=listOf(socks),status=ItemStatus.WISHED))
        }
        compose.waitUntil(5_000) { vm.uiState.value.items.size==4 }
        show(); workspace("从衣橱选择"); compose.onNodeWithText("从衣橱选择").performClick()
        compose.onNodeWithText("心愿",substring=false).performClick()
        fun choose(description:String) {
            compose.onNodeWithTag("tryOnPicker").performScrollToNode(hasContentDescription(description))
            compose.onNodeWithContentDescription(description).performClick()
        }
        choose("选择物品：心愿鞋");choose("选择物品：心愿袜")
        compose.onNodeWithTag("tryOnPicker").performScrollToNode(hasText("已拥有",substring=false))
        compose.onNodeWithText("已拥有",substring=false).performClick()
        assertEquals(listOf(13L,14L),vm.uiState.value.selected.map {it.itemId})
        compose.onNodeWithText("全部状态").performClick();choose("选择物品：月光裙")
        val output=File(instrumentation.targetContext.filesDir,"tryon-wishlist-shoes-screens").apply {mkdirs()}
        // Dialog owns an Android window: pair native night mode with the requested
        // visual pass instead of mixing a light parent theme and a dark window.
        val darkModes = when (InstrumentationRegistry.getArguments().getString("visualNight")) {
            "light" -> listOf(false)
            "dark" -> listOf(true)
            else -> listOf(false, true)
        }
        for(skin in SkinType.entries)for(dark in darkModes) {
            compose.runOnIdle {presentation.value=Presentation(width=360,scale=1f,skin=skin,dark=dark)}
            compose.onNodeWithText("完成（3 件）").assertIsDisplayed()
            compose.onNodeWithTag("tryOnPicker").performScrollToNode(hasText("全部状态"))
            instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
                File(output,"${skin.name.lowercase()}-${if(dark)"dark" else "light"}-options.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            compose.onNodeWithTag("tryOnPicker").performScrollToNode(hasContentDescription("已选物品：心愿鞋"))
            instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
                File(output,"${skin.name.lowercase()}-${if(dark)"dark" else "light"}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
        compose.runOnIdle {presentation.value=Presentation(width=320,scale=2f,dark=darkModes.last())}
        compose.onNodeWithText("完成（3 件）").assertIsDisplayed().assertIsEnabled()
        instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
            File(output,"small-320dp-2x.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        compose.onNodeWithTag("tryOnPicker").performScrollToNode(hasText("搜索物品名称"))
        compose.onNodeWithText("搜索物品名称").performClick().performTextInput("心愿袜")
        compose.waitUntil(5_000) {
            instrumentation.uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        compose.onNodeWithText("完成（3 件）").assertIsDisplayed()
        instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
            File(output,"small-320dp-2x-keyboard.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("完成（3 件）").performClick()
        assertEquals(listOf(TryOnRole.SHOES,TryOnRole.HOSIERY,TryOnRole.MAIN),vm.uiState.value.selected.map {it.role})
        assertNull(vm.uiState.value.selectionIssue)
    }

    @Test fun wearingDescriptionAcrossSevenSkinsAndKeyboardAndPaidConfirmation() {
        compose.runOnIdle { vm.toggleItem(11); vm.saveKey("controlled-key") }
        compose.waitUntil(10_000) { vm.uiState.value.hasKey && !vm.uiState.value.busy }
        show(fullHeight = true)
        val output = File(instrumentation.targetContext.filesDir, "try-on-physics-screens").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(width = 360, scale = 1f, skin = skin, dark = dark) }
            workspace("穿戴要求")
            compose.onNodeWithTag("tryOnInstructions").performTextClearance()
            compose.onNodeWithTag("tryOnInstructions").performTextInput("BNT 戴靠后，露出刘海；衬衫掖进裙腰。")
            compose.onNodeWithText("核对输入与费用").assertIsDisplayed().assertIsEnabled()
            compose.runOnIdle { keyboard?.hide() }
            compose.waitForIdle()
            workspace("穿戴要求")
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.runOnIdle { presentation.value = Presentation(width = 320, scale = 2f, skin = SkinType.VICTORIAN, dark = true) }
        workspace("补充穿戴要求")
        compose.onNodeWithTag("tryOnInstructions").performClick()
        compose.onNodeWithTag("tryOnInstructions").performTextInput(" ")
        compose.waitForIdle()
        compose.onNodeWithText("核对输入与费用").assertIsDisplayed().assertIsEnabled()
        val ime = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "small-large-font-keyboard.png").outputStream().use { ime.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("核对输入与费用").performClick()
        compose.onNodeWithTag("tryOnConfirmedInstructions").performScrollTo().assertTextContains("BNT 戴靠后，露出刘海；衬衫掖进裙腰。", substring = true)
        compose.onNodeWithText("确认并加入任务").assertIsDisplayed().assertHasClickAction()
        val dialog = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "small-large-font-confirmation.png").outputStream().use { dialog.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("再核对一下").performClick()
        assertEquals("BNT 戴靠后，露出刘海；衬衫掖进裙腰。 ", vm.uiState.value.instructions)
        history.value = listOf(TryOnResult(id = 1, name = "有穿戴要求的记录", imageUrl = photos.first(), itemIds = listOf("11"), itemNames = listOf("月光裙"), model = "controlled",
            requestSnapshotJson = ImageTaskRequest(listOf(ImageTaskGarment(11, "月光裙", TryOnRole.MAIN)), instructions = "BNT 戴靠后，露出刘海").encode()))
        compose.waitUntil { vm.uiState.value.results.isNotEmpty() }
        workspace("穿戴要求：BNT 戴靠后，露出刘海")
        compose.onNodeWithText("穿戴要求：BNT 戴靠后，露出刘海").assertIsDisplayed()
    }

    @Test fun pickerFiltersKeepSelectionAndImageAndRoleControlsChangeActualReferences() {
        show()
        workspace("从衣橱选择")
        compose.onNodeWithText("从衣橱选择").performClick()
        compose.onNodeWithContentDescription("选择物品：月光裙").performClick()
        compose.onNodeWithText("搜索物品名称").performTextInput("蝴蝶")
        compose.onNodeWithText("头饰", substring = false).performClick()
        compose.onNodeWithContentDescription("选择物品：蝴蝶结").assertIsDisplayed()
        compose.onNodeWithText("完成（1 件）").performClick()
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

    @Test fun missingKeyAndServiceActionOpenUnifiedConfigurationWithoutChangingSelection() {
        compose.runOnIdle { vm.toggleItem(11) }
        show()
        compose.onNodeWithText("配置服务密钥").performClick()
        openService()
        workspace("API 配置")
        compose.onNodeWithText("API 配置").performClick()
        assertEquals(2, configured)
        assertNull(key)
        assertEquals(listOf(11L), vm.uiState.value.selected.map { it.itemId })
    }

    @Test fun sevenSkinsLightDarkAndLargeTextHaveReachableControlsAndExportActualScreenshots() {
        compose.runOnIdle { vm.toggleItem(11) }
        show()
        val output = File(instrumentation.targetContext.filesDir, "try-on-ui-contact-sheets").apply { mkdirs() }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(skin = skin, dark = dark) }
            workspace("从衣橱选择")
            compose.onNodeWithText("从衣橱选择").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("配置服务密钥").assertIsDisplayed().assertIsEnabled()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(output, "${skin.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            workspace("更换参考图片")
            compose.onNodeWithText("更换参考图片").assertIsDisplayed()
            openService()
            workspace("API 配置")
            compose.onNodeWithText("API 配置").performClick()
            assertTrue(configured > 0)
        }
        for ((skin, dark) in listOf(SkinType.DEFAULT to false, SkinType.VICTORIAN to true)) {
            compose.runOnIdle { presentation.value = Presentation(width = 360, scale = 1f, skin = skin, dark = dark) }
            workspace("本次试衣人物")
            compose.onNodeWithText("配置服务密钥").assertIsDisplayed()
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
                submitTask = { _, _, _ -> error("不能付费调用") }, renameResult = { _, _ -> }, deleteResult = {})
            store.put("eight-pieces", vm)
        }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.keyLoaded }
        compose.runOnIdle { (1L..8L).forEach(vm::toggleItem) }
        show()
        compose.onNodeWithTag("tryOnUploadSummary").assertIsDisplayed().assertTextContains("9 张", substring = true)
        compose.onNodeWithTag("tryOnUploadSummary").assertTextContains("计费", substring = true)
        compose.onNodeWithText("核对输入与费用").assertIsDisplayed().assertIsEnabled()
    }

    private data class Presentation(val width: Int = 320, val scale: Float = 2f,
        val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
}
