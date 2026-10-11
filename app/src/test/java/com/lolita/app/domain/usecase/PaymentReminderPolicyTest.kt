package com.lolita.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PaymentReminderPolicyTest {
    private val day = 86_400_000L
    private val identity = PaymentReminderIdentity(-7, 31, 10 * day, 2)

    @Test
    fun calendarUsesTheSameDefaultAndCustomAdvanceDaysAsTheAppAlarm() {
        assertEquals(1_440, PaymentReminderPolicy.calendarReminderMinutes(null))
        assertEquals(1_440, PaymentReminderPolicy.calendarReminderMinutes(1))
        assertEquals(10_080, PaymentReminderPolicy.calendarReminderMinutes(7))
        assertEquals(day, PaymentReminderPolicy.calendarReminderMinutes(null) * 60_000L)
        assertEquals(7 * day, PaymentReminderPolicy.calendarReminderMinutes(7) * 60_000L)
    }

    @Test
    fun calendarAdvanceMinutesStayPositiveAndNeverOverflowTheProviderInteger() {
        assertEquals(Int.MAX_VALUE, PaymentReminderPolicy.calendarReminderMinutes(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, PaymentReminderPolicy.calendarReminderMinutes(Int.MAX_VALUE / 1_440 + 1))
        assertEquals(1, PaymentReminderPolicy.calendarReminderMinutes(0))
        assertEquals(1, PaymentReminderPolicy.calendarReminderMinutes(Int.MIN_VALUE))
    }

    @Test
    fun futureReminderKeepsItsConfiguredTimeAndMissedReminderCatchesUp() {
        assertEquals(8 * day, PaymentReminderPolicy.triggerTime(identity, 7 * day, false))
        assertEquals(11 * day + 60_000, PaymentReminderPolicy.triggerTime(identity, 11 * day, false))
        assertEquals(8 * day + 60_000, PaymentReminderPolicy.triggerTime(identity, 8 * day, false))
    }

    @Test
    fun alreadyDeliveredReminderIsNeverRescheduledAfterRestartOrClockChanges() {
        assertNull(PaymentReminderPolicy.triggerTime(identity, 7 * day, true))
        assertNull(PaymentReminderPolicy.triggerTime(identity, 11 * day, true))
        assertFalse(PaymentReminderPolicy.canDeliver(identity, identity.storageKey,
            identity.dueDate, 11 * day, true))
    }

    @Test
    fun changedDueDateReminderDaysOrOrderHasANewDeliveryIdentity() {
        val variants = listOf(identity.copy(dueDate = 12 * day), identity.copy(reminderDays = 1),
            identity.copy(priceId = 32), identity.copy(paymentId = -8))
        assertEquals(5, (variants + identity).map { it.storageKey }.toSet().size)
        variants.forEach { changed ->
            assertFalse(PaymentReminderPolicy.canDeliver(changed, identity.storageKey,
                changed.dueDate, 15 * day, false))
            assertTrue(PaymentReminderPolicy.canDeliver(changed, changed.storageKey,
                changed.dueDate, 15 * day, false))
        }
    }

    @Test
    fun receiverRejectsStaleMissingAndEarlyAlarmDates() {
        assertFalse(PaymentReminderPolicy.canDeliver(identity, identity.storageKey,
            9 * day, 11 * day, false))
        assertFalse(PaymentReminderPolicy.canDeliver(identity, identity.storageKey,
            null, 11 * day, false))
        assertFalse(PaymentReminderPolicy.canDeliver(identity, identity.storageKey,
            identity.dueDate, 7 * day, false))
        assertTrue(PaymentReminderPolicy.canDeliver(identity, identity.storageKey,
            identity.dueDate, 8 * day, false))
    }

    @Test
    fun previouslyInstalledAlarmsWithoutAnIdentityCanStillDeliverOnTime() {
        assertTrue(PaymentReminderPolicy.canDeliver(identity, null, identity.dueDate, 9 * day, false))
        assertFalse(PaymentReminderPolicy.canDeliver(identity, null, identity.dueDate, 7 * day, false))
    }

    @Test
    fun failedOrDisabledNotificationDoesNotConsumeTheRetry() {
        val receipts = mutableSetOf<String>()
        val delivery = tracker(receipts)
        assertFalse(delivery.deliverOnce(identity) { false })
        assertTrue(receipts.isEmpty())
        assertTrue(delivery.deliverOnce(identity) { true })
        assertEquals(setOf(identity.storageKey), receipts)
    }

    @Test
    fun savedReceiptSuppressesDeliveryFromANewTrackerButAllowsChangedConfiguration() {
        val receipts = mutableSetOf<String>()
        assertTrue(tracker(receipts).deliverOnce(identity) { true })
        var notifications = 0
        assertFalse(tracker(receipts).deliverOnce(identity) { notifications++; true })
        assertTrue(tracker(receipts).deliverOnce(identity.copy(reminderDays = 1)) { notifications++; true })
        assertEquals(1, notifications)
    }

    @Test
    fun concurrentReceiversOnlyDeliverOneNotification() {
        val receipts = mutableSetOf<String>()
        val executor = Executors.newFixedThreadPool(4)
        var notifications = 0
        try {
            val results = executor.invokeAll((1..8).map {
                Callable { tracker(receipts).deliverOnce(identity) { notifications++; true } }
            }, 5, TimeUnit.SECONDS)
            assertEquals(1, results.count { it.get() })
            assertEquals(1, notifications)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun tracker(receipts: MutableSet<String>) = PaymentReminderDelivery(
        wasDelivered = { it.storageKey in receipts },
        markDelivered = { receipts.add(it.storageKey) }
    )
}
