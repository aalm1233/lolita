package com.lolita.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.DailyOutfitReminderPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

class DailyOutfitReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Read inside the shared lock so startup/settings updates cannot install older settings. */
    suspend fun reschedule() = scheduleMutex.withLock {
        val config = AppModule.appPreferences().outfitReminderConfig.first()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val occurrence = DailyOutfitReminderPolicy.nextOccurrence(config, now, zone,
            DailyOutfitReminderDeliveryStore(context).wasDelivered(today))
        cancel()
        if (occurrence == null) return@withLock

        val intent = Intent(context, DailyOutfitReminderReceiver::class.java).apply {
            putExtra(REMINDER_DATE, occurrence.date.toString())
            putExtra(REMINDER_TIME, occurrence.triggerAtMillis)
            putExtra(REMINDER_HOUR, occurrence.hour)
            putExtra(REMINDER_MINUTE, occurrence.minute)
            putExtra(REMINDER_ZONE, occurrence.zoneId)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    occurrence.triggerAtMillis, pendingIntent)
            } catch (_: SecurityException) {
                // Exact-alarm access can be revoked between checking and scheduling.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    occurrence.triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                occurrence.triggerAtMillis, pendingIntent)
        }
    }

    fun cancel() {
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE,
            Intent(context, DailyOutfitReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
    }

    companion object {
        private val scheduleMutex = Mutex()
        private const val REQUEST_CODE = 9999
        internal const val REMINDER_DATE = "outfit_reminder_date"
        internal const val REMINDER_TIME = "outfit_reminder_time"
        internal const val REMINDER_HOUR = "outfit_reminder_hour"
        internal const val REMINDER_MINUTE = "outfit_reminder_minute"
        internal const val REMINDER_ZONE = "outfit_reminder_zone"
    }
}
