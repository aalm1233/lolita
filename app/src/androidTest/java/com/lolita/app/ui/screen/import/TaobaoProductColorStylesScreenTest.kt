package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
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
import com.google.gson.Gson
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductVariant
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real import screen; only controlled product facts, no network or user settings. */
@OptIn(ExperimentalLayoutApi::class)
class TaobaoProductColorStylesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val store = ViewModelStore()
    private lateinit var vm: TaobaoProductViewModel
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val compact = mutableStateOf(false)
    private var keyboard: SoftwareKeyboardController? = null
    @Volatile private var imeOpen = false

    @Before fun setup() {
        val colors = (1..4).map { TaobaoProductVariant("color-$it", "款式$it（测试示意）", "连裤袜") }
        val draft = TaobaoProductDraft("摇篮时光 · 印花连裤袜", "", "", "原始规格完整保留", "淘宝商品来源",
            "https://item.taobao.com/item.htm?id=844758918476", emptyList(), category = "连裤袜", colorVariants = colors)
        compose.runOnIdle {
            vm = TaobaoProductViewModel(application, SavedStateHandle(mapOf(TaobaoProductViewModel.SESSION_KEY to
                Gson().toJson(TaobaoProductImportSession(state = TaobaoProductState(url = draft.productUrl, draft = draft))))))
            store.put("color-styles-screen", vm)
        }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val density = LocalDensity.current
            val controller = LocalSoftwareKeyboardController.current
            val open = WindowInsets.isImeVisible
            SideEffect { keyboard = controller; imeOpen = open }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, if (compact.value) 2f else density.fontScale)) {
                Box(if (compact.value) Modifier.width(320.dp) else Modifier.fillMaxSize()) {
                    LolitaTheme(skin.value, dark.value) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SkinArtBackground(Modifier.matchParentSize())
                            TaobaoProductScreen({}, { error("UI test must not start image transfer") }, vm = vm)
                        }
                    }
                }
            }
        }
    }

    @After fun close() { compose.runOnIdle { store.clear() } }

    private fun chooseTwo() {
        compose.onNodeWithText("按颜色分类分别导入").performScrollTo().performClick()
        compose.onNodeWithText("请至少选择一款").assertIsNotEnabled()
        compose.onNodeWithText("款式1（测试示意）").performScrollTo().performClick()
        compose.onNodeWithText("款式3（测试示意）").performScrollTo().performClick()
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsEnabled()
        compose.runOnIdle { assertEquals(setOf("color-1", "color-3"), vm.state.value.selectedVariantIds) }
    }

    private fun screenshot(name: String) {
        val directory = File(application.filesDir, "taobao-color-styles-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        if (name.endsWith("keyboard")) File(directory, "$name-system.png").outputStream().use {
            val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) } finally { screenshot.recycle() }
        }
    }

    @Test fun fourOptionsCanSelectOnlyTwoAndToggleKeepsChoicesAndQuote() {
        compose.onNodeWithText("款式1（测试示意）").assertDoesNotExist()
        compose.onNodeWithText("核对并进入服饰编辑").assertIsEnabled()
        chooseTwo()
        compose.onAllNodesWithText("参考金额（元）")[1].performScrollTo().performTextInput("36.50")
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("按颜色分类分别导入").performScrollTo().performClick()
        compose.onNodeWithText("款式1（测试示意）").assertDoesNotExist()
        compose.onNodeWithText("核对并进入服饰编辑").assertIsEnabled()
        compose.onNodeWithText("按颜色分类分别导入").performScrollTo().performClick()
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(setOf("color-1", "color-3"), vm.state.value.selectedVariantIds)
            assertEquals("36.50", vm.state.value.draft!!.colorVariants[2].referenceQuote?.amount)
            assertNull(vm.state.value.draft!!.colorVariants[0].referenceQuote)
        }
    }

    @Test fun sevenSkinsLightDarkAndSmallLargeFontKeepChoiceAndActionReachable() {
        chooseTwo()
        for (mode in listOf(false, true)) for (type in SkinType.entries) {
            compose.runOnIdle { dark.value = mode; skin.value = type }
            compose.onNodeWithText("按颜色分类分别导入").performScrollTo().assertIsDisplayed()
            screenshot("${type.name.lowercase()}-${if (mode) "dark" else "light"}-options")
            compose.onNodeWithText("选择要拆分的物品").performScrollTo()
            compose.onNodeWithText("核对并逐件编辑（2件）").assertIsDisplayed().assertIsEnabled()
            screenshot("${type.name.lowercase()}-${if (mode) "dark" else "light"}")
        }
        compose.runOnIdle { compact.value = true; dark.value = false; skin.value = SkinType.DEFAULT }
        compose.onNodeWithText("款式4（测试示意）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsDisplayed().assertIsEnabled()
        screenshot("small-320dp-2x")
        compose.onAllNodesWithText("参考金额（元）")[1].performScrollTo().performClick().performTextInput("36.50")
        compose.runOnIdle { keyboard?.show() }
        compose.waitUntil(5_000) { imeOpen }
        compose.onNodeWithText("核对并逐件编辑（2件）").assertIsDisplayed().assertIsEnabled()
        screenshot("small-320dp-2x-keyboard")
        compose.runOnIdle { keyboard?.hide() }
    }
}
