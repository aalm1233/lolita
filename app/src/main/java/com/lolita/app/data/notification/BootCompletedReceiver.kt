package com.lolita.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
                withTimeout(9000) {
                    AppModule.init(context.applicationContext)
                    val db = AppModule.database()
                    val scheduler = PaymentReminderScheduler(context)
                    // Single query with JOIN to get both payment entity and item name
                    val paymentsWithInfo = db.paymentDao().getPendingReminderPaymentsWithItemInfoList()
                    val paymentEntities = db.paymentDao().getPendingReminderPaymentsList()
                    val paymentMap = paymentEntities.associateBy { it.id }

                    paymentsWithInfo.forEach { info ->
                        val payment = paymentMap[info.paymentId] ?: return@forEach
                        try {
                            scheduler.scheduleReminder(payment, info.itemName)
                        } catch (_: Exception) { }
                    }

                    // Reschedule daily outfit reminder if enabled
                    val appPreferences = AppModule.appPreferences()
                    val enabled = appPreferences.outfitReminderEnabled.first()
                    if (enabled) {
                        val hour = appPreferences.outfitReminderHour.first()
                        DailyOutfitReminderScheduler(context).schedule(hour)
                    }
                }
            } catch (_: Exception) {
                // Silently fail — reminders won't be rescheduled but app won't crash
            } finally {
                pendingResult.finish()
            }
        }
    }
}
