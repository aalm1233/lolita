package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.gson.Gson
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductVariant
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real selector and final price fields. No network, saved user settings, or paid boundary. */
class TaobaoProductSkuCombinationsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val store = ViewModelStore()
    private lateinit var vm: TaobaoProductViewModel
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val compact = mutableStateOf(false)
    private var keyboard: SoftwareKeyboardController? = null
    private var originalAutomationFlags: Int? = null

    @Before fun setup() {
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>().single()
            // Match the production MainActivity instead of letting the test window pan
            // its entire view above the status bar when the keyboard opens.
            activity.enableEdgeToEdge()
            activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val choices = listOf("黑色", "白色").flatMapIndexed { color, group ->
            listOf("S", "M", "L（身高165-175）").mapIndexed { size, name ->
                TaobaoProductVariant("sku-$color-$size", "$group · $name", "连裤袜", size = name, specificationGroup = group)
            }
        }
        val draft = TaobaoProductDraft("印花连裤袜（测试示意）", "", "", "原始规格完整保留", "原商品来源",
            "https://item.taobao.com/item.htm?id=123456", emptyList(), category = "连裤袜", skuVariants = choices)
        compose.runOnIdle {
            vm = TaobaoProductViewModel(app, SavedStateHandle(mapOf(TaobaoProductViewModel.SESSION_KEY to Gson().toJson(
                TaobaoProductImportSession(state = TaobaoProductState(url = draft.productUrl, draft = draft))))))
            store.put("sku-screen", vm)
        }
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val density = LocalDensity.current
            val controller = LocalSoftwareKeyboardController.current
            SideEffect { keyboard = controller }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, if (compact.value) 2f else 1f)) {
                Box(if (compact.value) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                    LolitaTheme(skin.value, dark.value) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SkinArtBackground(Modifier.matchParentSize())
                            TaobaoProductScreen({}, { error("UI must not start downloads") }, vm = vm)
                        }
                    }
                }
            }
        }
    }
    @After fun close() {
        compose.runOnIdle { store.clear() }
        originalAutomationFlags?.let { flags ->
            val info = instrumentation.uiAutomation.serviceInfo; info.flags = flags; instrumentation.uiAutomation.serviceInfo = info
        }
    }
    private fun choose(name: String) = compose.onNodeWithContentDescription("选择规格：$name").performScrollTo().performClick()
    private fun startTwo() {
        compose.onNodeWithText("按颜色与尺码").performScrollTo().performClick()
        compose.onNodeWithText("请至少选择一个组合").assertIsNotEnabled()
        choose("黑色 · S"); choose("白色 · M")
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsEnabled()
        assertEquals(setOf("sku-0-0", "sku-1-1"), vm.state.value.selectedVariantIds)
    }
    private fun hideKeyboard() { compose.runOnIdle { keyboard?.hide() }; compose.waitForIdle() }
    private fun screenshot(name: String, system: Boolean = false) {
        val folder = File(app.filesDir, "taobao-sku-combination-screenshots").apply { mkdirs() }
        val bitmap = if (system) requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        if (system) bitmap.recycle()
    }
    @Test fun searchSameColorMultipleSizesAndModeToggleKeepExactChoicesAndQuotes() {
        compose.onNodeWithText("搜索颜色、款式或尺码").assertDoesNotExist()
        startTwo()
        compose.onAllNodesWithText("参考金额（元）")[0].performScrollTo().performTextInput("36.50"); hideKeyboard()
        compose.onNodeWithText("搜索颜色、款式或尺码").performScrollTo().performTextInput("黑色"); hideKeyboard()
        choose("黑色 · M")
        compose.onNodeWithContentDescription("选择规格：白色 · M").assertDoesNotExist()
        compose.onNodeWithText("核对并逐件编辑（3件）").assertIsEnabled()
        compose.onNodeWithText("搜索颜色、款式或尺码").performScrollTo().performTextClearance()
        compose.onNodeWithText("搜索颜色、款式或尺码").performTextInput("不存在"); hideKeyboard()
        compose.onNodeWithText("没有匹配的规格，请调整搜索；已选组合仍然保留。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("核对并逐件编辑（3件）").assertIsEnabled()
        compose.onNodeWithText("清空搜索").performScrollTo().performClick()
        choose("黑色 · S")
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsEnabled()
        compose.onNodeWithText("按颜色与尺码").performScrollTo().performClick()
        compose.onNodeWithText("核对并进入服饰编辑").assertIsEnabled()
        compose.onNodeWithText("按颜色与尺码").performScrollTo().performClick()
        choose("黑色 · S")
        assertEquals(setOf("sku-0-0", "sku-0-1", "sku-1-1"), vm.state.value.selectedVariantIds)
        assertEquals("36.50", vm.state.value.draft!!.skuVariants.orEmpty()[0].referenceQuote?.amount)
        assertNull(vm.state.value.draft!!.skuVariants.orEmpty()[1].referenceQuote)
    }
    @Test fun sevenSkinsLightDarkKeepSearchAndSizeChoicesReachable() {
        startTwo()
        for (night in listOf(false, true)) for (type in SkinType.entries) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            compose.onNodeWithText("选择颜色和尺码组合").performScrollTo().assertIsDisplayed()
            screenshot("${type.name.lowercase()}-${if (night) "dark" else "light"}-options")
            compose.onNodeWithContentDescription("选择规格：白色 · M").performScrollTo().assertIsSelected()
            compose.onNodeWithText("核对并逐件编辑（2件）").assertIsDisplayed().assertIsEnabled()
            screenshot("${type.name.lowercase()}-${if (night) "dark" else "light"}-selected")
        }
    }
    @Test fun smallWindowLargeFontLongSizeAndRealPriceKeyboardKeepSubmitReachable() {
        compose.runOnIdle { compact.value = true }
        startTwo()
        choose("黑色 · L（身高165-175）")
        compose.onNodeWithContentDescription("选择规格：黑色 · L（身高165-175）").assertIsSelected()
        screenshot("small-320dp-2x")
        val info = instrumentation.uiAutomation.serviceInfo
        originalAutomationFlags = info.flags
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
        compose.onAllNodesWithText("参考金额（元）")[2].performScrollTo().performClick().performTextInput("39.50")
        compose.runOnIdle { keyboard?.show() }
        compose.waitUntil(5_000) { instrumentation.uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        compose.waitForIdle()
        compose.onNodeWithText("39.50").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("核对并逐件编辑（3件）").assertIsDisplayed().assertIsEnabled()
        screenshot("small-320dp-2x-keyboard", system = true)
        hideKeyboard()
        assertEquals("39.50", vm.state.value.draft!!.skuVariants.orEmpty().single { it.id == "sku-1-1" }.referenceQuote?.amount)
    }
}
