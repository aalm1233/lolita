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
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.PaymentStage

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val context: Context,
    private val itemDao: ItemDao? = null,
    private val database: LolitaDatabase? = null
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
        val db = database ?: return paymentDao.insertPayment(payment)
        return db.withTransaction {
            val stageId = db.orderDao().insertStage(PaymentStage(priceId = payment.priceId,
                name = "Imported payment", amount = payment.amount, dueDate = payment.dueDate,
                reminderSet = payment.reminderSet, customReminderDays = payment.customReminderDays))
            val id = paymentDao.insertPayment(payment.copy(stageId = stageId, calendarEventId = null))
            db.orderDao().refreshStatuses()
            id
        }
    }

    suspend fun updatePayment(payment: Payment, itemName: String = "") {
        val db = database ?: error("Database required")
        val old = paymentDao.getPaymentById(payment.id) ?: error("Payment missing")
        if (!old.isPaid && payment.isPaid) {
            // Compatibility for payment calendar actions: record actual without destroying legacy plans.
            OrderRepository(db, context).recordPayment(payment.priceId, payment.amount,
                payment.paidDate ?: System.currentTimeMillis(), old.stageId)
        } else OrderRepository(db, context).editPayment(payment)
    }

    suspend fun deletePayment(payment: Payment) {
        // Only used during explicit item deletion; external cleanup happens after commit.
        paymentDao.deletePayment(payment)
    }

    /**
     * Schedule reminder for an existing payment
     * Useful when reminder settings are changed after payment is created
     */
    fun scheduleReminderForPayment(payment: Payment, itemName: String = "") {
        if (payment.reminderSet && !payment.isPaid) {
            try {
                reminderScheduler.scheduleReminder(payment, itemName)
            } catch (_: SecurityException) {}
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

    fun getTotalUnpaidCount(): Flow<Int> =
        paymentDao.getTotalUnpaidCount()

}
