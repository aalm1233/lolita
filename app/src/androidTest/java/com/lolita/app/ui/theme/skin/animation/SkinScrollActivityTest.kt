package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkinScrollActivityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stoppingOrRemovingOneSourceKeepsOtherScrollingSourcesActive() {
        val activity = SkinScrollActivity()
        val first = Any()
        val second = Any()
        activity.setScrolling(first, true)
        activity.setScrolling(second, true)
        activity.setScrolling(first, false)
        assertTrue("一个列表停止不能恢复另一个仍在滚动的列表背景", activity.isScrolling.value)
        activity.setScrolling(first, true)
        activity.removeSource(first)
        assertTrue("一个列表销毁不能清除另一个列表的滚动状态", activity.isScrolling.value)
        activity.removeSource(second)
        assertFalse("最后一个滚动来源离开后必须恢复背景", activity.isScrolling.value)
    }

    @Test
    fun observersTrackRealScrollStateAndRemoveOnlyTheirOwnLifetime() {
        val activity = SkinScrollActivity()
        val firstState = ScrollState(0)
        val secondState = ScrollState(0)
        val showFirst = mutableStateOf(true)
        lateinit var scope: CoroutineScope
        lateinit var firstScroll: Job
        lateinit var secondScroll: Job
        compose.setContent {
            val currentScope = rememberCoroutineScope()
            SideEffect { scope = currentScope }
            CompositionLocalProvider(LocalSkinScrollActivity provides activity) {
                if (showFirst.value) ObserveSkinScroll(firstState)
                ObserveSkinScroll(secondState)
            }
        }
        compose.runOnIdle {
            assertFalse(activity.isScrolling.value)
            firstScroll = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                firstState.scroll { awaitCancellation() }
            }
            secondScroll = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                secondState.scroll { awaitCancellation() }
            }
        }
        compose.runOnIdle { assertTrue(activity.isScrolling.value) }

        compose.runOnIdle { showFirst.value = false }
        compose.runOnIdle {
            assertTrue("另一个观察者仍在滚动时，页面离开不能清空状态", activity.isScrolling.value)
            secondScroll.cancel()
        }
        compose.runOnIdle {
            assertFalse("已离开页面的滚动任务不能留下全局滚动状态", activity.isScrolling.value)
            firstScroll.cancel()
        }
    }
}
