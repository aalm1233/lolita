package com.lolita.app.data.repository

import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.dao.CatalogEntryDao
import com.lolita.app.data.local.entity.CatalogEntry
import kotlinx.coroutines.flow.Flow

class CatalogRepository(
    private val catalogEntryDao: CatalogEntryDao
) {
    fun getAllCatalogEntries(): Flow<List<CatalogEntry>> = catalogEntryDao.getAllCatalogEntries()

    fun getCatalogEntryById(id: Long): Flow<CatalogEntry?> = catalogEntryDao.getCatalogEntryById(id)

    suspend fun getCatalogEntryByIdOnce(id: Long): CatalogEntry? = catalogEntryDao.getCatalogEntryByIdOnce(id)

    suspend fun getCatalogEntryByLinkedItemId(linkedItemId: Long): CatalogEntry? =
        catalogEntryDao.getCatalogEntryByLinkedItemId(linkedItemId)

    suspend fun insertCatalogEntry(entry: CatalogEntry): Long = catalogEntryDao.insertCatalogEntry(entry)

    suspend fun updateCatalogEntry(entry: CatalogEntry) {
        catalogEntryDao.updateCatalogEntry(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCatalogEntry(entry: CatalogEntry) {
        catalogEntryDao.deleteCatalogEntry(entry)
        entry.imageUrls.forEach { path ->
            try {
                ImageFileHelper.deleteImage(path)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun linkToItem(catalogEntryId: Long, itemId: Long) {
        catalogEntryDao.updateLinkedItemId(
            catalogEntryId = catalogEntryId,
            linkedItemId = itemId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun clearLinkedItem(catalogEntryId: Long) {
        catalogEntryDao.updateLinkedItemId(
            catalogEntryId = catalogEntryId,
            linkedItemId = null,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getCatalogEntryCount(): Flow<Int> = catalogEntryDao.getCatalogEntryCount()

    suspend fun countEntriesByBrand(brandId: Long): Int = catalogEntryDao.countEntriesByBrand(brandId)

    suspend fun countEntriesByCategory(categoryId: Long): Int = catalogEntryDao.countEntriesByCategory(categoryId)
}
