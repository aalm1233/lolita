package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AfterSaleDao {
    @Query("SELECT * FROM item_reference_prices WHERE item_id=:itemId") fun reference(itemId: Long): Flow<ItemReferencePrice?>
    @Query("SELECT * FROM item_reference_prices WHERE item_id=:itemId") suspend fun getReference(itemId: Long): ItemReferencePrice?
    @Query("SELECT * FROM item_reference_prices") suspend fun references(): List<ItemReferencePrice>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReference(value: ItemReferencePrice)
    @Update suspend fun updateReference(value: ItemReferencePrice)
    @Query("DELETE FROM item_reference_prices WHERE item_id=:itemId") suspend fun deleteReference(itemId: Long)
    @Query("SELECT * FROM order_refunds WHERE price_id=:priceId ORDER BY received_at DESC,id DESC") fun refunds(priceId: Long): Flow<List<OrderRefund>>
    @Query("SELECT * FROM order_refunds WHERE price_id=:priceId ORDER BY id") suspend fun getRefunds(priceId: Long): List<OrderRefund>
    @Query("SELECT * FROM order_refunds WHERE operation_id=:operationId") suspend fun byOperation(operationId: String): OrderRefund?
    @Query("SELECT * FROM order_refunds WHERE id=:id") suspend fun getRefund(id: Long): OrderRefund?
    @Query("SELECT * FROM order_refunds") suspend fun allRefunds(): List<OrderRefund>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRefund(value: OrderRefund): Long
    @Update suspend fun updateRefund(value: OrderRefund)
    @Query("SELECT * FROM order_cancellations WHERE price_id=:priceId") fun cancellation(priceId: Long): Flow<OrderCancellation?>
    @Query("SELECT * FROM order_cancellations WHERE price_id=:priceId") suspend fun getCancellation(priceId: Long): OrderCancellation?
    @Query("SELECT * FROM order_cancellations") suspend fun cancellations(): List<OrderCancellation>
    @Query("SELECT * FROM order_cancellations") fun observeCancellations(): Flow<List<OrderCancellation>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCancellation(value: OrderCancellation)
    @Update suspend fun updateCancellation(value: OrderCancellation)
    @Query("SELECT COALESCE(SUM(CAST(ROUND(amount*100) AS INTEGER)),0)/100.0 FROM order_refunds WHERE voided_at IS NULL") fun totalRefunds(): Flow<Double>
    @Query("DELETE FROM order_refunds") suspend fun clearRefunds()
    @Query("DELETE FROM order_cancellations") suspend fun clearCancellations()
    @Query("DELETE FROM item_reference_prices") suspend fun clearReferences()
}
