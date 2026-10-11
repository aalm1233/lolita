package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Payment
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCleanupTest {
    private fun pointer(id: Long, eventId: Long? = null) =
        Payment(id = id, priceId = 17, amount = 100.0, dueDate = 123, calendarEventId = eventId)

    @Test
    fun repeatedFailedCleanupDoesNotExpandTheJournal() {
        val current = listOf(pointer(7), pointer(-7, 301))
        var journal = current
        repeat(100) { journal = ReminderCleanup.deduplicate(current + journal) }
        assertEquals(current, journal)
    }

    @Test
    fun changedCalendarPointersForTheSameStageAreAllRetained() {
        val pointers = listOf(pointer(-7, 301), pointer(-7, 302), pointer(-7), pointer(-7, 301))
        assertEquals(pointers.take(3), ReminderCleanup.deduplicate(pointers))
    }

    @Test
    fun legacyPaymentsAndStageAlarmsHaveDifferentIdentitiesEvenWithTheSameEvent() {
        val pointers = listOf(pointer(7, 301), pointer(-7, 301), pointer(-8, 301))
        assertEquals(pointers, ReminderCleanup.deduplicate(pointers + pointers))
    }

    @Test
    fun mutableAmountsAndDatesDoNotCreateAnotherCleanupPointer() {
        val first = pointer(-7, 301)
        val changed = first.copy(amount = 10.0, dueDate = 456)
        assertEquals(listOf(first), ReminderCleanup.deduplicate(listOf(first, changed)))
        assertEquals(100.0, first.amount, 0.0)
    }
}
