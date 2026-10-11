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
import com.lolita.app.di.AppModule
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.domain.usecase.PaymentReminderIdentity
import com.lolita.app.domain.usecase.PaymentReminderPolicy
import kotlinx.coroutines.*

/**
 * BroadcastReceiver for handling payment reminder notifications
 */
class PaymentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(PAYMENT_ID, Long.MIN_VALUE)
        val itemName = intent.getStringExtra(ITEM_NAME) ?: "服饰"
        val scheduledDueDate = intent.takeIf { it.hasExtra(DUE_DATE) }?.getLongExtra(DUE_DATE, 0L)
        val scheduledIdentity = intent.getStringExtra(REMINDER_IDENTITY)

        if (!intent.hasExtra(PAYMENT_ID) || paymentId == Long.MIN_VALUE) return

        // Alarm extras can be stale after editing or restoring an order. Re-check committed data.
        if (paymentId >= 0) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppModule.init(context)
                val db = AppModule.database()
                val stage = db.orderDao().getStage(-paymentId) ?: return@launch
                val price = db.priceDao().getPriceById(stage.priceId) ?: return@launch
                val binding = db.orderDao().getBinding(price.itemId) ?: return@launch
                if (binding.priceId != price.id || binding.purchaseRecordStatus != PurchaseRecordStatus.TRACKED ||
                    !stage.reminderSet || stage.dueDate == null) return@launch
                val identity = PaymentReminderIdentity(paymentId, price.id, stage.dueDate,
                    stage.customReminderDays ?: 1)
                val deliveries = PaymentReminderDeliveryStore(context)
                if (!PaymentReminderPolicy.canDeliver(identity, scheduledIdentity, scheduledDueDate,
                        System.currentTimeMillis(), deliveries.wasDelivered(identity))) return@launch
                val actual = db.paymentDao().getPaymentsByPriceList(price.id).filter { it.isPaid }
                val stageRemaining = com.lolita.app.domain.usecase.StageAccounting.remainingByStage(
                    db.orderDao().getStages(price.id),actual,price.totalPrice)[stage.id] ?: 0.0
                if (stageRemaining <= 0) return@launch
                val name = db.itemDao().getItemById(price.itemId)?.name ?: itemName
                deliveries.deliverOnce(identity) {
                    showNotification(context, paymentId, stageRemaining, name + " · " + stage.name, stage.dueDate)
                }
            } catch (e: Exception) {
                android.util.Log.w("PaymentReminder", "Ignoring invalid or changed installment", e)
            } finally { result.finish() }
        }

    }

    private fun showNotification(
        context: Context,
        paymentId: Long,
        amount: Double,
        itemName: String,
        dueDate: Long
    ): Boolean {
        NotificationChannelSetup.createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = NotificationChannelSetup.getNotificationChannelId()
        if (!notificationManager.areNotificationsEnabled() ||
            notificationManager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // Create intent to open app when notification is tapped
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val overdue = dueDate < System.currentTimeMillis()
        val dueDescription = if (overdue) "已于 $dueDateStr 到期" else "将于 $dueDateStr 到期"

        val notification = NotificationChannelSetup.getPaymentNotificationBuilder(context)
            .setContentTitle("付款提醒")
            .setContentText("$itemName ¥${String.format("%.2f", amount)} $dueDescription")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$itemName 的付款 ¥${String.format("%.2f", amount)} $dueDescription，" +
                        if (overdue) "请确认是否仍待付款。" else "请记得按时付款。")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((paymentId % Int.MAX_VALUE).toInt(), notification)
        return true
    }

    companion object {
        const val PAYMENT_ID = "payment_id"
        const val AMOUNT = "amount"
        const val ITEM_NAME = "item_name"
        const val DUE_DATE = "due_date"
        const val REMINDER_IDENTITY = "reminder_identity"
    }
}
