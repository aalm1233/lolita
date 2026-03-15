package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.CatalogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogEntryDao {
    @Query("SELECT * FROM catalog_entries ORDER BY updated_at DESC")
    fun getAllCatalogEntries(): Flow<List<CatalogEntry>>

    @Query("SELECT * FROM catalog_entries WHERE id = :id")
    fun getCatalogEntryById(id: Long): Flow<CatalogEntry?>

    @Query("SELECT * FROM catalog_entries WHERE id = :id")
    suspend fun getCatalogEntryByIdOnce(id: Long): CatalogEntry?

    @Query("SELECT * FROM catalog_entries WHERE linked_item_id = :linkedItemId LIMIT 1")
    suspend fun getCatalogEntryByLinkedItemId(linkedItemId: Long): CatalogEntry?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCatalogEntry(entry: CatalogEntry): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCatalogEntries(entries: List<CatalogEntry>)

    @Update
    suspend fun updateCatalogEntry(entry: CatalogEntry)

    @Delete
    suspend fun deleteCatalogEntry(entry: CatalogEntry)

    @Query("UPDATE catalog_entries SET linked_item_id = :linkedItemId, updated_at = :updatedAt WHERE id = :catalogEntryId")
    suspend fun updateLinkedItemId(catalogEntryId: Long, linkedItemId: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM catalog_entries")
    fun getCatalogEntryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_entries")
    suspend fun getCatalogEntryCountOnce(): Int

    @Query("SELECT * FROM catalog_entries ORDER BY updated_at DESC")
    suspend fun getAllCatalogEntriesList(): List<CatalogEntry>

    @Query("DELETE FROM catalog_entries")
    suspend fun deleteAllCatalogEntries()

    @Query("UPDATE catalog_entries SET style = :newName, updated_at = :updatedAt WHERE style = :oldName")
    suspend fun updateEntriesStyle(oldName: String, newName: String, updatedAt: Long)

    @Query("UPDATE catalog_entries SET style = NULL, updated_at = :updatedAt WHERE style = :name")
    suspend fun clearEntriesStyle(name: String, updatedAt: Long)

    @Query("UPDATE catalog_entries SET season = :newName, updated_at = :updatedAt WHERE season = :oldName")
    suspend fun updateEntriesSeason(oldName: String, newName: String, updatedAt: Long)

    @Query("UPDATE catalog_entries SET season = NULL, updated_at = :updatedAt WHERE season = :name")
    suspend fun clearEntriesSeason(name: String, updatedAt: Long)

    @Query("UPDATE catalog_entries SET source = :newName, updated_at = :updatedAt WHERE source = :oldName")
    suspend fun updateEntriesSource(oldName: String, newName: String, updatedAt: Long)

    @Query("UPDATE catalog_entries SET source = NULL, updated_at = :updatedAt WHERE source = :name")
    suspend fun clearEntriesSource(name: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM catalog_entries WHERE brand_id = :brandId")
    suspend fun countEntriesByBrand(brandId: Long): Int

    @Query("SELECT COUNT(*) FROM catalog_entries WHERE category_id = :categoryId")
    suspend fun countEntriesByCategory(categoryId: Long): Int
}
