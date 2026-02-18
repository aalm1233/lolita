package com.lolita.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lolita.app.R
import com.lolita.app.ui.MainActivity
import kotlinx.coroutines.runBlocking

class DailyOutfitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val hasLog = runBlocking {
            try {
                val repo = com.lolita.app.di.AppModule.outfitLogRepository()
                repo.getTodayOutfitLog() != null
            } catch (e: Exception) { false }
        }
        if (hasLog) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "穿搭提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "每日穿搭记录提醒" }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "quick_outfit_log")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天穿了什么？")
            .setContentText("记录一下今天的穿搭吧~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "outfit_reminder"
        private const val NOTIFICATION_ID = 8888
    }
}
