package com.lolita.app.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lolita.app.ui.MainActivity
import com.lolita.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver for handling payment reminder notifications
 */
class PaymentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(PAYMENT_ID, -1L)
        val amount = intent.getDoubleExtra(AMOUNT, 0.0)
        val itemName = intent.getStringExtra(ITEM_NAME) ?: "服饰"
        val dueDate = intent.getLongExtra(DUE_DATE, System.currentTimeMillis())

        if (paymentId == -1L) return

        showNotification(context, paymentId, amount, itemName, dueDate)
    }

    private fun showNotification(
        context: Context,
        paymentId: Long,
        amount: Double,
        itemName: String,
        dueDate: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = NotificationChannelSetup.getNotificationChannelId()

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        // Create intent to open app when notification is tapped
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (paymentId % Int.MAX_VALUE).toInt(),
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Format the due date
        val dateFormat = SimpleDateFormat("MM月dd日", Locale.CHINA)
        val dueDateStr = dateFormat.format(Date(dueDate))

        val notification = NotificationChannelSetup.getPaymentNotificationBuilder(context)
            .setContentTitle("付款提醒")
            .setContentText("$itemName ¥${String.format("%.2f", amount)} 将于 $dueDateStr 到期")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$itemName 的付款 ¥${String.format("%.2f", amount)} 将于 $dueDateStr 到期，请记得按时付款。")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((paymentId % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        const val PAYMENT_ID = "payment_id"
        const val AMOUNT = "amount"
        const val ITEM_NAME = "item_name"
        const val DUE_DATE = "due_date"
    }
}
