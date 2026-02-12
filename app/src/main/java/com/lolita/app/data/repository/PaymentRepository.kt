package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.entity.Payment
import kotlinx.coroutines.flow.Flow

class PaymentRepository(
    private val paymentDao: PaymentDao
) {
    fun getAllPayments(): Flow<List<Payment>> = paymentDao.getAllPayments()

    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>> =
        paymentDao.getPaymentsByPrice(priceId)

    fun getUnpaidPayments(): Flow<List<Payment>> = paymentDao.getUnpaidPayments()

    fun getPendingReminderPayments(): Flow<List<Payment>> =
        paymentDao.getPendingReminderPayments()

    suspend fun insertPayment(payment: Payment): Long = paymentDao.insertPayment(payment)

    suspend fun updatePayment(payment: Payment) = paymentDao.updatePayment(payment)

    suspend fun deletePayment(payment: Payment) = paymentDao.deletePayment(payment)
}
