package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lolita.app.data.local.entity.RemoteBrand
import com.lolita.app.data.local.entity.RemoteCatalogEntry
import com.lolita.app.data.local.entity.RemoteCategory
import com.lolita.app.data.local.entity.RemoteSeason
import com.lolita.app.data.local.entity.RemoteSharedCoordinate
import com.lolita.app.data.local.entity.RemoteSharedItem
import com.lolita.app.data.local.entity.RemoteSharedPricePlan
import com.lolita.app.data.local.entity.RemoteSource
import com.lolita.app.data.local.entity.RemoteStyle
import com.lolita.app.data.local.entity.SHARED_LIBRARY_SYNC_CACHE_KEY
import com.lolita.app.data.local.entity.SharedLibraryCacheSummary
import com.lolita.app.data.local.entity.SharedLibraryPreviewItem
import com.lolita.app.data.local.entity.SharedLibrarySyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedLibrarySyncDao {
    @Query("SELECT * FROM shared_library_sync_state WHERE cache_key = :cacheKey LIMIT 1")
    fun observeSyncState(cacheKey: String = SHARED_LIBRARY_SYNC_CACHE_KEY): Flow<SharedLibrarySyncState?>

    @Query("SELECT * FROM shared_library_sync_state WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun getSyncState(cacheKey: String = SHARED_LIBRARY_SYNC_CACHE_KEY): SharedLibrarySyncState?

    @Query("SELECT * FROM remote_catalog_entries ORDER BY updated_at DESC, name ASC")
    fun observeAllRemoteCatalogEntries(): Flow<List<RemoteCatalogEntry>>

    @Query("SELECT * FROM remote_brands ORDER BY name ASC")
    fun observeAllRemoteBrands(): Flow<List<RemoteBrand>>

    @Query("SELECT * FROM remote_categories ORDER BY name ASC")
    fun observeAllRemoteCategories(): Flow<List<RemoteCategory>>

    @Query("SELECT * FROM remote_styles ORDER BY name ASC")
    fun observeAllRemoteStyles(): Flow<List<RemoteStyle>>

    @Query("SELECT * FROM remote_seasons ORDER BY name ASC")
    fun observeAllRemoteSeasons(): Flow<List<RemoteSeason>>

    @Query("SELECT * FROM remote_sources ORDER BY name ASC")
    fun observeAllRemoteSources(): Flow<List<RemoteSource>>

    @Query("SELECT * FROM remote_catalog_entries ORDER BY updated_at DESC, name ASC")
    suspend fun getAllRemoteCatalogEntriesList(): List<RemoteCatalogEntry>

    @Query("SELECT * FROM remote_brands WHERE public_id = :publicId LIMIT 1")
    suspend fun getRemoteBrand(publicId: String): RemoteBrand?

    @Query("SELECT * FROM remote_categories WHERE public_id = :publicId LIMIT 1")
    suspend fun getRemoteCategory(publicId: String): RemoteCategory?

    @Upsert
    suspend fun upsertSyncState(state: SharedLibrarySyncState)

    @Query("DELETE FROM shared_library_sync_state WHERE cache_key = :cacheKey")
    suspend fun deleteSyncState(cacheKey: String = SHARED_LIBRARY_SYNC_CACHE_KEY)

    @Upsert
    suspend fun upsertBrands(items: List<RemoteBrand>)

    @Upsert
    suspend fun upsertCategories(items: List<RemoteCategory>)

    @Upsert
    suspend fun upsertStyles(items: List<RemoteStyle>)

    @Upsert
    suspend fun upsertSeasons(items: List<RemoteSeason>)

    @Upsert
    suspend fun upsertSources(items: List<RemoteSource>)

    @Upsert
    suspend fun upsertCatalogEntries(items: List<RemoteCatalogEntry>)

    @Upsert
    suspend fun upsertCoordinates(items: List<RemoteSharedCoordinate>)

    @Upsert
    suspend fun upsertSharedItems(items: List<RemoteSharedItem>)

    @Upsert
    suspend fun upsertPricePlans(items: List<RemoteSharedPricePlan>)

    @Query("DELETE FROM remote_brands WHERE public_id IN (:publicIds)")
    suspend fun deleteBrands(publicIds: List<String>)

    @Query("DELETE FROM remote_categories WHERE public_id IN (:publicIds)")
    suspend fun deleteCategories(publicIds: List<String>)

    @Query("DELETE FROM remote_styles WHERE public_id IN (:publicIds)")
    suspend fun deleteStyles(publicIds: List<String>)

    @Query("DELETE FROM remote_seasons WHERE public_id IN (:publicIds)")
    suspend fun deleteSeasons(publicIds: List<String>)

    @Query("DELETE FROM remote_sources WHERE public_id IN (:publicIds)")
    suspend fun deleteSources(publicIds: List<String>)

    @Query("DELETE FROM remote_catalog_entries WHERE public_id IN (:publicIds)")
    suspend fun deleteCatalogEntries(publicIds: List<String>)

    @Query("DELETE FROM remote_shared_coordinates WHERE public_id IN (:publicIds)")
    suspend fun deleteCoordinates(publicIds: List<String>)

    @Query("DELETE FROM remote_shared_items WHERE public_id IN (:publicIds)")
    suspend fun deleteSharedItems(publicIds: List<String>)

    @Query("DELETE FROM remote_shared_price_plans WHERE public_id IN (:publicIds)")
    suspend fun deletePricePlans(publicIds: List<String>)

    @Query("DELETE FROM remote_shared_price_plans")
    suspend fun clearPricePlans()

    @Query("DELETE FROM remote_shared_items")
    suspend fun clearSharedItems()

    @Query("DELETE FROM remote_shared_coordinates")
    suspend fun clearCoordinates()

    @Query("DELETE FROM remote_catalog_entries")
    suspend fun clearCatalogEntries()

    @Query("DELETE FROM remote_sources")
    suspend fun clearSources()

    @Query("DELETE FROM remote_seasons")
    suspend fun clearSeasons()

    @Query("DELETE FROM remote_styles")
    suspend fun clearStyles()

    @Query("DELETE FROM remote_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM remote_brands")
    suspend fun clearBrands()

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM remote_brands) AS brandCount,
            (SELECT COUNT(*) FROM remote_categories) AS categoryCount,
            (SELECT COUNT(*) FROM remote_styles) AS styleCount,
            (SELECT COUNT(*) FROM remote_seasons) AS seasonCount,
            (SELECT COUNT(*) FROM remote_sources) AS sourceCount,
            (SELECT COUNT(*) FROM remote_catalog_entries) AS catalogCount,
            (SELECT COUNT(*) FROM remote_shared_coordinates) AS coordinateCount,
            (SELECT COUNT(*) FROM remote_shared_items) AS itemCount,
            (SELECT COUNT(*) FROM remote_shared_price_plans) AS pricePlanCount
        """
    )
    fun observeCacheSummary(): Flow<SharedLibraryCacheSummary>

    @Query(
        """
        SELECT public_id AS publicId, name, updated_at AS updatedAt
        FROM remote_shared_items
        ORDER BY updated_at DESC, name ASC
        LIMIT :limit
        """
    )
    fun observeRecentSharedItems(limit: Int): Flow<List<SharedLibraryPreviewItem>>

    @Query(
        """
        SELECT public_id AS publicId, name, updated_at AS updatedAt
        FROM remote_catalog_entries
        ORDER BY updated_at DESC, name ASC
        LIMIT :limit
        """
    )
    fun observeRecentCatalogEntries(limit: Int): Flow<List<SharedLibraryPreviewItem>>
}
