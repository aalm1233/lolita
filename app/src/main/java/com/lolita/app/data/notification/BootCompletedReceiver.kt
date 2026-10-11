package com.lolita.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Recover alarms after reboot/update and rebuild wall-clock reminders after clock/zone changes.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RECOVERY_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    AppModule.init(context.applicationContext)

                    NotificationChannelSetup.createNotificationChannel(context)

                    try {
                        DailyOutfitReminderScheduler(context).reschedule()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("BootCompletedReceiver", "Failed to reschedule outfit reminder", e)
                    }

                    try {
                        AppModule.orderRepository().reconcileReminders()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("BootCompletedReceiver", "Failed to reschedule payment reminders", e)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("BootCompletedReceiver", "Reminder recovery timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BootCompletedReceiver", "Failed to reschedule reminders", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val RECOVERY_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
