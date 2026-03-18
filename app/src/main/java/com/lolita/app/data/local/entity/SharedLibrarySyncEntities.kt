package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val SHARED_LIBRARY_SYNC_CACHE_KEY = "shared_library"

@Entity(tableName = "shared_library_sync_state")
data class SharedLibrarySyncState(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String = SHARED_LIBRARY_SYNC_CACHE_KEY,

    @ColumnInfo(name = "backend_base_url")
    val backendBaseUrl: String = "",

    @ColumnInfo(name = "asset_base_url")
    val assetBaseUrl: String = "",

    @ColumnInfo(name = "next_cursor", defaultValue = "0")
    val nextCursor: Long = 0,

    @ColumnInfo(name = "schema_version", defaultValue = "0")
    val schemaVersion: Int = 0,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)

@Entity(
    tableName = "remote_brands",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteBrand(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "logo_url")
    val logoUrl: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_categories",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteCategory(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category_group")
    val group: CategoryGroup = CategoryGroup.CLOTHING,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_styles",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteStyle(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_seasons",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteSeason(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_sources",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteSource(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_catalog_entries",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["brand_public_id"]),
        Index(value = ["category_public_id"])
    ]
)
data class RemoteCatalogEntry(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand_public_id")
    val brandPublicId: String? = null,

    @ColumnInfo(name = "category_public_id")
    val categoryPublicId: String? = null,

    @ColumnInfo(name = "style_public_id")
    val stylePublicId: String? = null,

    @ColumnInfo(name = "season_public_id")
    val seasonPublicId: String? = null,

    @ColumnInfo(name = "source_public_id")
    val sourcePublicId: String? = null,

    @ColumnInfo(name = "series_name")
    val seriesName: String? = null,

    @ColumnInfo(name = "reference_url")
    val referenceUrl: String? = null,

    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "colors")
    val colors: List<String> = emptyList(),

    @ColumnInfo(name = "size")
    val size: String? = null,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_shared_coordinates",
    indices = [Index(value = ["name"]), Index(value = ["updated_at"])]
)
data class RemoteSharedCoordinate(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_shared_items",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["brand_public_id"]),
        Index(value = ["category_public_id"]),
        Index(value = ["catalog_entry_public_id"]),
        Index(value = ["coordinate_public_id"])
    ]
)
data class RemoteSharedItem(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "brand_public_id")
    val brandPublicId: String? = null,

    @ColumnInfo(name = "category_public_id")
    val categoryPublicId: String? = null,

    @ColumnInfo(name = "style_public_id")
    val stylePublicId: String? = null,

    @ColumnInfo(name = "season_public_id")
    val seasonPublicId: String? = null,

    @ColumnInfo(name = "source_public_id")
    val sourcePublicId: String? = null,

    @ColumnInfo(name = "catalog_entry_public_id")
    val catalogEntryPublicId: String? = null,

    @ColumnInfo(name = "coordinate_public_id")
    val coordinatePublicId: String? = null,

    @ColumnInfo(name = "coordinate_order", defaultValue = "0")
    val coordinateOrder: Int = 0,

    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "colors")
    val colors: List<String> = emptyList(),

    @ColumnInfo(name = "size")
    val size: String? = null,

    @ColumnInfo(name = "size_chart_image_url")
    val sizeChartImageUrl: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "remote_shared_price_plans",
    indices = [Index(value = ["shared_item_public_id"]), Index(value = ["updated_at"])]
)
data class RemoteSharedPricePlan(
    @PrimaryKey
    @ColumnInfo(name = "public_id")
    val publicId: String,

    @ColumnInfo(name = "shared_item_public_id")
    val sharedItemPublicId: String,

    @ColumnInfo(name = "price_type")
    val priceType: PriceType = PriceType.FULL,

    @ColumnInfo(name = "total_price")
    val totalPrice: Double,

    @ColumnInfo(name = "deposit")
    val deposit: Double? = null,

    @ColumnInfo(name = "balance")
    val balance: Double? = null,

    @ColumnInfo(name = "deposit_due_at")
    val depositDueAt: Long? = null,

    @ColumnInfo(name = "balance_due_at")
    val balanceDueAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

data class SharedLibraryCacheSummary(
    @ColumnInfo(name = "brandCount")
    val brandCount: Int = 0,

    @ColumnInfo(name = "categoryCount")
    val categoryCount: Int = 0,

    @ColumnInfo(name = "styleCount")
    val styleCount: Int = 0,

    @ColumnInfo(name = "seasonCount")
    val seasonCount: Int = 0,

    @ColumnInfo(name = "sourceCount")
    val sourceCount: Int = 0,

    @ColumnInfo(name = "catalogCount")
    val catalogCount: Int = 0,

    @ColumnInfo(name = "coordinateCount")
    val coordinateCount: Int = 0,

    @ColumnInfo(name = "itemCount")
    val itemCount: Int = 0,

    @ColumnInfo(name = "pricePlanCount")
    val pricePlanCount: Int = 0
)

data class SharedLibraryPreviewItem(
    @ColumnInfo(name = "publicId")
    val publicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long
)
