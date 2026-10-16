package com.lolita.app.ui.screen.tasks

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.domain.model.*
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
    @Test fun failedTryOnUsesSeparateRetryRouteWithoutEnqueuing() {
        assertRetryRoute(mannequin = false, expected = Screen.TryOnRetry.route)
    }
    @Test fun failedMannequinUsesSeparateRetryRouteWithOriginalItemWithoutEnqueuing() {
        assertRetryRoute(mannequin = true, expected = Screen.MannequinRetry.route)
        compose.runOnIdle { assertEquals(11L, controller.currentBackStackEntry!!.arguments!!.getLong("itemId")) }
    }
    @Test fun personalRetryOpensPersonalEditorWithOriginalIdentityWithoutEnqueuing() {
        start(false)
        compose.runOnIdle {
            val request = PersonalModelRequest("原人物", PersonalModelMode.PARAMETERS, BodyMeasurements(165.0, 52.0))
            tasks.value = tasks.value.map { it.copy(kind = ImageTaskKind.PERSONAL_MODEL, status = ImageTaskStatus.FAILED,
                sourceImagePaths = emptyList(), requestJson = ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode()) }
            controller.navigate(Screen.ImageTaskDetail.createRoute(tasks.value.single().id))
        }
        compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasText("核对后重新发起"))
        compose.onNodeWithText("核对后重新发起").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.PersonalModelEdit.route }
        compose.runOnIdle { assertEquals(tasks.value.single().id, controller.currentBackStackEntry!!.arguments!!.getString("retryTaskId")) }
        assertEquals(1, submissions)
    }

    private fun assertRetryRoute(mannequin: Boolean, expected: String) {
        start(mannequin)
        compose.runOnIdle {
            tasks.value = tasks.value.map { it.copy(status = ImageTaskStatus.FAILED, sourceImagePaths = emptyList()) }
            controller.navigate(Screen.ImageTaskDetail.createRoute(tasks.value.single().id))
        }
        compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasText("核对后重新发起"))
        compose.onNodeWithText("核对后重新发起").performClick()
        compose.waitUntil(10_000) { controller.currentDestination?.route == expected }
        compose.runOnIdle { assertEquals(tasks.value.single().id, controller.currentBackStackEntry!!.arguments!!.getString("retryTaskId")) }
        assertEquals(1, submissions)
    }

}
