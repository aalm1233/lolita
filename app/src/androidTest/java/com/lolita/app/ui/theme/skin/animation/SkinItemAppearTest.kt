package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SkinItemAppearTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appearanceAnimationUpdatesLayerWithoutRecomposingCard() {
        val compositions = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLolitaSkin provides getSkinConfig(SkinType.DEFAULT)) {
                AppearanceCardProbe { compositions.incrementAndGet() }
            }
        }

        // Start the real entrance animation, then advance through changing progress values.
        compose.mainClock.advanceTimeByFrame()
        val initialCompositions = compose.runOnIdle { compositions.get() }
        repeat(8) { compose.mainClock.advanceTimeByFrame() }
        val animatedCompositions = compose.runOnIdle { compositions.get() }

        assertEquals(
            "入场动画只应更新图层，不能为每一帧重新组合卡片",
            initialCompositions,
            animatedCompositions
        )
    }

    @Composable
    private fun AppearanceCardProbe(onComposed: () -> Unit) {
        val modifier = Modifier.size(80.dp).skinItemAppear(0).background(Color.Red)
        SideEffect(onComposed)
        Box(modifier)
    }
}
