package com.lolita.app.ui.screen.tasks

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTaskNavigationTest : ImageTaskNavigationFixture() {
    @Test fun notificationEntryWaitsForNavHostThenConsumesExactlyOnce() {
        start(false)
        compose.runOnIdle { openTasks.value = true }
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ImageTasks.route && opened == 1 }
        compose.onNodeWithTag("imageTasks").assertExists()
        compose.runOnIdle { controller.navigate(Screen.Settings.route) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.Settings.route, controller.currentDestination?.route); assertEquals(1, opened) }
    }
    @Test fun generationTaskEntryOpensItsDetailAndRetryNeverAutomaticallySubmits() {
        start(false)
        compose.onNodeWithTag("tryOnWorkspace").performScrollToNode(hasText("查看任务"))
        compose.onNodeWithText("查看任务").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.ImageTaskDetail.route }
        compose.onNodeWithText("生成中").assertExists()
        assertEquals(1, submissions)
    }
}
