package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SkinBackgroundAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun backgroundStopsRequestingFramesDuringScrollAndResumesAfterward() {
        val activity = SkinScrollActivity()
        val scrollState = ScrollState(0)
        val draws = AtomicInteger()
        lateinit var scope: CoroutineScope
        lateinit var scroll: Job
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val currentScope = rememberCoroutineScope()
            SideEffect { scope = currentScope }
            CompositionLocalProvider(
                LocalLolitaSkin provides getSkinConfig(SkinType.DEFAULT),
                LocalSkinScrollActivity provides activity,
                LocalIsListScrolling provides activity.isScrolling
            ) {
                ObserveSkinScroll(scrollState)
                SkinBackgroundAnimation(
                    Modifier.size(240.dp).drawWithContent {
                        draws.incrementAndGet()
                        drawContent()
                    }
                )
            }
        }

        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val initialDraws = compose.runOnIdle { draws.get() }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue("静止页面的真实背景 Canvas 应持续绘制", draws.get() > initialDraws)
            scroll = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                scrollState.scroll { awaitCancellation() }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val pausedDraws = compose.runOnIdle {
            assertTrue(activity.isScrolling.value)
            draws.get()
        }
        repeat(8) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals("滚动时背景不能自行请求每帧重绘", pausedDraws, draws.get())
            scroll.cancel()
        }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue("滚动结束后背景应恢复绘制", draws.get() > pausedDraws)
        }
    }
}
