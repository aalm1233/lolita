package com.lolita.app.ui.screen.mannequin

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.ui.screen.tasks.ImageTaskNavigationFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MannequinNavigationTest : ImageTaskNavigationFixture() {
    @Test fun allBottomTabsRemainEnabledDuringMannequinGeneration() { start(true); allBottomTabsRemainAvailable() }
    @Test fun quickOutfitOpensDuringMannequinWithoutCancellingTask() { start(true); quickOutfitDoesNotWaitForGeneration() }
    @Test fun systemBackLeavesGenerationWithoutCancelOrDiscard() {
        start(true); compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ItemDetail.route }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
    }
    @Test fun toolbarBackLeavesGenerationWithoutCancelOrDiscard() {
        start(true); compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ItemDetail.route }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
    }
}
