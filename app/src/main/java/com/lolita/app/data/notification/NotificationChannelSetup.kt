package com.lolita.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.lolita.app.R

/**
 * Notification channel setup for Android O+ (API 26+)
 */
object NotificationChannelSetup {

    private const val OUTFIT_CHANNEL_ID = "outfit_reminder"

    private const val CHANNEL_ID = "payment_reminder_channel"
    private const val CHANNEL_NAME = "Payment Reminders"
    private const val CHANNEL_DESCRIPTION = "Notifications for upcoming payment due dates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createOutfitNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(OUTFIT_CHANNEL_ID, "穿搭提醒",
            NotificationManager.IMPORTANCE_DEFAULT).apply { description = "每日穿搭记录提醒" })
    }

    fun canShowOutfitNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(OUTFIT_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun outfitNotificationSettingsIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (manager.getNotificationChannel(OUTFIT_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, OUTFIT_CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun getOutfitNotificationChannelId(): String = OUTFIT_CHANNEL_ID

    fun getNotificationChannelId(): String = CHANNEL_ID

    fun getPaymentNotificationBuilder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
    }
}
