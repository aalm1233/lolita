package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY due_date ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Long): Payment?

    @Query("SELECT * FROM payments WHERE price_id = :priceId")
    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE price_id = :priceId")
    suspend fun getPaymentsByPriceList(priceId: Long): List<Payment>

    @Query("SELECT * FROM payments WHERE is_paid = 0 ORDER BY due_date ASC")
    fun getUnpaidPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 AND reminder_set = 1 ORDER BY due_date ASC")
    fun getPendingReminderPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 AND reminder_set = 1 ORDER BY due_date ASC")
    suspend fun getPendingReminderPaymentsList(): List<Payment>

    @Query("""
        SELECT p.id AS paymentId, p.amount, p.due_date AS dueDate, p.is_paid AS isPaid,
               p.paid_date AS paidDate, pr.type AS priceType, i.name AS itemName, pr.item_id AS itemId
        FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.is_paid = 0 AND p.reminder_set = 1
        ORDER BY p.due_date ASC
    """)
    suspend fun getPendingReminderPaymentsWithItemInfoList(): List<PaymentWithItemInfo>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Query("UPDATE payments SET calendar_event_id = :calendarEventId WHERE id = :paymentId")
    suspend fun updateCalendarEventId(paymentId: Long, calendarEventId: Long?)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments ORDER BY due_date ASC")
    suspend fun getAllPaymentsList(): List<Payment>

    // Calendar queries — only include payments for OWNED items
    @Query("""
        SELECT p.id AS paymentId, p.amount, p.due_date AS dueDate, p.is_paid AS isPaid,
               p.paid_date AS paidDate, pr.type AS priceType, i.name AS itemName, pr.item_id AS itemId
        FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.due_date BETWEEN :startDate AND :endDate AND i.status = 'OWNED'
        ORDER BY p.due_date ASC
    """)
    fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long): Flow<List<PaymentWithItemInfo>>

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0.0) FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.is_paid = 0 AND p.due_date BETWEEN :monthStart AND :monthEnd AND i.status = 'OWNED'
    """)
    fun getMonthUnpaidTotal(monthStart: Long, monthEnd: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0.0) FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.is_paid = 0 AND i.status = 'OWNED'
    """)
    fun getTotalUnpaidAmount(): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0.0) FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.is_paid = 0 AND p.due_date < :now AND i.status = 'OWNED'
    """)
    fun getOverdueAmount(now: Long): Flow<Double>

    @Query("""
        SELECT COUNT(*) FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE p.is_paid = 0 AND i.status = 'OWNED'
    """)
    fun getTotalUnpaidCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        WHERE pr.item_id = :itemId AND pr.type = 'DEPOSIT_BALANCE'
        AND p.is_paid = 0
    """)
    suspend fun countUnpaidBalancePaymentsForItem(itemId: Long): Int

    @Query("""
        SELECT pr.item_id FROM payments p
        INNER JOIN prices pr ON p.price_id = pr.id
        WHERE p.id = :paymentId
    """)
    suspend fun getItemIdByPaymentId(paymentId: Long): Long?

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}
