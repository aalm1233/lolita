package com.lolita.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all pending payment reminders after device reboot.
 * AlarmManager alarms are cleared on reboot, so we need to re-register them.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppModule.init(context.applicationContext)
                val db = AppModule.database()
                val scheduler = PaymentReminderScheduler(context)
                val payments = db.paymentDao().getPendingReminderPaymentsWithItemInfoList()
                val paymentEntities = db.paymentDao().getPendingReminderPaymentsList()

                val paymentMap = paymentEntities.associateBy { it.id }
                payments.forEach { info ->
                    val payment = paymentMap[info.paymentId] ?: return@forEach
                    scheduler.scheduleReminder(payment, info.itemName)
                }
            } catch (_: Exception) {
                // Silently fail — reminders won't be rescheduled but app won't crash
            } finally {
                pendingResult.finish()
            }
        }
    }
}
