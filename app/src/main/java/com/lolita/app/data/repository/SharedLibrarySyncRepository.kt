package com.lolita.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.dao.SharedLibrarySyncDao
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.PriceType
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
import com.lolita.app.data.remote.BrandSyncDto
import com.lolita.app.data.remote.CatalogEntrySyncDto
import com.lolita.app.data.remote.CategorySyncDto
import com.lolita.app.data.remote.ChangeBatchDto
import com.lolita.app.data.remote.ChangesPayloadDto
import com.lolita.app.data.remote.SeasonSyncDto
import com.lolita.app.data.remote.SharedCoordinateSyncDto
import com.lolita.app.data.remote.SharedItemSyncDto
import com.lolita.app.data.remote.SharedLibrarySyncApi
import com.lolita.app.data.remote.SharedPricePlanSyncDto
import com.lolita.app.data.remote.SnapshotPayloadDto
import com.lolita.app.data.remote.SourceSyncDto
import com.lolita.app.data.remote.StyleSyncDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.absoluteValue

data class SharedLibrarySyncOverview(
    val syncState: SharedLibrarySyncState? = null,
    val cacheSummary: SharedLibraryCacheSummary = SharedLibraryCacheSummary(),
    val recentSharedItems: List<SharedLibraryPreviewItem> = emptyList(),
    val recentCatalogEntries: List<SharedLibraryPreviewItem> = emptyList()
)

data class SharedLibrarySyncResult(
    val fullSync: Boolean,
    val nextCursor: Long,
    val summary: SharedLibraryCacheSummary
)

