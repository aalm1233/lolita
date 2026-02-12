package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
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

    @Query("SELECT * FROM items WHERE coordinate_id = :coordinateId")
    fun getItemsByCoordinate(coordinateId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE brand_id = :brandId")
    fun getItemsByBrand(brandId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE category_id = :categoryId")
    fun getItemsByCategory(categoryId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchItemsByName(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status = 'WISHED' ORDER BY " +
            "CASE priority " +
            "WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 " +
            "WHEN 'LOW' THEN 3 " +
            "END ASC, updated_at DESC")
    fun getWishlistByPriority(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails?>
}

data class ItemWithFullDetails(
    val item: Item,
    val brand: Brand,
    val category: Category,
    val prices: List<Price>
)
