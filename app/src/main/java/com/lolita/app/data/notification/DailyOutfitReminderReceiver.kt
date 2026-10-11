package com.lolita.app.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lolita.app.R
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.DailyOutfitReminderOccurrence
import com.lolita.app.domain.usecase.DailyOutfitReminderPolicy
import com.lolita.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId

class DailyOutfitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(6_000) {
                    AppModule.init(context.applicationContext)
                    val scheduled = readOccurrence(intent) ?: return@withTimeout
                    val preferences = AppModule.appPreferences()
                    val config = preferences.outfitReminderConfig.first()
                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()
                    val deliveries = DailyOutfitReminderDeliveryStore(context)
                    if (!DailyOutfitReminderPolicy.canDeliver(config, scheduled, now, zone,
                            hasTodayLog = false, todayDelivered = deliveries.wasDelivered(scheduled.date))) {
                        return@withTimeout
                    }
                    val hasLog = AppModule.outfitLogRepository().getTodayOutfitLog(now,
                        java.util.TimeZone.getTimeZone(zone)) != null
                    // Preference reads and the DB query suspend; check the latest configuration again.
                    val latestConfig = preferences.outfitReminderConfig.first()
                    if (DailyOutfitReminderPolicy.canDeliver(latestConfig, scheduled,
                            System.currentTimeMillis(), ZoneId.systemDefault(), hasLog,
                            deliveries.wasDelivered(scheduled.date))) {
                        deliveries.deliverOnce(scheduled.date) { showReminderNotification(context) }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("DailyOutfitReminder", "Outfit reminder check timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DailyOutfitReminder", "Failed to check today outfit log", e)
            } finally {
                try {
                    // Re-register even on a query timeout, within the broadcast time budget.
                    withContext(NonCancellable) {
                        withTimeout(2_000) { DailyOutfitReminderScheduler(context).reschedule() }
                    }
                } catch (e: Exception) {
                    Log.w("DailyOutfitReminder", "Failed to reschedule next reminder", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun readOccurrence(intent: Intent?): DailyOutfitReminderOccurrence? {
        if (intent == null || !intent.hasExtra(DailyOutfitReminderScheduler.REMINDER_TIME) ||
            !intent.hasExtra(DailyOutfitReminderScheduler.REMINDER_HOUR) ||
            !intent.hasExtra(DailyOutfitReminderScheduler.REMINDER_MINUTE)) return null
        val date = intent.getStringExtra(DailyOutfitReminderScheduler.REMINDER_DATE) ?: return null
        val zone = intent.getStringExtra(DailyOutfitReminderScheduler.REMINDER_ZONE) ?: return null
        return DailyOutfitReminderOccurrence(LocalDate.parse(date),
            intent.getLongExtra(DailyOutfitReminderScheduler.REMINDER_TIME, 0),
            intent.getIntExtra(DailyOutfitReminderScheduler.REMINDER_HOUR, -1),
            intent.getIntExtra(DailyOutfitReminderScheduler.REMINDER_MINUTE, -1), zone)
    }

    private fun showReminderNotification(context: Context): Boolean {
        NotificationChannelSetup.createOutfitNotificationChannel(context)
        if (!NotificationChannelSetup.canShowOutfitNotifications(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "quick_outfit_log")
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannelSetup.getOutfitNotificationChannelId())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天穿了什么？")
            .setContentText("记录一下今天的穿搭吧~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        private const val NOTIFICATION_ID = 8888
    }
}
