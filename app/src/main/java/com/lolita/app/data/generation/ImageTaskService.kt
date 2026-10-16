package com.lolita.app.data.generation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lolita.app.di.AppModule
import kotlinx.coroutines.*

/** Started only by an accepted user submission. Android must never redeliver paid work. */
class ImageTaskService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            ImageTaskNotifications.createChannel(this)
            ServiceCompat.startForeground(this, ImageTaskNotifications.ID,
                ImageTaskNotifications.notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            foregroundReady = true
        } catch (_: RuntimeException) {
            AppModule.imageTaskCoordinator().serviceStartFailed()
            stopSelf()
            return
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lolita:image-task").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        serviceScope.launch {
            try {
                AppModule.imageTaskRunner().runQueue { task ->
                    withContext(Dispatchers.Main.immediate) {
                        if (task?.status?.isActive == true) {
                            wakeLock?.acquire(13 * 60 * 1000L)
                            val manager = getSystemService(android.app.NotificationManager::class.java)
                            manager.notify(ImageTaskNotifications.ID,
                                ImageTaskNotifications.notification(this@ImageTaskService, task.title, "正在生成，可离开页面或锁屏等待"))
                        } else releaseWakeLock()
                    }
                }
            } finally {
                // A newer onStartCommand owns a newer ID and must be allowed to drain.
                if (stopSelfResult(startId)) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        serviceScope.cancel(CancellationException("系统后台执行时限已到"))
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    private fun releaseWakeLock() { wakeLock?.takeIf { it.isHeld }?.release() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ImageTaskService::class.java))
        }
    }
}
