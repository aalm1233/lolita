package com.lolita.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lolita.app.data.local.entity.Payment
import java.util.Calendar

/**
 * Result of scheduling a reminder
 */
enum class ReminderScheduleResult {
    EXACT,      // Exact alarm scheduled successfully
    INEXACT,    // Fell back to inexact alarm (permission not granted)
    SKIPPED,    // Not scheduled (paid, no reminder, or in the past)
    CANCELLED   // Existing reminder was cancelled
}

/**
 * Scheduler for payment reminders using AlarmManager
 */
class PaymentReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule a reminder for a payment
     * @return ReminderScheduleResult indicating what happened
     */
    fun scheduleReminder(payment: Payment, itemName: String): ReminderScheduleResult {
        if (!payment.reminderSet || payment.isPaid) {
            cancelReminder(payment.id)
            return ReminderScheduleResult.CANCELLED
        }

        // Calculate reminder time: due date minus custom reminder days (default 1 day)
        val reminderDays = payment.customReminderDays ?: 1
        val reminderTimeMillis = payment.dueDate - (reminderDays * DAY_IN_MILLIS)

        // Don't schedule if reminder time is in the past
        if (reminderTimeMillis < System.currentTimeMillis()) {
            return ReminderScheduleResult.SKIPPED
        }

        val intent = createReminderIntent(payment, itemName).apply {
            putExtra(PaymentReminderReceiver.DUE_DATE, payment.dueDate)
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

    companion object {
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
    }
}
