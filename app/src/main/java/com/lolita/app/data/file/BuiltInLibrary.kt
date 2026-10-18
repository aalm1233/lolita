package com.lolita.app.data.file

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Offline defaults become ordinary editable records and UUID-owned local images. */
class BuiltInLibrary(
    private val context: Context,
    private val database: LolitaDatabase
) {
    private val preferences = context.getSharedPreferences("builtin_library", Context.MODE_PRIVATE)
    private val catalog by lazy {
        context.assets.open("builtin/catalog.json").bufferedReader().use {
            Gson().fromJson(it, Catalog::class.java)
        }.also { catalog ->
            require(catalog.formatVersion == 1) { "不支持的内置资料版本" }
            require(catalog.rooms.map { it.name }.distinct().size == catalog.rooms.size) { "重复的内置房间" }
            catalog.rooms.forEach { require(it.name.isNotBlank()) }
            catalog.brands.forEach { require(it.key.isNotBlank() && it.names.isNotEmpty()) }
        }
    }
    private val brandAssets by lazy {
        buildMap<String, String> {
            catalog.brands.forEach { brand ->
                brand.names.forEach { name ->
                    val key = normalizedName(name)
                    require(key.isNotBlank()) { "内置品牌名称为空" }
                    val previous = put(key, brand.assetPath)
                    require(previous == null || previous == brand.assetPath) { "内置品牌别名冲突：$name" }
                }
            }
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            if (preferences.getBoolean(INITIALIZED, false)) return@withLock
            val staged = mutableListOf<String>()
            try {
                // Validate brand aliases before creating files or changing any records.
                val assets = brandAssets
                database.withTransaction {
                    val locations = database.locationDao()
                    var nextSortOrder = (locations.getAllLocationsList().maxOfOrNull { it.sortOrder } ?: -1) + 1
                    catalog.rooms.forEach { room ->
                        val existing = locations.getLocationByName(room.name)
                        if (existing == null || existing.imageUrl.isNullOrBlank()) {
                            val image = ImageFileHelper.copyAssetToInternalStorage(context, room.assetPath)
                            staged.add(image)
                            if (existing == null) {
                                locations.insertLocation(Location(name = room.name, description = room.description,
                                    imageUrl = image, sortOrder = nextSortOrder++))
                            } else {
                                locations.updateLocation(existing.copy(imageUrl = image))
                            }
                        }
                    }
                    val brands = database.brandDao()
                    brands.getAllBrandsList().forEach { brand ->
                        if (brand.systemKey != null) return@forEach
                        val asset = assets[normalizedName(brand.name)]
                        if (brand.logoUrl.isNullOrBlank() && asset != null) {
                            val logo = ImageFileHelper.copyAssetToInternalStorage(context, asset)
                            staged.add(logo)
                            brands.updateBrand(brand.copy(logoUrl = logo))
                        }
                    }
                }
                // Local marker is deliberately outside business backups, and only follows commit.
                withContext(NonCancellable) {
                    check(preferences.edit().putBoolean(INITIALIZED, true).commit()) { "无法保存内置资料初始化状态" }
                }
            } catch (failure: Throwable) {
                cleanupUnreferenced(staged)
                throw failure
            }
        }
    }

    /** Only callers preparing a new record use this; updates never reapply a removed logo. */
    suspend fun withDefaultBrandLogo(brand: Brand): Brand {
        if (brand.systemKey != null || !brand.logoUrl.isNullOrBlank()) return brand
        val asset = withContext(Dispatchers.IO) { brandAssets[normalizedName(brand.name)] } ?: return brand
        return brand.copy(logoUrl = ImageFileHelper.copyAssetToInternalStorage(context, asset))
    }

    /** Own preparation and persistence together so an aborted insert cannot orphan its image. */
    suspend fun insertBrand(brand: Brand): Long = initializationMutex.withLock {
        val prepared = withDefaultBrandLogo(brand)
        try {
            database.brandDao().insertBrand(prepared)
        } catch (failure: Throwable) {
            if (prepared.logoUrl != brand.logoUrl) cleanupUnreferenced(listOfNotNull(prepared.logoUrl))
            throw failure
        }
    }

    private suspend fun cleanupUnreferenced(paths: List<String>) = withContext(NonCancellable + Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        // A cancellation can arrive just after SQLite committed. Keep any committed image alive.
        database.withTransaction {
            val references = database.brandDao().getAllBrandsList().mapNotNull { it.logoUrl }.toSet() +
                database.locationDao().getAllLocationsList().mapNotNull { it.imageUrl }.toSet()
            paths.filterNot { it in references }.forEach { File(it).delete() }
        }
    }

    private fun normalizedName(name: String) = name.trim().lowercase(Locale.ROOT)
    private data class Catalog(val formatVersion: Int, val rooms: List<Room>, val brands: List<BrandAsset>)
    private data class Room(val name: String, val description: String, val assetPath: String)
    private data class BrandAsset(val key: String, val names: List<String>, val assetPath: String)

    private companion object {
        const val INITIALIZED = "initialized_v1"
        // Also serializes distinct service instances in the same process (tests and callers).
        val initializationMutex = Mutex()
    }
}
