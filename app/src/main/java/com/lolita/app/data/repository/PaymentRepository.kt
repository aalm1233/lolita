package com.lolita.app.data.repository

import android.content.Context
import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.notification.PaymentReminderScheduler
import kotlinx.coroutines.flow.Flow

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val context: Context
) {
    private val reminderScheduler = PaymentReminderScheduler(context)

    fun getAllPayments(): Flow<List<Payment>> = paymentDao.getAllPayments()

    suspend fun getPaymentById(id: Long): Payment? = paymentDao.getPaymentById(id)

    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>> =
        paymentDao.getPaymentsByPrice(priceId)

    fun getUnpaidPayments(): Flow<List<Payment>> = paymentDao.getUnpaidPayments()

    fun getPendingReminderPayments(): Flow<List<Payment>> =
        paymentDao.getPendingReminderPayments()

    suspend fun insertPayment(payment: Payment, itemName: String = ""): Long {
        val id = paymentDao.insertPayment(payment)
        // Schedule reminder if enabled
        if (payment.reminderSet && !payment.isPaid) {
            val insertedPayment = payment.copy(id = id)
            reminderScheduler.scheduleReminder(insertedPayment, itemName)
        }
        return id
    }

    suspend fun updatePayment(payment: Payment, itemName: String = "") {
        paymentDao.updatePayment(payment)

        // Update reminder based on payment status
        if (payment.isPaid) {
            // Cancel reminder when payment is marked as paid
            reminderScheduler.cancelReminder(payment.id)
        } else if (payment.reminderSet) {
            // Schedule or update reminder
            reminderScheduler.scheduleReminder(payment, itemName)
        } else {
            // Cancel reminder if reminder is disabled
            reminderScheduler.cancelReminder(payment.id)
        }
    }

    suspend fun deletePayment(payment: Payment) {
        // Cancel reminder before deleting
        reminderScheduler.cancelReminder(payment.id)
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
}
