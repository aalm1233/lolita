package com.lolita.app.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.IOException
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedLibrarySyncApi(
    private val gson: Gson = Gson()
) {
    suspend fun fetchSnapshot(baseUrl: String): SnapshotPayloadDto = withContext(Dispatchers.IO) {
        val type = object : TypeToken<ApiEnvelope<SnapshotPayloadDto>>() {}.type
        executeGet("$baseUrl/api/v1/sync/snapshot", type)
    }

    suspend fun fetchChanges(baseUrl: String, cursor: Long, limit: Int): ChangesPayloadDto = withContext(Dispatchers.IO) {
        val type = object : TypeToken<ApiEnvelope<ChangesPayloadDto>>() {}.type
        executeGet("$baseUrl/api/v1/sync/changes?cursor=$cursor&limit=$limit", type)
    }

    fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("请先填写后端地址")
        }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val uri = URI(withScheme)
        if (uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("后端地址格式不正确")
        }
        return withScheme.trimEnd('/')
    }

    private fun <T> executeGet(url: String, responseType: Type): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (statusCode !in 200..299) {
                throw IOException(parseErrorMessage(body).ifBlank { "请求失败: HTTP $statusCode" })
            }

            val envelope: ApiEnvelope<T> = gson.fromJson(body, responseType)
            if (envelope.code !in 200..299) {
                throw IOException(envelope.message.ifBlank { "请求失败" })
            }

            envelope.data ?: throw IOException("服务端返回了空数据")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseErrorMessage(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            gson.fromJson(body, ApiErrorEnvelope::class.java).message
        }.getOrNull().orEmpty()
    }
}

private data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

private data class ApiErrorEnvelope(
    val code: Int = 0,
    val message: String = ""
)

data class SnapshotPayloadDto(
    val schemaVersion: Int = 0,
    val assetBaseUrl: String = "",
    val nextCursor: Long = 0,
    val data: SnapshotDataDto = SnapshotDataDto()
)

data class SnapshotDataDto(
    val brands: List<BrandSyncDto> = emptyList(),
    val categories: List<CategorySyncDto> = emptyList(),
    val styles: List<StyleSyncDto> = emptyList(),
    val seasons: List<SeasonSyncDto> = emptyList(),
    val sources: List<SourceSyncDto> = emptyList(),
    val catalogEntries: List<CatalogEntrySyncDto> = emptyList(),
    val items: List<SharedItemSyncDto> = emptyList(),
    val coordinates: List<SharedCoordinateSyncDto> = emptyList(),
    val pricePlans: List<SharedPricePlanSyncDto> = emptyList()
)

data class ChangesPayloadDto(
    val schemaVersion: Int = 0,
    val assetBaseUrl: String = "",
    val nextCursor: Long = 0,
    val changes: ChangeBatchDto = ChangeBatchDto()
)

data class ChangeBatchDto(
    val brands: ResourceChangeSetDto<BrandSyncDto> = ResourceChangeSetDto(),
    val categories: ResourceChangeSetDto<CategorySyncDto> = ResourceChangeSetDto(),
    val styles: ResourceChangeSetDto<StyleSyncDto> = ResourceChangeSetDto(),
    val seasons: ResourceChangeSetDto<SeasonSyncDto> = ResourceChangeSetDto(),
    val sources: ResourceChangeSetDto<SourceSyncDto> = ResourceChangeSetDto(),
    val catalogEntries: ResourceChangeSetDto<CatalogEntrySyncDto> = ResourceChangeSetDto(),
    val items: ResourceChangeSetDto<SharedItemSyncDto> = ResourceChangeSetDto(),
    val coordinates: ResourceChangeSetDto<SharedCoordinateSyncDto> = ResourceChangeSetDto(),
    val pricePlans: ResourceChangeSetDto<SharedPricePlanSyncDto> = ResourceChangeSetDto()
)

data class ResourceChangeSetDto<T>(
    val upserts: List<T> = emptyList(),
    val deletedPublicIds: List<String> = emptyList()
)

data class BrandSyncDto(
    val publicId: String = "",
    val name: String = "",
    val logoUrl: String? = null,
    val updatedAt: Long = 0
)

data class CategorySyncDto(
    val publicId: String = "",
    val name: String = "",
    val group: String = "CLOTHING",
    val updatedAt: Long = 0
)

data class StyleSyncDto(
    val publicId: String = "",
    val name: String = "",
    val updatedAt: Long = 0
)

data class SeasonSyncDto(
    val publicId: String = "",
    val name: String = "",
    val updatedAt: Long = 0
)

data class SourceSyncDto(
    val publicId: String = "",
    val name: String = "",
    val updatedAt: Long = 0
)

data class CatalogEntrySyncDto(
    val publicId: String = "",
    val name: String = "",
    val brandPublicId: String? = null,
    val categoryPublicId: String? = null,
    val stylePublicId: String? = null,
    val seasonPublicId: String? = null,
    val sourcePublicId: String? = null,
    val seriesName: String? = null,
    val referenceUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val size: String? = null,
    val description: String = "",
    val updatedAt: Long = 0
)

data class SharedItemSyncDto(
    val publicId: String = "",
    val name: String = "",
    val description: String = "",
    val brandPublicId: String? = null,
    val categoryPublicId: String? = null,
    val stylePublicId: String? = null,
    val seasonPublicId: String? = null,
    val sourcePublicId: String? = null,
    val catalogEntryPublicId: String? = null,
    val coordinatePublicId: String? = null,
    val coordinateOrder: Int = 0,
    val imageUrls: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val size: String? = null,
    val sizeChartImageUrl: String? = null,
    val updatedAt: Long = 0
)

data class SharedCoordinateSyncDto(
    val publicId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrls: List<String> = emptyList(),
    val updatedAt: Long = 0
)

data class SharedPricePlanSyncDto(
    val publicId: String = "",
    val sharedItemPublicId: String = "",
    val priceType: String = "FULL",
    val totalPrice: Double = 0.0,
    val deposit: Double? = null,
    val balance: Double? = null,
    val depositDueAt: Long? = null,
    val balanceDueAt: Long? = null,
    val updatedAt: Long = 0
)
