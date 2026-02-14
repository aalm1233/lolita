package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    fun getPricesByItem(itemId: Long): Flow<List<Price>>

    @Query("SELECT * FROM prices WHERE id = :id")
    suspend fun getPriceById(id: Long): Price?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrice(price: Price): Long

    @Update
    suspend fun updatePrice(price: Price)

    @Delete
    suspend fun deletePrice(price: Price)

    @Transaction
    @Query("SELECT * FROM prices WHERE id = :id")
    fun getPriceWithPayments(id: Long): Flow<PriceWithPayments?>

    @Query("SELECT * FROM prices")
    suspend fun getAllPricesList(): List<Price>

    @Query("SELECT COALESCE(SUM(total_price), 0.0) FROM prices")
    fun getTotalSpending(): Flow<Double>

    @Transaction
    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    fun getPricesWithPaymentsByItem(itemId: Long): Flow<List<PriceWithPayments>>

    @Query("""
        SELECT COALESCE(SUM(p.total_price), 0.0)
        FROM prices p INNER JOIN items i ON p.item_id = i.id
        WHERE i.coordinate_id = :coordinateId
    """)
    fun getTotalPriceByCoordinate(coordinateId: Long): Flow<Double>

    @Transaction
    @Query("""
        SELECT p.* FROM prices p INNER JOIN items i ON p.item_id = i.id
        WHERE i.coordinate_id = :coordinateId
    """)
    fun getPricesWithPaymentsByCoordinate(coordinateId: Long): Flow<List<PriceWithPayments>>

    @Query("SELECT COALESCE(SUM(total_price), 0.0) FROM prices WHERE item_id IN (:itemIds)")
    fun getTotalPriceByItemIds(itemIds: List<Long>): Flow<Double>

    @Query("SELECT item_id, COALESCE(SUM(total_price), 0.0) as totalPrice FROM prices GROUP BY item_id")
    fun getItemPriceSums(): Flow<List<ItemPriceSum>>
}

data class ItemPriceSum(
    @androidx.room.ColumnInfo(name = "item_id") val itemId: Long,
    val totalPrice: Double
)

data class PriceWithPayments(
    @Embedded val price: Price,
    @Relation(
        parentColumn = "id",
        entityColumn = "price_id"
    )
    val payments: List<Payment>
)
