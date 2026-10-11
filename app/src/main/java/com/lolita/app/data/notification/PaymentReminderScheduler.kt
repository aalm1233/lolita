package com.lolita.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.domain.usecase.PaymentReminderIdentity
import com.lolita.app.domain.usecase.PaymentReminderPolicy

/**
 * Result of scheduling a reminder
 */
enum class ReminderScheduleResult {
    EXACT,      // Exact alarm scheduled successfully
    INEXACT,    // Fell back to inexact alarm (permission not granted)
    SKIPPED,    // The same reminder was already delivered
    CANCELLED   // Existing reminder was cancelled
}

/**
 * Scheduler for payment reminders using AlarmManager
 */
class PaymentReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val deliveries = PaymentReminderDeliveryStore(context)

    /**
     * Schedule a reminder for a payment
     * @return ReminderScheduleResult indicating what happened
     */
    fun scheduleReminder(payment: Payment, itemName: String): ReminderScheduleResult {
        if (!payment.reminderSet || payment.isPaid) {
            cancelReminder(payment.id)
            return ReminderScheduleResult.CANCELLED
        }

        val identity = PaymentReminderIdentity(payment.id, payment.priceId, payment.dueDate,
            payment.customReminderDays ?: 1)
        // Missed reminders still catch up after one minute, once for each due-date/configuration.
        val reminderTimeMillis = PaymentReminderPolicy.triggerTime(identity, System.currentTimeMillis(),
            deliveries.wasDelivered(identity)) ?: run {
            cancelReminder(payment.id)
            return ReminderScheduleResult.SKIPPED
        }

        val intent = createReminderIntent(payment, itemName).apply {
            putExtra(PaymentReminderReceiver.DUE_DATE, payment.dueDate)
            putExtra(PaymentReminderReceiver.REMINDER_IDENTITY, identity.storageKey)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (payment.id % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use setExactAndAllowWhileIdle for Android 12+ compatibility
        // Check canScheduleExactAlarms on Android 12+ to avoid SecurityException
        var isExact = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMillis,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm when exact alarm permission not granted
                isExact = false
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminderTimeMillis,
                pendingIntent
            )
        }
        return if (isExact) ReminderScheduleResult.EXACT else ReminderScheduleResult.INEXACT
    }

    /**
     * Cancel a scheduled reminder for a payment
     * @param paymentId The ID of the payment
     */
    fun cancelReminder(paymentId: Long) {
        val intent = createReminderIntent(
            Payment(id = paymentId, amount = 0.0, dueDate = 0, priceId = 0),
            ""
        )

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (paymentId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * Cancel all scheduled reminders (useful for testing or app reset)
     */
    fun cancelAllReminders(paymentIds: List<Long>) {
        paymentIds.forEach { paymentId ->
            cancelReminder(paymentId)
        }
    }

    private fun createReminderIntent(payment: Payment, itemName: String): Intent {
        return Intent(context, PaymentReminderReceiver::class.java).apply {
            putExtra(PaymentReminderReceiver.PAYMENT_ID, payment.id)
            putExtra(PaymentReminderReceiver.AMOUNT, payment.amount)
            putExtra(PaymentReminderReceiver.ITEM_NAME, itemName)
        }
    }
}
