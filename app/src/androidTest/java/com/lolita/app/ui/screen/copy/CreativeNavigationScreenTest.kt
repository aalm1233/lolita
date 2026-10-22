package com.lolita.app.ui.screen.copy

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.navigation.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Dedicated empty AVD only. Exercises the actual app shell and system gesture inset. */
class CreativeNavigationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private lateinit var nav: NavHostController
    private lateinit var host: android.view.View
    private fun show() {
        compose.setContent {
            val view = LocalView.current
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) { LolitaTheme(skin.value, dark.value) {
                val controller = rememberNavController(); SideEffect { nav = controller; host = view }
                val active = LolitaSkin.current
                val still = remember(active) { active.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                    animations = object : SkinAnimationProvider by active.animations {
                        override val ambientAnimation = active.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                CompositionLocalProvider(LocalLolitaSkin provides still) { LolitaNavHost(navController = controller) }
            } }
        }
    }
    @Test fun actualEmptyWardrobeImportAndRestoreActionsUseExistingRoutes() {
        show()
        compose.onNodeWithText("服饰").performClick()
        compose.onNodeWithText("添加第一件物品").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("从淘宝导入").performScrollTo().performClick()
        compose.onNodeWithText("商品链接导入（试用）").assertIsDisplayed()
        compose.onNodeWithText("订单文件导入").performClick()
        compose.waitUntil(10_000) { nav.currentDestination?.route == Screen.TaobaoImport.route }
        compose.onNodeWithText("如何获取订单文件").performClick()
        compose.waitUntil(10_000) { nav.currentDestination?.route == Screen.TaobaoImportGuide.route }
        compose.runOnIdle { nav.popBackStack(); nav.popBackStack() }
        compose.onNodeWithText("已有资料？恢复备份").performScrollTo().performClick()
        compose.waitUntil(10_000) { nav.currentDestination?.route == Screen.BackupRestore.route }
        compose.onNodeWithText("选择备份文件").performScrollTo().assertIsDisplayed()
    }
    @Test fun real320dpDoubleFontNavigationStaysAboveGestureAreaAcrossSkins() {
        Assume.assumeTrue(context.resources.configuration.screenWidthDp <= 320 && context.resources.configuration.fontScale >= 1.99f)
        show()
        val directory = File(context.filesDir, "creative-navigation-native").apply { mkdirs() }
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            for (label in listOf("首页", "穿搭", "统计", "个人")) {
                compose.onNode(isSelectable() and hasText(label)).assertIsDisplayed().performClick().assertIsSelected()
                val text = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().maxBy { it.boundsInWindow.bottom }
                val navBottom = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                assertTrue("$type/$night/$label must clear the system gesture region", text.boundsInWindow.bottom <= host.rootView.height - navBottom + 1f)
            }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(directory, "${type.name.lowercase()}-${if (night) "dark" else "light"}-small.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
