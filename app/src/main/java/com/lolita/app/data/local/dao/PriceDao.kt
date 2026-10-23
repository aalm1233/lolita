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
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM item_reference_prices WHERE item_id=:itemId")
    fun observeReference(itemId: Long): Flow<com.lolita.app.data.local.entity.ItemReferencePrice?>
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

    @Transaction
    @Query("SELECT * FROM prices WHERE item_id=:itemId")
    fun observeAllPricesWithPaymentsByItem(itemId: Long): Flow<List<PriceWithPayments>>

    @Query("SELECT * FROM prices")
    suspend fun getAllPricesList(): List<Price>

    @Query("SELECT COALESCE(SUM(ROUND(pay.amount*100))/100.0, 0.0) FROM payments pay INNER JOIN prices p ON pay.price_id=p.id INNER JOIN order_bindings ob ON ob.price_id=p.id INNER JOIN items i ON p.item_id = i.id WHERE pay.is_paid=1")
    fun getTotalSpending(): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(CAST(ROUND(pay.amount * 100) AS INTEGER)) / 100.0, 0.0)
        FROM payments pay INNER JOIN prices p ON pay.price_id = p.id
        WHERE pay.is_paid = 1
    """)
    fun getAllRecordedSpending(): Flow<Double>

    @Query("""SELECT COUNT(*) FROM order_bindings b
        WHERE b.purchase_record_status <> 'TRACKED'
            OR EXISTS(SELECT 1 FROM payments p WHERE p.price_id=b.price_id
                AND p.is_paid=1 AND p.paid_date IS NULL)""")
    fun getIncompleteCurrentOrderCount(): Flow<Int>

    @Query("""SELECT COALESCE(AVG(p.total_price),0) FROM prices p JOIN order_bindings b ON b.price_id=p.id
        JOIN items i ON i.id=b.item_id WHERE i.status='OWNED'""")
    fun getAverageOwnedPrice(): Flow<Double>

    @Transaction
    @Query("SELECT * FROM prices WHERE item_id = :itemId AND id IN (SELECT price_id FROM order_bindings)")
    fun getPricesWithPaymentsByItem(itemId: Long): Flow<List<PriceWithPayments>>

    @Query("""
        SELECT COALESCE(SUM(p.total_price), 0.0)
        FROM prices p INNER JOIN items i ON p.item_id = i.id
        WHERE i.coordinate_id = :coordinateId AND p.id IN (SELECT price_id FROM order_bindings)
    """)
    fun getTotalPriceByCoordinate(coordinateId: Long): Flow<Double>

    @Transaction
    @Query("""
        SELECT p.* FROM prices p INNER JOIN items i ON p.item_id = i.id
        WHERE i.coordinate_id = :coordinateId AND p.id IN (SELECT price_id FROM order_bindings)
    """)
    fun getPricesWithPaymentsByCoordinate(coordinateId: Long): Flow<List<PriceWithPayments>>

    @Query("SELECT COALESCE(SUM(total_price), 0.0) FROM prices WHERE item_id IN (:itemIds) AND id IN (SELECT price_id FROM order_bindings)")
    fun getTotalPriceByItemIds(itemIds: List<Long>): Flow<Double>

    @Query("""
        SELECT p.item_id, p.total_price AS totalPrice FROM prices p
        JOIN order_bindings b ON b.price_id=p.id JOIN items i ON i.id=p.item_id
        WHERE i.status!='WISHED'
        UNION ALL
        SELECT r.item_id, r.amount AS totalPrice FROM item_reference_prices r
        JOIN items i ON i.id=r.item_id WHERE i.status='WISHED' AND i.included_in_parent_price=0
    """)
    fun getItemPriceSums(): Flow<List<ItemPriceSum>>

    @Query("""
        SELECT b.name AS name, COALESCE(SUM((SELECT COALESCE(SUM(ROUND(a.amount*100))/100.0,0) FROM payments a WHERE a.price_id=p.id AND a.is_paid=1)), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        INNER JOIN brands b ON i.brand_id = b.id
        WHERE p.id IN (SELECT price_id FROM order_bindings)
        GROUP BY b.name
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByBrand(): Flow<List<BrandSpending>>

    @Query("""
        SELECT c.name AS name, COALESCE(SUM((SELECT COALESCE(SUM(ROUND(a.amount*100))/100.0,0) FROM payments a WHERE a.price_id=p.id AND a.is_paid=1)), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        INNER JOIN categories c ON i.category_id = c.id
        WHERE p.id IN (SELECT price_id FROM order_bindings)
        GROUP BY c.name
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByCategory(): Flow<List<CategorySpending>>

    @Query("""
        SELECT COALESCE(i.style, '') AS style, COALESCE(SUM((SELECT COALESCE(SUM(ROUND(a.amount*100))/100.0,0) FROM payments a WHERE a.price_id=p.id AND a.is_paid=1)), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE p.id IN (SELECT price_id FROM order_bindings)
        GROUP BY i.style
        ORDER BY totalSpending DESC
    """)
    fun getSpendingByStyle(): Flow<List<StyleSpending>>

    @Query("""
        SELECT strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch', 'localtime') AS yearMonth,
               COALESCE(SUM(ROUND(pay.amount*100))/100.0, 0.0) AS totalSpending
        FROM payments pay
        INNER JOIN prices pr ON pay.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE pr.id IN (SELECT price_id FROM order_bindings)
          AND pay.is_paid = 1
          AND pay.paid_date IS NOT NULL
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlySpending(): Flow<List<MonthlySpending>>

    @Query("""
        SELECT COALESCE(SUM(ROUND(pay.amount * 100)) / 100.0, 0.0)
        FROM payments pay
        INNER JOIN prices pr ON pay.price_id = pr.id
        INNER JOIN items i ON pr.item_id = i.id
        WHERE pr.id IN (SELECT price_id FROM order_bindings)
          AND pay.is_paid = 1 AND pay.paid_date IS NULL
    """)
    fun getUndatedSpending(): Flow<Double>

    @Query("""
        SELECT i.id AS itemId, i.name AS itemName, i.image_urls AS imageUrls,
               COALESCE(SUM((SELECT COALESCE(SUM(ROUND(a.amount*100))/100.0,0) FROM payments a WHERE a.price_id=p.id AND a.is_paid=1)), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE p.id IN (SELECT price_id FROM order_bindings)
        GROUP BY i.id
        ORDER BY totalSpending DESC
        LIMIT 1
    """)
    fun getMostExpensiveItem(): Flow<ItemWithSpending?>

    @Query("""
        SELECT COALESCE(i.season, '') AS style, COALESCE(SUM((SELECT COALESCE(SUM(ROUND(a.amount*100))/100.0,0) FROM payments a WHERE a.price_id=p.id AND a.is_paid=1)), 0.0) AS totalSpending
        FROM prices p
        INNER JOIN items i ON p.item_id = i.id
        WHERE p.id IN (SELECT price_id FROM order_bindings)
        GROUP BY i.season
    """)
    fun getSpendingBySeasonRaw(): Flow<List<StyleSpending>>

    @Query("""SELECT COALESCE(SUM(CAST(ROUND(r.amount*100) AS INTEGER)),0)/100.0
        FROM item_reference_prices r JOIN items i ON i.id=r.item_id WHERE i.status='WISHED' AND i.included_in_parent_price=0""")
    fun getWishlistTotalBudget(): Flow<Double>

    @Query("""SELECT i.priority AS priority, COUNT(DISTINCT i.id) AS itemCount,
        COALESCE(SUM(CAST(ROUND(r.amount*100) AS INTEGER)),0)/100.0 AS totalBudget
        FROM items i LEFT JOIN item_reference_prices r ON r.item_id=i.id AND i.included_in_parent_price=0
        WHERE i.status='WISHED' GROUP BY i.priority""")
    fun getWishlistByPriorityStats(): Flow<List<PriorityStats>>

    @Query("SELECT COALESCE(SUM(CAST(ROUND(amount*100) AS INTEGER)),0)/100.0 FROM order_refunds WHERE voided_at IS NULL")
    fun getAllRefunds(): Flow<Double>

    @Query("""
        SELECT DISTINCT i.* FROM items i
        INNER JOIN prices pr ON pr.item_id = i.id
        INNER JOIN payments pay ON pay.price_id = pr.id
        WHERE pr.id IN (SELECT price_id FROM order_bindings)
          AND pay.is_paid = 1
          AND strftime('%Y-%m', pay.paid_date / 1000, 'unixepoch', 'localtime') = :yearMonth
        ORDER BY i.updated_at DESC
    """)
    fun getItemsByPurchaseMonth(yearMonth: String): Flow<List<Item>>

    @Query("DELETE FROM prices")
    suspend fun deleteAllPrices()

    @Query(STAGE_BALANCES + """
        SELECT pr.id AS priceId, pr.total_price AS totalPrice,
               MIN(pay.paid_date) AS firstPaidDate,
               pr.type AS priceType, i.name AS itemName, i.id AS itemId,
               CASE WHEN (SELECT purchase_record_status FROM order_bindings WHERE price_id=pr.id)='TRACKED' AND ROUND(pr.total_price*100)>COALESCE((SELECT SUM(ROUND(p.amount*100)) FROM payments p WHERE p.price_id=pr.id AND p.is_paid=1),0) THEN 1 ELSE 0 END AS unpaidCount,
               (SELECT COUNT(*) FROM balances b WHERE b.price_id=pr.id AND b.remaining>0 AND b.due_date<:now) AS overdueCount
        FROM prices pr
        INNER JOIN items i ON pr.item_id = i.id
        INNER JOIN payments pay ON pay.price_id = pr.id
        WHERE pay.is_paid = 1
          AND pay.paid_date BETWEEN :startDate AND :endDate
          AND pr.id IN (SELECT price_id FROM order_bindings)
        GROUP BY pr.id
        ORDER BY firstPaidDate ASC
    """)
    fun getPricesWithStatusByDateRange(startDate: Long, endDate: Long, now: Long): Flow<List<PriceWithStatus>>


}

data class ItemPriceSum(
    @androidx.room.ColumnInfo(name = "item_id") val itemId: Long,
    val totalPrice: Double
)

data class PriceWithStatus(
    val priceId: Long,
    val totalPrice: Double,
    val firstPaidDate: Long,
    val priceType: com.lolita.app.data.local.entity.PriceType,
    val itemName: String,
    val itemId: Long,
    val unpaidCount: Int,
    val overdueCount: Int
)

data class PriceWithPayments(
    @Embedded val price: Price,
    @Relation(
        parentColumn = "id",
        entityColumn = "price_id"
    )
    val payments: List<Payment>,
    @Relation(parentColumn = "id", entityColumn = "price_id")
    val binding: com.lolita.app.data.local.entity.OrderBinding? = null,
    @Relation(parentColumn = "id", entityColumn = "price_id")
    val cancellation: com.lolita.app.data.local.entity.OrderCancellation? = null,
    @Relation(parentColumn = "id", entityColumn = "price_id")
    val refunds: List<com.lolita.app.data.local.entity.OrderRefund> = emptyList()
)

