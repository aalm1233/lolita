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
import com.lolita.app.data.local.entity.BrandSpending
import com.lolita.app.data.local.entity.CategorySpending
import com.lolita.app.data.local.entity.StyleSpending
import com.lolita.app.data.local.entity.MonthlySpending
import com.lolita.app.data.local.entity.ItemWithSpending
import com.lolita.app.data.local.entity.PriorityStats
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    fun getPricesByItem(itemId: Long): Flow<List<Price>>

    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    suspend fun getPricesByItemList(itemId: Long): List<Price>

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

    @Query("SELECT COALESCE(SUM(p.total_price), 0.0) FROM prices p INNER JOIN items i ON p.item_id = i.id WHERE i.status = 'OWNED'")
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

    @Query("""
        SELECT b.name AS name, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        INNER JOIN brands b ON i.brand_id = b.id
        WHERE i.status = 'OWNED'
        GROUP BY b.name
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByBrand(): Flow<List<BrandSpending>>

    @Query("""
        SELECT c.name AS name, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        INNER JOIN categories c ON i.category_id = c.id
        WHERE i.status = 'OWNED'
        GROUP BY c.name
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByCategory(): Flow<List<CategorySpending>>

    @Query("""
        SELECT i.style AS style, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE i.status = 'OWNED' AND i.style IS NOT NULL AND i.style != ''
        GROUP BY i.style
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByStyle(): Flow<List<StyleSpending>>

    @Query("""
        SELECT strftime('%Y-%m', p.purchase_date / 1000, 'unixepoch') AS yearMonth,
               COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE i.status = 'OWNED' AND p.purchase_date IS NOT NULL
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlySpending(): Flow<List<MonthlySpending>>

    @Query("""
        SELECT i.id AS itemId, i.name AS itemName, i.image_url AS imageUrl,
               COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE i.status = 'OWNED'
        GROUP BY i.id
        ORDER BY totalSpending DESC
        LIMIT 1
    """)
    fun getMostExpensiveItem(): Flow<ItemWithSpending?>

    @Query("""
        SELECT i.season AS style, COALESCE(SUM(p.total_price), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE i.status = 'OWNED' AND i.season IS NOT NULL AND i.season != ''
        GROUP BY i.season
    """)
    fun getSpendingBySeasonRaw(): Flow<List<StyleSpending>>

    @Query("""
        SELECT COALESCE(SUM(p.total_price), 0.0)
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE i.status = 'WISHED'
    """)
    fun getWishlistTotalBudget(): Flow<Double>

    @Query("""
        SELECT i.priority AS priority, COUNT(i.id) AS itemCount,
               COALESCE(SUM(p.total_price), 0.0) AS totalBudget
        FROM items i
        LEFT JOIN prices p ON p.item_id = i.id
        WHERE i.status = 'WISHED'
        GROUP BY i.priority
    """)
    fun getWishlistByPriorityStats(): Flow<List<PriorityStats>>
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
