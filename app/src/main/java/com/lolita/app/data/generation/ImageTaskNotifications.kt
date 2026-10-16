package com.lolita.app.data.generation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lolita.app.R
import com.lolita.app.ui.MainActivity

object ImageTaskNotifications {
    const val ID = 4801
    private const val CHANNEL = "image_tasks"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "图像生成任务", NotificationManager.IMPORTANCE_LOW))
    }
    fun notification(context: Context, title: String = "图像生成任务", text: String = "正在准备，请稍候"): Notification {
        val intent = Intent(context, MainActivity::class.java).putExtra("navigate_to", "image_tasks")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_PROGRESS).build()
    }
}
