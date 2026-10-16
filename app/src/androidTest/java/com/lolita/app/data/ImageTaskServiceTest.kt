package com.lolita.app.data

import androidx.test.core.app.ActivityScenario
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.app.ActivityManager
import android.app.Notification
import android.content.ComponentName
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.ImageTaskNotifications
import com.lolita.app.data.generation.ImageTaskService
import com.lolita.app.di.AppModule
import com.lolita.app.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTaskServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val taskCenterMarker = "离开页面后继续处理。"

    @Test fun foregroundNotificationOpensTaskCenterAndEmptyServiceStops() = runBlocking {
        AppModule.imageTaskCoordinator().awaitStartup()
        // This lifecycle test never enqueues generation or touches credentials.
        assertTrue(AppModule.imageTaskRepository().getAll().none { it.status.retainsSourceImages })
        val component = ComponentName(context, ImageTaskService::class.java)
        val info = context.packageManager.getServiceInfo(component, 0)
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            val notification = ImageTaskNotifications.notification(context)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            scenario.onActivity { activity -> ImageTaskService.start(activity); notification.contentIntent.send() }
            scenario.recreate() // Preserve an entry request even if recreation wins the routing frame.
            instrumentation.waitForIdleSync()
            awaitText(taskCenterMarker)
            scenario.onActivity { assertNull(it.intent.getStringExtra("navigate_to")) }
            scenario.recreate()
            awaitText(taskCenterMarker)
            var node = findText(instrumentation.uiAutomation.rootInActiveWindow, "个人", exact = true) ?: error("个人导航未出现")
            while (!node.isClickable && node.parent != null) node = node.parent
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(10_000) {
                while (hasText(taskCenterMarker)) delay(50)
            }
            scenario.recreate()
            awaitText("任务中心") // personal menu entry proves this is the restored personal screen.
            assertFalse(hasText(taskCenterMarker))
            scenario.onActivity { assertNull(it.intent.getStringExtra("navigate_to")) }
            val manager = context.getSystemService(ActivityManager::class.java)
            withTimeout(10_000) {
                @Suppress("DEPRECATION")
                while (manager.getRunningServices(100).any { it.service == component }) delay(50)
            }
        } finally { scenario.close() }
    }
    private fun hasText(text: String): Boolean = findText(instrumentation.uiAutomation.rootInActiveWindow, text) != null
    private fun findText(node: AccessibilityNodeInfo?, text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.let { if (exact) it.toString() == text else it.contains(text) } == true) return node
        for (index in 0 until node.childCount) findText(node.getChild(index), text, exact)?.let { return it }
        return null
    }
    private suspend fun awaitText(text: String) {
        try { withTimeout(10_000) { while (!hasText(text)) delay(50) } }
        catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            fun describe(node: AccessibilityNodeInfo?): String = if (node == null) "<no root>" else buildString {
                append(node.text); append(" "); append(node.contentDescription); append("\n")
                for (index in 0 until node.childCount) append(describe(node.getChild(index)))
            }
            throw AssertionError("Expected $text, accessibility tree: ${describe(instrumentation.uiAutomation.rootInActiveWindow)}", failure)
        }
    }
}
