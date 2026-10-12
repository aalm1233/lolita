package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SkinFlingBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everySkinKeepsMovingAfterReleaseInBothDirections() {
        val behaviors = mutableMapOf<SkinType, FlingBehavior>()
        compose.setContent {
            SkinType.entries.forEach { skin ->
                LolitaTheme(skinType = skin) { behaviors[skin] = rememberSkinFlingBehavior() }
            }
        }
        compose.runOnIdle {
            for ((skin, behavior) in behaviors) for (velocity in listOf(3_000f, -3_000f)) {
                var distance = 0f
                val scope = object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        distance += pixels
                        return pixels
                    }
                }
                val remaining = runBlocking(FrameClock()) { with(behavior) { scope.performFling(velocity) } }
                assertTrue("$skin: 惯性滚动方向必须与释放速度相同，实际位移 $distance", distance * velocity > 0f)
                assertTrue("$skin: 释放后必须继续滚动，实际位移 $distance", abs(distance) > 100f)
                assertTrue("$skin: 完整消耗的惯性不应向父容器传递明显余速 $remaining", abs(remaining) < 20f)
            }
        }
    }

    @Test fun hittingAnEdgeReturnsOnlyUnconsumedVelocity() {
        var behavior: FlingBehavior? = null
        compose.setContent { LolitaTheme(skinType = SkinType.DEFAULT) { behavior = rememberSkinFlingBehavior() } }
        compose.runOnIdle {
            var calls = 0
            val edge = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float { calls++; return 0f }
            }
            val remaining = runBlocking(FrameClock()) { with(behavior!!) { edge.performFling(3_000f) } }
            assertTrue("边界必须及时终止惯性，不能空转到衰减结束 ($calls 帧)", calls <= 3)
            assertTrue("余速应保留原始方向且不超过输入速度: $remaining", remaining > 0f && remaining <= 3_000f)
        }
    }

    private class FrameClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_666_667L
            check(nanos < 20_000_000_000L) { "惯性动画未在 20 秒内结束" }
            return onFrame(nanos)
        }
    }
}