class SharedLibrarySyncRepository(
    private val database: LolitaDatabase,
    private val dao: SharedLibrarySyncDao,
    private val api: SharedLibrarySyncApi = SharedLibrarySyncApi()
) {
    fun observeRemoteCatalogEntries(): Flow<List<RemoteCatalogEntry>> = dao.observeAllRemoteCatalogEntries()

    fun observeRemoteBrands(): Flow<List<RemoteBrand>> = dao.observeAllRemoteBrands()

    fun observeRemoteCategories(): Flow<List<RemoteCategory>> = dao.observeAllRemoteCategories()

    fun observeRemoteStyles(): Flow<List<RemoteStyle>> = dao.observeAllRemoteStyles()

    fun observeRemoteSeasons(): Flow<List<RemoteSeason>> = dao.observeAllRemoteSeasons()

    fun observeRemoteSources(): Flow<List<RemoteSource>> = dao.observeAllRemoteSources()

    fun observeOverview(): Flow<SharedLibrarySyncOverview> {
        return combine(
            dao.observeSyncState(),
            dao.observeCacheSummary(),
            dao.observeRecentSharedItems(limit = 5),
            dao.observeRecentCatalogEntries(limit = 5)
        ) { syncState, summary, recentItems, recentCatalogEntries ->
            SharedLibrarySyncOverview(
                syncState = syncState,
                cacheSummary = summary,
                recentSharedItems = recentItems,
                recentCatalogEntries = recentCatalogEntries
            )
        }
    }

    suspend fun sync(baseUrl: String, forceFull: Boolean = false): SharedLibrarySyncResult = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = api.normalizeBaseUrl(baseUrl)
        val currentState = dao.getSyncState()
        val shouldRunFullSync = forceFull ||
            currentState == null ||
            currentState.nextCursor <= 0L ||
            currentState.backendBaseUrl != normalizedBaseUrl ||
            currentState.schemaVersion != SUPPORTED_SCHEMA_VERSION

        runCatching {
            if (shouldRunFullSync) {
                val snapshot = api.fetchSnapshot(normalizedBaseUrl)
                validateSchema(snapshot.schemaVersion)
                applySnapshot(normalizedBaseUrl, snapshot)
            } else {
                applyIncrementalChanges(normalizedBaseUrl, currentState)
            }
        }.onFailure { error ->
            recordFailure(normalizedBaseUrl, error.message ?: "同步失败")
        }.getOrThrow()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        database.withTransaction {
            clearAllCachedTables()
            dao.deleteSyncState(SHARED_LIBRARY_SYNC_CACHE_KEY)
        }
    }

    suspend fun getRemoteCatalogEntryBySyntheticId(catalogEntryId: Long): RemoteCatalogEntry? = withContext(Dispatchers.IO) {
        dao.getAllRemoteCatalogEntriesList()
            .firstOrNull { syntheticCatalogEntryId(it.publicId) == catalogEntryId }
    }

    suspend fun getRemoteBrand(publicId: String?): RemoteBrand? = withContext(Dispatchers.IO) {
        val normalizedPublicId = publicId?.takeIf { it.isNotBlank() } ?: return@withContext null
        dao.getRemoteBrand(normalizedPublicId)
    }

    suspend fun getRemoteCategory(publicId: String?): RemoteCategory? = withContext(Dispatchers.IO) {
        val normalizedPublicId = publicId?.takeIf { it.isNotBlank() } ?: return@withContext null
        dao.getRemoteCategory(normalizedPublicId)
    }

    private suspend fun applySnapshot(baseUrl: String, snapshot: SnapshotPayloadDto): SharedLibrarySyncResult {
        val assetBaseUrl = snapshot.assetBaseUrl.ifBlank { baseUrl }
        database.withTransaction {
            clearAllCachedTables()
            dao.upsertBrands(snapshot.data.brands.map { it.toEntity(assetBaseUrl) })
            dao.upsertCategories(snapshot.data.categories.map { it.toEntity() })
            dao.upsertStyles(snapshot.data.styles.map { it.toEntity() })
            dao.upsertSeasons(snapshot.data.seasons.map { it.toEntity() })
            dao.upsertSources(snapshot.data.sources.map { it.toEntity() })
            dao.upsertCatalogEntries(snapshot.data.catalogEntries.map { it.toEntity(assetBaseUrl) })
            dao.upsertCoordinates(snapshot.data.coordinates.map { it.toEntity(assetBaseUrl) })
            dao.upsertSharedItems(snapshot.data.items.map { it.toEntity(assetBaseUrl) })
            dao.upsertPricePlans(snapshot.data.pricePlans.map { it.toEntity() })
            dao.upsertSyncState(
                SharedLibrarySyncState(
                    cacheKey = SHARED_LIBRARY_SYNC_CACHE_KEY,
                    backendBaseUrl = baseUrl,
                    assetBaseUrl = assetBaseUrl,
                    nextCursor = snapshot.nextCursor,
                    schemaVersion = snapshot.schemaVersion,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastError = null
                )
            )
        }

        return SharedLibrarySyncResult(
            fullSync = true,
            nextCursor = snapshot.nextCursor,
            summary = dao.observeCacheSummary().first()
        )
    }

    private suspend fun applyIncrementalChanges(baseUrl: String, currentState: SharedLibrarySyncState): SharedLibrarySyncResult {
        var cursor = currentState.nextCursor
        var lastResponse: ChangesPayloadDto? = null

        repeat(MAX_CHANGE_PAGES) { pageIndex ->
            val response = api.fetchChanges(baseUrl, cursor, CHANGE_PAGE_SIZE)
            validateSchema(response.schemaVersion)
            lastResponse = response

            database.withTransaction {
                applyChangeBatch(response.assetBaseUrl.ifBlank { baseUrl }, response.changes)
                dao.upsertSyncState(
                    currentState.copy(
                        backendBaseUrl = baseUrl,
                        assetBaseUrl = response.assetBaseUrl.ifBlank { baseUrl },
                        nextCursor = response.nextCursor,
                        schemaVersion = response.schemaVersion,
                        lastSyncedAt = System.currentTimeMillis(),
                        lastError = null
                    )
                )
            }

            if (response.nextCursor <= cursor) {
                return SharedLibrarySyncResult(
                    fullSync = false,
                    nextCursor = response.nextCursor,
                    summary = dao.observeCacheSummary().first()
                )
            }
            cursor = response.nextCursor

            if (pageIndex == MAX_CHANGE_PAGES - 1) {
                error("同步增量页数过多，请尝试全量重建")
            }
        }

        val finalResponse = lastResponse ?: return SharedLibrarySyncResult(
            fullSync = false,
            nextCursor = currentState.nextCursor,
            summary = dao.observeCacheSummary().first()
        )
        return SharedLibrarySyncResult(
            fullSync = false,
            nextCursor = finalResponse.nextCursor,
            summary = dao.observeCacheSummary().first()
        )
    }

    private suspend fun applyChangeBatch(assetBaseUrl: String, changes: ChangeBatchDto) {
        if (changes.brands.upserts.isNotEmpty()) dao.upsertBrands(changes.brands.upserts.map { it.toEntity(assetBaseUrl) })
        if (changes.brands.deletedPublicIds.isNotEmpty()) dao.deleteBrands(changes.brands.deletedPublicIds)

        if (changes.categories.upserts.isNotEmpty()) dao.upsertCategories(changes.categories.upserts.map { it.toEntity() })
        if (changes.categories.deletedPublicIds.isNotEmpty()) dao.deleteCategories(changes.categories.deletedPublicIds)

        if (changes.styles.upserts.isNotEmpty()) dao.upsertStyles(changes.styles.upserts.map { it.toEntity() })
        if (changes.styles.deletedPublicIds.isNotEmpty()) dao.deleteStyles(changes.styles.deletedPublicIds)

        if (changes.seasons.upserts.isNotEmpty()) dao.upsertSeasons(changes.seasons.upserts.map { it.toEntity() })
        if (changes.seasons.deletedPublicIds.isNotEmpty()) dao.deleteSeasons(changes.seasons.deletedPublicIds)

        if (changes.sources.upserts.isNotEmpty()) dao.upsertSources(changes.sources.upserts.map { it.toEntity() })
        if (changes.sources.deletedPublicIds.isNotEmpty()) dao.deleteSources(changes.sources.deletedPublicIds)

        if (changes.catalogEntries.upserts.isNotEmpty()) dao.upsertCatalogEntries(changes.catalogEntries.upserts.map { it.toEntity(assetBaseUrl) })
        if (changes.catalogEntries.deletedPublicIds.isNotEmpty()) dao.deleteCatalogEntries(changes.catalogEntries.deletedPublicIds)

        if (changes.coordinates.upserts.isNotEmpty()) dao.upsertCoordinates(changes.coordinates.upserts.map { it.toEntity(assetBaseUrl) })
        if (changes.coordinates.deletedPublicIds.isNotEmpty()) dao.deleteCoordinates(changes.coordinates.deletedPublicIds)

        if (changes.items.upserts.isNotEmpty()) dao.upsertSharedItems(changes.items.upserts.map { it.toEntity(assetBaseUrl) })
        if (changes.items.deletedPublicIds.isNotEmpty()) dao.deleteSharedItems(changes.items.deletedPublicIds)

        if (changes.pricePlans.upserts.isNotEmpty()) dao.upsertPricePlans(changes.pricePlans.upserts.map { it.toEntity() })
        if (changes.pricePlans.deletedPublicIds.isNotEmpty()) dao.deletePricePlans(changes.pricePlans.deletedPublicIds)
    }

    private suspend fun recordFailure(baseUrl: String, message: String) {
        database.withTransaction {
            val current = dao.getSyncState()
            dao.upsertSyncState(
                (current ?: SharedLibrarySyncState(cacheKey = SHARED_LIBRARY_SYNC_CACHE_KEY)).copy(
                    backendBaseUrl = baseUrl,
                    lastError = message
                )
            )
        }
    }

    private suspend fun clearAllCachedTables() {
        dao.clearPricePlans()
        dao.clearSharedItems()
        dao.clearCoordinates()
        dao.clearCatalogEntries()
        dao.clearSources()
        dao.clearSeasons()
        dao.clearStyles()
        dao.clearCategories()
        dao.clearBrands()
    }

    private fun validateSchema(schemaVersion: Int) {
        check(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "后端同步协议版本不兼容：$schemaVersion"
        }
    }

    private fun BrandSyncDto.toEntity(assetBaseUrl: String): RemoteBrand {
        return RemoteBrand(
            publicId = publicId,
            name = name,
            logoUrl = resolveAssetUrl(assetBaseUrl, logoUrl),
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun CategorySyncDto.toEntity(): RemoteCategory {
        return RemoteCategory(
            publicId = publicId,
            name = name,
            group = parseCategoryGroup(group),
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun StyleSyncDto.toEntity(): RemoteStyle {
        return RemoteStyle(publicId = publicId, name = name, updatedAt = normalizeRemoteTimestamp(updatedAt))
    }

    private fun SeasonSyncDto.toEntity(): RemoteSeason {
        return RemoteSeason(publicId = publicId, name = name, updatedAt = normalizeRemoteTimestamp(updatedAt))
    }

    private fun SourceSyncDto.toEntity(): RemoteSource {
        return RemoteSource(publicId = publicId, name = name, updatedAt = normalizeRemoteTimestamp(updatedAt))
    }

    private fun CatalogEntrySyncDto.toEntity(assetBaseUrl: String): RemoteCatalogEntry {
        return RemoteCatalogEntry(
            publicId = publicId,
            name = name,
            brandPublicId = brandPublicId,
            categoryPublicId = categoryPublicId,
            stylePublicId = stylePublicId,
            seasonPublicId = seasonPublicId,
            sourcePublicId = sourcePublicId,
            seriesName = seriesName,
            referenceUrl = referenceUrl,
            imageUrls = imageUrls.mapNotNull { resolveAssetUrl(assetBaseUrl, it) },
            colors = colors.filter { it.isNotBlank() },
            size = size,
            description = description,
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun SharedCoordinateSyncDto.toEntity(assetBaseUrl: String): RemoteSharedCoordinate {
        return RemoteSharedCoordinate(
            publicId = publicId,
            name = name,
            description = description,
            imageUrls = imageUrls.mapNotNull { resolveAssetUrl(assetBaseUrl, it) },
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun SharedItemSyncDto.toEntity(assetBaseUrl: String): RemoteSharedItem {
        return RemoteSharedItem(
            publicId = publicId,
            name = name,
            description = description,
            brandPublicId = brandPublicId,
            categoryPublicId = categoryPublicId,
            stylePublicId = stylePublicId,
            seasonPublicId = seasonPublicId,
            sourcePublicId = sourcePublicId,
            catalogEntryPublicId = catalogEntryPublicId,
            coordinatePublicId = coordinatePublicId,
            coordinateOrder = coordinateOrder,
            imageUrls = imageUrls.mapNotNull { resolveAssetUrl(assetBaseUrl, it) },
            colors = colors.filter { it.isNotBlank() },
            size = size,
            sizeChartImageUrl = resolveAssetUrl(assetBaseUrl, sizeChartImageUrl),
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun SharedPricePlanSyncDto.toEntity(): RemoteSharedPricePlan {
        return RemoteSharedPricePlan(
            publicId = publicId,
            sharedItemPublicId = sharedItemPublicId,
            priceType = parsePriceType(priceType),
            totalPrice = totalPrice,
            deposit = deposit,
            balance = balance,
            depositDueAt = normalizeRemoteTimestamp(depositDueAt),
            balanceDueAt = normalizeRemoteTimestamp(balanceDueAt),
            updatedAt = normalizeRemoteTimestamp(updatedAt)
        )
    }

    private fun normalizeRemoteTimestamp(value: Long?): Long {
        if (value == null || value <= 0L) return 0L
        return if (value < SECOND_TIMESTAMP_THRESHOLD) value * 1_000L else value
    }

    private fun resolveAssetUrl(assetBaseUrl: String, rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return if (trimmed.startsWith("/")) {
            assetBaseUrl.trimEnd('/') + trimmed
        } else {
            assetBaseUrl.trimEnd('/') + "/" + trimmed
        }
    }

    private fun parseCategoryGroup(value: String?): CategoryGroup {
        return runCatching {
            CategoryGroup.valueOf(value.orEmpty().trim().uppercase())
        }.getOrDefault(CategoryGroup.CLOTHING)
    }

    private fun parsePriceType(value: String?): PriceType {
        return runCatching {
            PriceType.valueOf(value.orEmpty().trim().uppercase())
        }.getOrDefault(PriceType.FULL)
    }

    companion object {
        fun syntheticRemoteId(publicId: String): Long {
            val base = runCatching {
                UUID.fromString(publicId).mostSignificantBits xor UUID.fromString(publicId).leastSignificantBits
            }.getOrElse {
                publicId.hashCode().toLong()
            }
            if (base == Long.MIN_VALUE) return Long.MIN_VALUE + 1
            val negative = -base.absoluteValue
            return if (negative == 0L) -1L else negative
        }

        fun syntheticCatalogEntryId(publicId: String): Long {
            return syntheticRemoteId(publicId)
        }

        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val CHANGE_PAGE_SIZE = 200
        private const val MAX_CHANGE_PAGES = 50
        private const val SECOND_TIMESTAMP_THRESHOLD = 10_000_000_000L
    }
}
