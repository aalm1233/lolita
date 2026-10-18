package com.lolita.app.ui.screen.tryon

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.ui.navigation.Screen
import org.junit.Assert.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.screen.tasks.ImageTaskNavigationFixture
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TryOnNavigationTest : ImageTaskNavigationFixture() {
    @Test fun allBottomTabsRemainEnabledDuringTryOn() { start(false); allBottomTabsRemainAvailable() }
    @Test fun quickOutfitOpensDuringTryOnWithoutCancellingTask() { start(false); quickOutfitDoesNotWaitForGeneration() }
    @Test fun systemBackLeavesTryOnWithoutCancellingItsTask() {
        start(false); compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ItemDetail.route }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
    }
    @Test fun toolbarBackLeavesTryOnWithoutCancellingItsTask() {
        start(false); compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ItemDetail.route }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
    }
}
