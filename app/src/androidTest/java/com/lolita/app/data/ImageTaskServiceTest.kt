package com.lolita.app.data

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

    @Test fun foregroundNotificationOpensTaskCenterAndEmptyServiceStops() = runBlocking {
        AppModule.imageTaskCoordinator().awaitStartup()
        // This lifecycle test never enqueues generation or touches credentials.
        assertTrue(AppModule.imageTaskRepository().getAll().none { it.status.retainsSourceImages })
        val component = ComponentName(context, ImageTaskService::class.java)
        val info = context.packageManager.getServiceInfo(component, 0)
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val notification = ImageTaskNotifications.notification(context)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            instrumentation.runOnMainSync {
                ImageTaskService.start(activity)
                notification.contentIntent.send()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals("image_tasks", activity.intent.getStringExtra("navigate_to")) }
            val manager = context.getSystemService(ActivityManager::class.java)
            withTimeout(10_000) {
                @Suppress("DEPRECATION")
                while (manager.getRunningServices(100).any { it.service == component }) delay(50)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
