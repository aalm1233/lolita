package com.lolita.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyOutfitReminderPolicyTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val config = DailyOutfitReminderConfig(true, 20, 45)

    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    @Test
    fun selectedMinutesRemainEligibleLaterToday() {
        val next = DailyOutfitReminderPolicy.nextOccurrence(config,
            time("2026-10-11T20:15:00+08:00[Asia/Shanghai]"), shanghai)!!
        assertEquals(LocalDate.of(2026, 10, 11), next.date)
        assertEquals(time("2026-10-11T20:45:00+08:00[Asia/Shanghai]"), next.triggerAtMillis)
    }

    @Test
    fun passedTimeSchedulesNextDayAndDisabledSettingCancels() {
        val now = time("2026-10-11T20:45:00+08:00[Asia/Shanghai]")
        assertEquals(LocalDate.of(2026, 10, 12),
            DailyOutfitReminderPolicy.nextOccurrence(config, now, shanghai)!!.date)
        assertNull(DailyOutfitReminderPolicy.nextOccurrence(config.copy(enabled = false), now, shanghai))
    }

    @Test
    fun springGapNormalizesOnlyTheMissingDayAndFollowingDayReturnsToChosenTime() {
        val zone = ZoneId.of("America/New_York")
        val twoAm = DailyOutfitReminderConfig(true, 2, 0)
        val gap = DailyOutfitReminderPolicy.nextOccurrence(twoAm,
            time("2026-03-07T23:00:00-05:00[America/New_York]"), zone)!!
        assertEquals(time("2026-03-08T03:00:00-04:00[America/New_York]"), gap.triggerAtMillis)
        val next = DailyOutfitReminderPolicy.nextOccurrence(twoAm, gap.triggerAtMillis, zone)!!
        assertEquals(time("2026-03-09T02:00:00-04:00[America/New_York]"), next.triggerAtMillis)
    }

    @Test
    fun repeatedFallHourDoesNotScheduleAnotherReminderOnTheSameDate() {
        val zone = ZoneId.of("America/New_York")
        val early = DailyOutfitReminderConfig(true, 1, 30)
        val first = DailyOutfitReminderPolicy.nextOccurrence(early,
            time("2026-11-01T00:00:00-04:00[America/New_York]"), zone)!!
        assertEquals(time("2026-11-01T01:30:00-04:00[America/New_York]"), first.triggerAtMillis)
        val next = DailyOutfitReminderPolicy.nextOccurrence(early,
            time("2026-11-01T01:15:00-05:00[America/New_York]"), zone)!!
        assertEquals(LocalDate.of(2026, 11, 2), next.date)
        assertEquals(25 * 60 * 60 * 1000L, next.triggerAtMillis - first.triggerAtMillis)
    }

    @Test
    fun disabledOrChangedConfigRejectsAlreadyDispatchedAlarm() {
        val now = time("2026-10-11T20:45:00+08:00[Asia/Shanghai]")
        val alarm = DailyOutfitReminderPolicy.nextOccurrence(config, now - 60_000, shanghai)!!
        assertTrue(DailyOutfitReminderPolicy.canDeliver(config, alarm, now, shanghai, false, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config.copy(enabled = false), alarm, now,
            shanghai, false, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config.copy(hour = 21), alarm, now,
            shanghai, false, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config.copy(minute = 46), alarm, now,
            shanghai, false, false))
    }

    @Test
    fun recordedOutfitOrSuccessfulDeliverySuppressesToday() {
        val now = time("2026-10-11T20:45:00+08:00[Asia/Shanghai]")
        val alarm = DailyOutfitReminderPolicy.nextOccurrence(config, now - 60_000, shanghai)!!
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, alarm, now, shanghai, true, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, alarm, now, shanghai, false, true))
        val changed = config.copy(hour = 21)
        assertEquals(LocalDate.of(2026, 10, 12),
            DailyOutfitReminderPolicy.nextOccurrence(changed, now, shanghai, todayDelivered = true)!!.date)
    }

    @Test
    fun clockRollbackCrossDayAndUnidentifiedLegacyAlarmCannotDeliver() {
        val now = time("2026-10-11T20:45:00+08:00[Asia/Shanghai]")
        val alarm = DailyOutfitReminderPolicy.nextOccurrence(config, now - 60_000, shanghai)!!
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, alarm, now - 1, shanghai, false, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, alarm, now + 86_400_000,
            shanghai, false, false))
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, null, now, shanghai, false, false))
    }

    @Test
    fun timezoneChangeRejectsOldEpochAndRebuildsLocalTimeWithoutSameDayDuplicate() {
        val now = time("2026-10-11T19:00:00+08:00[Asia/Shanghai]")
        val alarm = DailyOutfitReminderPolicy.nextOccurrence(config, now, shanghai)!!
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertFalse(DailyOutfitReminderPolicy.canDeliver(config, alarm, alarm.triggerAtMillis,
            tokyo, false, false))
        val replacement = DailyOutfitReminderPolicy.nextOccurrence(config, now, tokyo)!!
        assertEquals(time("2026-10-11T20:45:00+09:00[Asia/Tokyo]"), replacement.triggerAtMillis)
        assertEquals(LocalDate.of(2026, 10, 12),
            DailyOutfitReminderPolicy.nextOccurrence(config, now, tokyo, todayDelivered = true)!!.date)
    }

    @Test
    fun unsuccessfulNotificationCanRetryAndDateReceiptSurvivesPolicyRecreation() {
        val receipts = mutableSetOf<LocalDate>()
        val date = LocalDate.of(2026, 10, 11)
        val delivery = DailyOutfitReminderDelivery(receipts::contains) { receipts.add(it) }
        assertFalse(delivery.deliverOnce(date) { false })
        assertTrue(receipts.isEmpty())
        assertTrue(delivery.deliverOnce(date) { true })
        val recreated = DailyOutfitReminderDelivery(receipts::contains) { receipts.add(it) }
        assertFalse(recreated.deliverOnce(date) { throw AssertionError("Duplicate notification") })
        assertTrue(recreated.deliverOnce(date.plusDays(1)) { true })
        assertFalse(recreated.deliverOnce(date) { throw AssertionError("Clock rollback duplicate") })
        assertEquals(2, receipts.size)
    }
}
