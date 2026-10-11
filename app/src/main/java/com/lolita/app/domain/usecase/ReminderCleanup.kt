package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Payment

/** Cleanup journals retain pointers, not financial history; keep each old calendar pointer once. */
object ReminderCleanup {
    fun deduplicate(payments: List<Payment>): List<Payment> =
        payments.distinctBy { it.id to it.calendarEventId }
}
