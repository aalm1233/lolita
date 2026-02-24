package com.lolita.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.BrandItemCount
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updated_at DESC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items WHERE status = :status ORDER BY updated_at DESC")
    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchItemsByName(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status = 'WISHED' ORDER BY " +
            "CASE priority " +
            "WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 " +
            "WHEN 'LOW' THEN 3 " +
            "END ASC, updated_at DESC")
    fun getWishlistByPriority(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails?>

    @Query("SELECT * FROM items ORDER BY updated_at DESC")
    suspend fun getAllItemsList(): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE brand_id = :brandId")
    suspend fun countItemsByBrand(brandId: Long): Int

    @Query("SELECT COUNT(*) FROM items WHERE category_id = :categoryId")
    suspend fun countItemsByCategory(categoryId: Long): Int

    @Query("UPDATE items SET style = :newName WHERE style = :oldName")
    suspend fun updateItemsStyle(oldName: String, newName: String)

    @Query("SELECT * FROM items WHERE season = :name OR season LIKE :name || ',%' OR season LIKE '%,' || :name || ',%' OR season LIKE '%,' || :name")
    suspend fun getItemsWithSeason(name: String): List<Item>

    @Update
    suspend fun updateItems(items: List<Item>)

    @Query("UPDATE items SET style = NULL WHERE style = :name")
    suspend fun clearItemsStyle(name: String)

    @Query("""
        SELECT b.name AS brandName, COUNT(i.id) AS itemCount
        FROM items i
        INNER JOIN brands b ON i.brand_id = b.id
        WHERE i.status = 'OWNED'
        GROUP BY b.name
        ORDER BY itemCount DESC
        LIMIT 5
    """)
    fun getTopBrandsByCount(): Flow<List<BrandItemCount>>

    @Query("SELECT COUNT(*) FROM items WHERE status = 'OWNED'")
    fun getOwnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE status = 'WISHED'")
    fun getWishedCount(): Flow<Int>

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("SELECT * FROM items WHERE status = :status AND id != :excludeId ORDER BY updated_at DESC")
    suspend fun getOwnedItemsExcluding(status: ItemStatus, excludeId: Long): List<Item>

    @Query("SELECT * FROM items WHERE style = :style OR style LIKE '%' || :style || '%' ORDER BY updated_at DESC")
    fun getItemsByStyle(style: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE season = :season OR season LIKE '%' || :season || '%' ORDER BY updated_at DESC")
    fun getItemsBySeason(season: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status = 'WISHED' AND priority = :priority ORDER BY updated_at DESC")
    fun getWishlistByPriorityFilter(priority: ItemPriority): Flow<List<Item>>

    @Query("""
        SELECT i.* FROM items i
        INNER JOIN brands b ON i.brand_id = b.id
        WHERE b.name = :brandName
        ORDER BY i.updated_at DESC
    """)
    fun getItemsByBrandName(brandName: String): Flow<List<Item>>

    @Query("""
        SELECT i.* FROM items i
        INNER JOIN categories c ON i.category_id = c.id
        WHERE c.name = :categoryName
        ORDER BY i.updated_at DESC
    """)
    fun getItemsByCategoryName(categoryName: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE location_id = :locationId ORDER BY updated_at DESC")
    fun getItemsByLocationId(locationId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE location_id IS NULL ORDER BY updated_at DESC")
    fun getItemsWithNoLocation(): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items WHERE location_id = :locationId")
    suspend fun countItemsByLocation(locationId: Long): Int

    @Query("SELECT COUNT(*) FROM items WHERE location_id IS NULL")
    fun countItemsWithNoLocation(): Flow<Int>

    @Query("SELECT location_id, COUNT(*) as count FROM items WHERE location_id IS NOT NULL GROUP BY location_id")
    fun getItemCountsByLocation(): Flow<List<LocationItemCount>>

    @Query("""
        SELECT location_id, image_urls FROM items
        WHERE location_id IS NOT NULL AND image_urls != '[]'
        ORDER BY updated_at DESC
    """)
    fun getLocationItemImages(): Flow<List<LocationItemImage>>

    @Query("UPDATE items SET source = :newName WHERE source = :oldName")
    suspend fun updateItemsSource(oldName: String, newName: String)

    @Query("UPDATE items SET source = NULL WHERE source = :name")
    suspend fun clearItemsSource(name: String)

    @Query("UPDATE items SET status = :status WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Long, status: String)

    @Query("""
        UPDATE items SET status = 'PENDING_BALANCE'
        WHERE status = 'OWNED'
        AND id IN (
            SELECT DISTINCT pr.item_id FROM payments p
            INNER JOIN prices pr ON p.price_id = pr.id
            WHERE pr.type = 'DEPOSIT_BALANCE' AND p.is_paid = 0
        )
    """)
    suspend fun refreshPendingBalanceStatus()

    @Query("UPDATE items SET coordinate_order = :order WHERE id = :itemId")
    suspend fun updateCoordinateOrder(itemId: Long, order: Int)
}

data class ItemWithFullDetails(
    @Embedded val item: Item,
    @Relation(parentColumn = "brand_id", entityColumn = "id")
    val brand: Brand,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category,
    @Relation(parentColumn = "id", entityColumn = "item_id")
    val prices: List<Price>
)

data class LocationItemCount(
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "count") val count: Int
)

data class LocationItemImage(
    @ColumnInfo(name = "location_id") val locationId: Long,
    @ColumnInfo(name = "image_urls") val imageUrls: List<String>
)
