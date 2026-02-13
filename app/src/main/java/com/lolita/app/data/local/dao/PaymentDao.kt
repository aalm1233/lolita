package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY due_date ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Long): Payment?

    @Query("SELECT * FROM payments WHERE price_id = :priceId")
    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 ORDER BY due_date ASC")
    fun getUnpaidPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 AND reminder_set = 1 ORDER BY due_date ASC")
    fun getPendingReminderPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments ORDER BY due_date ASC")
    suspend fun getAllPaymentsList(): List<Payment>
}
