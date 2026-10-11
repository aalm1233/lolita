package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM order_bindings WHERE item_id=:itemId") suspend fun getBinding(itemId: Long): OrderBinding?
    @Query("SELECT * FROM order_bindings") suspend fun getAllBindings(): List<OrderBinding>
    @Query("SELECT * FROM order_bindings") fun observeBindings(): Flow<List<OrderBinding>>
    @Query("SELECT * FROM payment_stages") suspend fun getAllStages(): List<PaymentStage>
    @Query("SELECT * FROM payment_stages WHERE price_id=:priceId ORDER BY id") suspend fun getStages(priceId: Long): List<PaymentStage>
    @Query("SELECT * FROM payment_stages WHERE price_id=:priceId ORDER BY id") fun observeStages(priceId: Long): Flow<List<PaymentStage>>
    @Query("SELECT * FROM payment_stages WHERE id=:id") suspend fun getStage(id: Long): PaymentStage?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBinding(binding: OrderBinding)
    @Query("DELETE FROM order_bindings WHERE item_id=:itemId") suspend fun deleteBinding(itemId: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStage(stage: PaymentStage): Long
    @Update suspend fun updateStage(stage: PaymentStage)
    @Query("UPDATE payment_stages SET calendar_event_id=:eventId WHERE id=:stageId") suspend fun updateStageCalendarEventId(stageId: Long, eventId: Long?)
    @Query("DELETE FROM payment_stages WHERE id=:id") suspend fun deleteStage(id: Long)
    @Query("DELETE FROM payment_stages") suspend fun clearStages()
    @Query("DELETE FROM order_bindings") suspend fun clearBindings()

    @Query("""UPDATE items SET status = (
        SELECT CASE WHEN COALESCE((SELECT SUM(ROUND(p.amount*100)) FROM payments p WHERE p.price_id=pr.id AND p.is_paid=1),0) >= ROUND(pr.total_price*100)
        THEN 'OWNED' WHEN pr.type='DEPOSIT_BALANCE' THEN 'PENDING_BALANCE' ELSE 'PENDING_PAYMENT' END
        FROM order_bindings b JOIN prices pr ON pr.id=b.price_id WHERE b.item_id=items.id)
        WHERE id IN (SELECT item_id FROM order_bindings)""")
    suspend fun refreshIndependentStatuses()
    @Query("""UPDATE items SET status=(SELECT parent.status FROM items parent WHERE parent.id=items.parent_item_id)
        WHERE included_in_parent_price=1 AND parent_item_id IS NOT NULL""")
    suspend fun refreshIncludedStatuses()
    @Transaction suspend fun refreshStatuses() { refreshIndependentStatuses(); refreshIncludedStatuses() }
}
