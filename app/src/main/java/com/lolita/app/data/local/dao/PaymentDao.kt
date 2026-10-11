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

internal const val STAGE_BALANCES = """WITH raw AS (
 SELECT st.*,pr.total_price,pr.type,i.name AS item_name,i.id AS item_id,
 MAX(ROUND(st.amount*100)-COALESCE((SELECT SUM(ROUND(a.amount*100)) FROM payments a WHERE a.stage_id=st.id AND a.is_paid=1),0),0) AS stage_remaining,
 COALESCE((SELECT SUM(ROUND(a.amount*100)) FROM payments a WHERE a.price_id=pr.id AND a.stage_id IS NULL AND a.is_paid=1),0) AS unassigned,
 MAX(ROUND(pr.total_price*100)-COALESCE((SELECT SUM(ROUND(a.amount*100)) FROM payments a WHERE a.price_id=pr.id AND a.is_paid=1),0),0) AS order_remaining
 FROM payment_stages st JOIN prices pr ON pr.id=st.price_id JOIN order_bindings ob ON ob.price_id=pr.id JOIN items i ON i.id=ob.item_id
 WHERE ob.purchase_record_status='TRACKED'
), balances AS (
 SELECT r.*,MIN(
 MAX(r.order_remaining-MAX(COALESCE((SELECT SUM(p.stage_remaining) FROM raw p WHERE p.price_id=r.price_id AND p.id<r.id),0)-r.unassigned,0),0),
 MAX(r.stage_remaining-MAX(r.unassigned-COALESCE((SELECT SUM(p.stage_remaining) FROM raw p WHERE p.price_id=r.price_id AND p.id<r.id),0),0),0)
 )/100.0 AS remaining FROM raw r
)
"""

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
               p.paid_date AS paidDate, pr.id AS priceId, pr.type AS priceType, i.name AS itemName, pr.item_id AS itemId
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

    @Query(STAGE_BALANCES + """SELECT -id AS paymentId,remaining AS amount,due_date AS dueDate,0 AS isPaid,NULL AS paidDate,
    price_id AS priceId,type AS priceType,item_name AS itemName,item_id AS itemId FROM balances
    WHERE due_date BETWEEN :startDate AND :endDate AND remaining>0
    UNION ALL SELECT p.id,p.amount,p.paid_date,1,p.paid_date,pr.id,pr.type,i.name,i.id
    FROM payments p JOIN prices pr ON pr.id=p.price_id JOIN order_bindings ob ON ob.price_id=pr.id JOIN items i ON i.id=ob.item_id
    WHERE p.is_paid=1 AND p.paid_date BETWEEN :startDate AND :endDate ORDER BY dueDate""")
    fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long): Flow<List<PaymentWithItemInfo>>

    @Query(STAGE_BALANCES + "SELECT COALESCE(SUM(remaining),0) FROM balances WHERE due_date BETWEEN :monthStart AND :monthEnd")
    fun getMonthUnpaidTotal(monthStart: Long, monthEnd: Long): Flow<Double>

    @Query("""SELECT COALESCE(SUM(MAX(ROUND(pr.total_price*100)-COALESCE((SELECT SUM(ROUND(a.amount*100)) FROM payments a WHERE a.price_id=pr.id AND a.is_paid=1),0),0)),0)/100.0 FROM prices pr JOIN order_bindings ob ON ob.price_id=pr.id WHERE ob.purchase_record_status='TRACKED'""")
    fun getTotalUnpaidAmount(): Flow<Double>

    @Query(STAGE_BALANCES + "SELECT COALESCE(SUM(remaining),0) FROM balances WHERE due_date<:now")
    fun getOverdueAmount(now: Long): Flow<Double>

    @Query("""SELECT COUNT(*) FROM prices pr JOIN order_bindings ob ON ob.price_id=pr.id WHERE ob.purchase_record_status='TRACKED' AND ROUND(pr.total_price*100)>COALESCE((SELECT SUM(ROUND(a.amount*100)) FROM payments a WHERE a.price_id=pr.id AND a.is_paid=1),0)""")
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
