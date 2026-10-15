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
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.local.entity.BrandItemCount
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updated_at DESC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT DISTINCT size FROM items WHERE size IS NOT NULL AND TRIM(size) <> '' ORDER BY size")
    fun getSizes(): Flow<List<String>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items WHERE import_token = :token LIMIT 1")
    suspend fun getItemByImportToken(token: String): Item?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeItem(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE parent_item_id = :parentId ORDER BY coordinate_order, created_at, id")
    fun getChildren(parentId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE parent_item_id = :parentId ORDER BY coordinate_order, created_at, id")
    suspend fun getChildrenList(parentId: Long): List<Item>

    @Query("UPDATE items SET status = :status WHERE parent_item_id = :parentId AND included_in_parent_price = 1")
    suspend fun updateSharedChildrenStatus(parentId: Long, status: String)

    @Query("SELECT * FROM items WHERE status = :status ORDER BY updated_at DESC")
    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>>

    // One query keeps every review entry, reason and count on the same Room snapshot.
    // Shared accessories use the parent's current ledger; archived orders stay untouched.
    @Query("""
        SELECT i.*, owner.id AS orderItemId, b.price_id AS priceId,
            b.purchase_record_status AS purchaseRecordStatus,
            EXISTS(SELECT 1 FROM payments p WHERE p.price_id=b.price_id
                AND p.is_paid=1 AND p.paid_date IS NULL) AS hasUnknownPaymentDates
        FROM items i
        JOIN items owner ON owner.id=CASE
            WHEN i.included_in_parent_price=1 AND i.parent_item_id IS NOT NULL
                THEN i.parent_item_id ELSE i.id END
        LEFT JOIN order_bindings b ON b.item_id=owner.id
        WHERE i.status='PENDING_REVIEW'
            OR b.purchase_record_status<>'TRACKED'
            OR (b.item_id IS NULL AND owner.status<>'WISHED')
            OR EXISTS(SELECT 1 FROM payments p WHERE p.price_id=b.price_id
                AND p.is_paid=1 AND p.paid_date IS NULL)
        ORDER BY i.updated_at DESC, i.id DESC
    """)
    fun getPurchaseReviews(): Flow<List<ItemPurchaseReview>>

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

    @Query("UPDATE items SET image_urls = :imageUrls, updated_at = :updatedAt WHERE id = :itemId")
    suspend fun updateImages(itemId: Long, imageUrls: List<String>, updatedAt: Long): Int

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

    @Query("UPDATE items SET parent_item_id = NULL, included_in_parent_price = 0")
    suspend fun clearAllParentLinks()

    @Query("DELETE FROM items")
    suspend fun deleteAllItemRows()

    @Transaction
    suspend fun deleteAllItems() {
        clearAllParentLinks()
        deleteAllItemRows()
    }

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("SELECT * FROM items WHERE status = :status AND id != :excludeId ORDER BY updated_at DESC")
    suspend fun getOwnedItemsExcluding(status: ItemStatus, excludeId: Long): List<Item>

    @Query("SELECT * FROM items WHERE style = :style ORDER BY updated_at DESC")
    fun getItemsByStyle(style: String): Flow<List<Item>>

    // Match Kotlin Char.isWhitespace used by SpendingBreakdown.seasons when trimming tokens.
    @Query("""
        WITH RECURSIVE season_tokens(item_id, token, rest) AS (
            SELECT id, '', COALESCE(season, '') || ',' FROM items
            UNION ALL
            SELECT item_id,
                trim(substr(rest, 1, instr(rest, ',') - 1), char(
                    9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 160, 5760,
                    8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202,
                    8232, 8233, 8239, 8287, 12288
                )),
                substr(rest, instr(rest, ',') + 1)
            FROM season_tokens WHERE rest != ''
        )
        SELECT * FROM items WHERE id IN (
            SELECT item_id FROM season_tokens WHERE token = :season AND token != ''
        ) ORDER BY updated_at DESC
    """)
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
