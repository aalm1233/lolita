package com.lolita.app.domain.usecase

/** Identifies the promise to remind once, independently of mutable names and balances. */
data class PaymentReminderIdentity(
    val paymentId: Long,
    val priceId: Long,
    val dueDate: Long,
    val reminderDays: Int
) {
    val storageKey: String get() = "v1:$paymentId:$priceId:$dueDate:$reminderDays"
    val reminderTime: Long get() = Math.subtractExact(dueDate, reminderDays.toLong() * DAY_IN_MILLIS)

    companion object {
        private const val DAY_IN_MILLIS = 86_400_000L
    }
}

object PaymentReminderPolicy {
    private const val CATCH_UP_DELAY = 60_000L

    fun calendarReminderMinutes(reminderDays: Int?): Int =
        ((reminderDays ?: 1).toLong() * 24 * 60).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    fun triggerTime(identity: PaymentReminderIdentity, now: Long, delivered: Boolean): Long? = when {
        delivered -> null
        identity.reminderTime <= now -> Math.addExact(now, CATCH_UP_DELAY)
        else -> identity.reminderTime
    }

    /** Older installed alarms lack the identity key but still include their original due date. */
    fun canDeliver(
        identity: PaymentReminderIdentity,
        scheduledIdentity: String?,
        scheduledDueDate: Long?,
        now: Long,
        delivered: Boolean
    ): Boolean = !delivered && scheduledDueDate == identity.dueDate &&
        (scheduledIdentity == null || scheduledIdentity == identity.storageKey) &&
        identity.reminderTime <= now
}

/** Mark only successful notifications; disabled notifications remain eligible for a later retry. */
class PaymentReminderDelivery(
    private val wasDelivered: (PaymentReminderIdentity) -> Boolean,
    private val markDelivered: (PaymentReminderIdentity) -> Unit
) {
    fun deliverOnce(identity: PaymentReminderIdentity, showNotification: () -> Boolean): Boolean =
        synchronized(deliveryLock) {
            if (wasDelivered(identity) || !showNotification()) return@synchronized false
            markDelivered(identity)
            true
        }

    companion object {
        private val deliveryLock = Any()
    }
}
