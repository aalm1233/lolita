package com.lolita.app.data.generation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lolita.app.domain.model.ImageTaskExecutionSummary
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.R
import com.lolita.app.ui.MainActivity

object ImageTaskNotifications {
    const val ID = 4801
    private const val CHANNEL = "image_tasks"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "图像生成任务", NotificationManager.IMPORTANCE_LOW))
    }
    fun notification(context: Context, summary: ImageTaskExecutionSummary): Notification {
        val stage = when (summary.status) {
            ImageTaskStatus.PREPARING -> "正在准备"
            ImageTaskStatus.GENERATING -> "正在生成"
            ImageTaskStatus.SAVING -> "正在保存"
            else -> if (summary.queuedCount > 0) "等待处理" else "处理完毕"
        }
        val queue = if (summary.queuedCount > 0) " · 排队 ${summary.queuedCount} 项" else ""
        return notification(context, summary.title ?: "图像生成任务", "$stage$queue，可在任务中心查看")
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
