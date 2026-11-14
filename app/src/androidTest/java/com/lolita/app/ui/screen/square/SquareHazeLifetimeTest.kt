package com.lolita.app.ui.screen.square

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real NavHost and Haze navigation; the Square feed intentionally has no title bar. */
class SquareHazeLifetimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactFeedKeepsCompleteCategoryLabelsAcrossSevenSkinsAndModes() {
        val selectedSkin = mutableStateOf(SkinType.DEFAULT)
        val dark = mutableStateOf(false)
        lateinit var host: android.view.View
        compose.setContent {
            host = LocalView.current
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                LolitaTheme(selectedSkin.value, dark.value) {
                    val active = LolitaSkin.current
                    // Stop decorative ambient motion for Compose idling, keeping real blur enabled.
                    val still = remember(active) { active.copy(animations = object : SkinAnimationProvider by active.animations {
                        override val ambientAnimation = active.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                    assertTrue(still.topBarBlurEnabled)
                    assertTrue(still.navBarBlurEnabled)
                    CompositionLocalProvider(LocalLolitaSkin provides still) {
                        LolitaNavHost(navController = rememberNavController())
                    }
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "square-compact-ui").apply { mkdirs() }
        for (skin in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { selectedSkin.value = skin; dark.value = night }
            compose.onNode(isSelectable() and hasText("首页")).performClick()
            val homeTabBounds = compose.onNode(isSelectable() and hasText("位置")).fetchSemanticsNode().boundsInRoot
            compose.onNode(isSelectable() and hasText("广场")).performClick()
            compose.onNodeWithTag("square-refresh").assertExists()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("square-feed").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("square-import").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("square-service").assertDoesNotExist()
            compose.onNodeWithContentDescription("查看心愿").assertDoesNotExist()
            compose.onNodeWithContentDescription("导入资料包").assertDoesNotExist()
            compose.onAllNodesWithText("广场").assertCountEquals(1) // Only bottom navigation.
            compose.onNodeWithTag("square-detail-topbar").assertDoesNotExist()
            val location = IntArray(2).also { host.getLocationOnScreen(it) }
            val statusTop = ViewCompat.getRootWindowInsets(host)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
            assertTrue(compose.onNodeWithTag("square-refresh").fetchSemanticsNode().boundsInRoot.top + location[1] >= statusTop)
            if (compose.onAllNodesWithTag("square-feed").fetchSemanticsNodes().isNotEmpty()) {
                for (label in listOf("上新", "再贩", "现货", "尾款")) {
                    val tab = compose.onNodeWithTag("square-kind-$label")
                    tab.performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
                    val results = mutableListOf<TextLayoutResult>()
                    compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("square-kind-$label")), useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    val text = results.single()
                    assertEquals(12f, text.layoutInput.style.fontSize.value)
                    assertEquals(1, text.lineCount)
                    assertFalse("Complete label required: $label; width=${text.didOverflowWidth}, height=${text.didOverflowHeight}, size=${text.size}, constraints=${text.layoutInput.constraints}, fontScale=${text.layoutInput.density.fontScale}, lines=${text.lineCount}", text.hasVisualOverflow)
                    val bounds = tab.fetchSemanticsNode().boundsInRoot
                    assertEquals("Square and Home tab heights", homeTabBounds.height, bounds.height, 1f)
                    assertEquals("Square and Home tab top", homeTabBounds.top, bounds.top, 1f)
                    val filterBounds = compose.onNodeWithTag("square-filter-button").fetchSemanticsNode().boundsInRoot
                    assertEquals("Filter aligned with tabs", bounds.center.y, filterBounds.center.y, 1f)
                }
                compose.onNodeWithTag("square-kind-全部").assertDoesNotExist()
                compose.onNodeWithTag("square-kind-上新").performScrollTo().performClick()
            }
            compose.waitForIdle()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(directory, "${skin.name.lowercase()}-${if (night) "dark" else "light"}.png")
                    .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            compose.onNode(isSelectable() and hasText("个人")).performClick().assertIsSelected()
            compose.onNode(isSelectable() and hasText("广场")).performClick().assertIsSelected()
            compose.onNodeWithTag("square-refresh").assertExists()
        }
    }
}
