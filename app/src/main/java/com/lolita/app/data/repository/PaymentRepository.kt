package com.lolita.app.data.repository

import android.content.Context
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import kotlinx.coroutines.flow.Flow

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val context: Context,
    private val itemDao: ItemDao? = null
) {
    private val reminderScheduler = PaymentReminderScheduler(context)

    fun getAllPayments(): Flow<List<Payment>> = paymentDao.getAllPayments()

    suspend fun getPaymentById(id: Long): Payment? = paymentDao.getPaymentById(id)

    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>> =
        paymentDao.getPaymentsByPrice(priceId)

    suspend fun getPaymentsByPriceList(priceId: Long): List<Payment> =
        paymentDao.getPaymentsByPriceList(priceId)

    fun getUnpaidPayments(): Flow<List<Payment>> = paymentDao.getUnpaidPayments()

    fun getPendingReminderPayments(): Flow<List<Payment>> =
        paymentDao.getPendingReminderPayments()

    suspend fun insertPayment(payment: Payment, itemName: String = ""): Long {
        val id = paymentDao.insertPayment(payment)
        // Schedule reminder if enabled
        if (payment.reminderSet && !payment.isPaid) {
            try {
                val insertedPayment = payment.copy(id = id)
                reminderScheduler.scheduleReminder(insertedPayment, itemName)
            } catch (_: SecurityException) {
                // Exact alarm permission not granted, reminder skipped
            }
        }
        // Add calendar event only if reminder is set and not paid
        if (payment.reminderSet && !payment.isPaid) {
            val calendarEventId = try {
                CalendarEventHelper.insertEvent(
                    context = context,
                    title = "付款提醒: $itemName",
                    description = "金额: ¥${payment.amount}",
                    startTimeMillis = payment.dueDate
                )
            } catch (_: Exception) {
                null
            }
            if (calendarEventId != null) {
                paymentDao.updateCalendarEventId(id, calendarEventId)
            }
        }
        return id
    }

    suspend fun updatePayment(payment: Payment, itemName: String = "") {
        // Handle calendar event: always delete old
        val oldPayment = paymentDao.getPaymentById(payment.id)
        var updatedPayment = payment
        try {
            oldPayment?.calendarEventId?.let { CalendarEventHelper.deleteEvent(context, it) }
            // Only create new calendar event if not paid and reminder is set
            if (!payment.isPaid && payment.reminderSet) {
                val newEventId = CalendarEventHelper.insertEvent(
                    context = context,
                    title = "付款提醒: $itemName",
                    description = "金额: ¥${payment.amount}",
                    startTimeMillis = payment.dueDate
                )
                updatedPayment = payment.copy(calendarEventId = newEventId)
            } else {
                updatedPayment = payment.copy(calendarEventId = null)
            }
        } catch (_: Exception) {
            // Calendar operation failed, proceed without calendar event
        }

        paymentDao.updatePayment(updatedPayment)

        // Auto-toggle PENDING_BALANCE status based on unpaid balance payments
        checkAndUpdatePendingBalanceStatus(payment.id)

        try {
            // Update reminder based on payment status
            if (payment.isPaid) {
                reminderScheduler.cancelReminder(payment.id)
            } else if (payment.reminderSet) {
                reminderScheduler.scheduleReminder(payment, itemName)
            } else {
                reminderScheduler.cancelReminder(payment.id)
            }
        } catch (_: SecurityException) {
            // Exact alarm permission not granted, reminder skipped
        }
    }

    suspend fun deletePayment(payment: Payment) {
        // Cancel reminder before deleting
        reminderScheduler.cancelReminder(payment.id)
        // Delete calendar event if exists
        payment.calendarEventId?.let {
            try {
                CalendarEventHelper.deleteEvent(context, it)
            } catch (_: Exception) { }
        }
        paymentDao.deletePayment(payment)
    }

    /**
     * Schedule reminder for an existing payment
     * Useful when reminder settings are changed after payment is created
     */
    fun scheduleReminderForPayment(payment: Payment, itemName: String = "") {
        if (payment.reminderSet && !payment.isPaid) {
            reminderScheduler.scheduleReminder(payment, itemName)
        }
    }

    /**
     * Cancel reminder for a payment
     */
    fun cancelReminderForPayment(paymentId: Long) {
        reminderScheduler.cancelReminder(paymentId)
    }

    /**
     * Get the scheduler instance (for testing purposes)
     */
    fun getScheduler(): PaymentReminderScheduler = reminderScheduler

    // Calendar queries
    fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long): Flow<List<PaymentWithItemInfo>> =
        paymentDao.getPaymentsWithItemInfoByDateRange(startDate, endDate)

    fun getMonthUnpaidTotal(monthStart: Long, monthEnd: Long): Flow<Double> =
        paymentDao.getMonthUnpaidTotal(monthStart, monthEnd)

    fun getTotalUnpaidAmount(): Flow<Double> =
        paymentDao.getTotalUnpaidAmount()

    fun getOverdueAmount(now: Long): Flow<Double> =
        paymentDao.getOverdueAmount(now)

    private suspend fun checkAndUpdatePendingBalanceStatus(paymentId: Long) {
        val dao = itemDao ?: return
        val itemId = paymentDao.getItemIdByPaymentId(paymentId) ?: return
        val item = dao.getItemById(itemId) ?: return
        if (item.status == ItemStatus.OWNED || item.status == ItemStatus.PENDING_BALANCE) {
            val unpaidCount = paymentDao.countUnpaidBalancePaymentsForItem(itemId)
            val newStatus = if (unpaidCount > 0) ItemStatus.PENDING_BALANCE else ItemStatus.OWNED
            if (item.status != newStatus) {
                dao.updateItemStatus(itemId, newStatus.name)
            }
        }
    }
}
