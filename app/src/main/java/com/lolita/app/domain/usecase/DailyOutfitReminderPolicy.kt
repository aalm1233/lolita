package com.lolita.app.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DailyOutfitReminderConfig(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0
) {
    init {
        require(hour in 0..23) { "Hour must be 0-23" }
        require(minute in 0..59) { "Minute must be 0-59" }
    }
}

data class DailyOutfitReminderOccurrence(
    val date: LocalDate,
    val triggerAtMillis: Long,
    val hour: Int,
    val minute: Int,
    val zoneId: String
)

/** Rebuild each day's wall-clock time so DST gaps do not move following days' reminders. */
object DailyOutfitReminderPolicy {
    fun nextOccurrence(
        config: DailyOutfitReminderConfig,
        nowMillis: Long,
        zone: ZoneId,
        todayDelivered: Boolean = false
    ): DailyOutfitReminderOccurrence? {
        if (!config.enabled) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayOccurrence = occurrence(config, today, zone)
        return if (!todayDelivered && todayOccurrence.triggerAtMillis > nowMillis) todayOccurrence
        else occurrence(config, today.plusDays(1), zone)
    }

    fun canDeliver(
        config: DailyOutfitReminderConfig,
        scheduled: DailyOutfitReminderOccurrence?,
        nowMillis: Long,
        zone: ZoneId,
        hasTodayLog: Boolean,
        todayDelivered: Boolean
    ): Boolean {
        if (!config.enabled || scheduled == null || hasTodayLog || todayDelivered) return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return scheduled == occurrence(config, today, zone) && nowMillis >= scheduled.triggerAtMillis
    }

    private fun occurrence(config: DailyOutfitReminderConfig, date: LocalDate, zone: ZoneId) =
        DailyOutfitReminderOccurrence(date,
            date.atTime(LocalTime.of(config.hour, config.minute)).atZone(zone).toInstant().toEpochMilli(),
            config.hour, config.minute, zone.id)
}

/** Receipts follow the local date, even if the user changes the reminder time later that day. */
class DailyOutfitReminderDelivery(
    private val wasDelivered: (LocalDate) -> Boolean,
    private val markDelivered: (LocalDate) -> Unit
) {
    fun deliverOnce(date: LocalDate, showNotification: () -> Boolean): Boolean = synchronized(deliveryLock) {
        if (wasDelivered(date) || !showNotification()) return@synchronized false
        markDelivered(date)
        true
    }

    companion object {
        private val deliveryLock = Any()
    }
}
