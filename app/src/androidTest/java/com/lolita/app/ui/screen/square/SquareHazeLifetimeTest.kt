package com.lolita.app.ui.screen.square

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

/** Actual shared Haze source, top bar and navigation; no blur-disabling skin override. */
class SquareHazeLifetimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun serviceDialogAndNavigationKeepBlurAcrossSevenSkinsAndModes() {
        val selectedSkin = mutableStateOf(SkinType.DEFAULT)
        val dark = mutableStateOf(false)
        compose.setContent {
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
        val directory = File(instrumentation.targetContext.cacheDir, "native-race-ui").apply { mkdirs() }
        for (skin in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { selectedSkin.value = skin; dark.value = night }
            compose.onNode(isSelectable() and hasText("广场")).performClick()
            compose.onNodeWithTag("square-service").performClick()
            compose.onNodeWithTag("square-service-address").assertIsDisplayed()
            compose.onNodeWithTag("square-service-save").assertIsDisplayed()
            compose.waitForIdle()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(directory, "${skin.name.lowercase()}-${if (night) "dark" else "light"}.png")
                    .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            compose.onNodeWithText("取消").performClick()
            compose.onNode(isSelectable() and hasText("个人")).performClick().assertIsSelected()
            compose.onNode(isSelectable() and hasText("广场")).performClick().assertIsSelected()
            compose.onNodeWithTag("square-service").assertIsDisplayed()
        }
    }
}
