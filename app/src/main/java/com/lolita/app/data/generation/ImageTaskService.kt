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
import kotlinx.coroutines.flow.distinctUntilChanged

/** Started only by an accepted user submission. Android must never redeliver paid work. */
class ImageTaskService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: ImageTaskCoordinator.ServiceSession
    private var foregroundReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        session = AppModule.imageTaskCoordinator().openServiceSession()
        try {
            ImageTaskNotifications.createChannel(this)
            ServiceCompat.startForeground(this, ImageTaskNotifications.ID,
                ImageTaskNotifications.notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            foregroundReady = true
        } catch (_: RuntimeException) {
            AppModule.imageTaskCoordinator().serviceStartFailed(session)
            stopSelf()
            return
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lolita:image-task").apply {
            setReferenceCounted(false)
        }
        serviceScope.launch {
            AppModule.imageTaskCoordinator().executionSummary.distinctUntilChanged().collect { summary ->
                if (foregroundReady) getSystemService(android.app.NotificationManager::class.java).notify(
                    ImageTaskNotifications.ID, ImageTaskNotifications.notification(this@ImageTaskService, summary))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (!foregroundReady || !AppModule.imageTaskCoordinator().admitServiceStart(session, taskId)) {
            // A rejected new start only owns currently unassigned submissions. A late
            // callback from this instance cannot take another instance's admitted rows.
            AppModule.imageTaskCoordinator().rejectServiceStart(taskId)
            return START_NOT_STICKY
        }
        serviceScope.launch {
            try {
                AppModule.imageTaskRunner().runQueue(session) { task ->
                    withContext(Dispatchers.Main.immediate) {
                        // Only the claimed-task boundary renews the bounded lock;
                        // notification stage/queue emissions never renew it.
                        if (foregroundReady && task?.status?.isActive == true) {
                            wakeLock?.acquire(13 * 60 * 1000L)
                        } else releaseWakeLock()
                    }
                }
            } finally {
                // A newer onStartCommand owns a newer ID and must be allowed to drain.
                if (foregroundReady && stopSelfResult(startId)) {
                    foregroundReady = false
                    AppModule.imageTaskCoordinator().serviceStopped(session)
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        foregroundReady = false
        AppModule.imageTaskCoordinator().serviceStopped(session, "系统后台执行时限已到；请求可能仍会计费，请核对后重新发起")
        serviceScope.cancel(CancellationException("系统后台执行时限已到"))
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        foregroundReady = false
        AppModule.imageTaskCoordinator().serviceStopped(session)
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    private fun releaseWakeLock() { wakeLock?.takeIf { it.isHeld }?.release() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TASK_ID = "image_task_id"
        fun start(context: Context, taskId: String? = null) {
            ContextCompat.startForegroundService(context, Intent(context, ImageTaskService::class.java).putExtra(EXTRA_TASK_ID, taskId))
        }
    }
}
