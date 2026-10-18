package com.lolita.app.data.repository

import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.LocationDao
import com.lolita.app.data.local.dao.LocationItemCount
import com.lolita.app.data.local.dao.LocationItemImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.LolitaDatabase
import kotlinx.coroutines.flow.Flow

class LocationRepository(
    private val locationDao: LocationDao,
    private val itemDao: ItemDao,
    private val database: LolitaDatabase? = null,
    private val storageRepository: StorageRepository? = null
) {
    fun getAllLocations(): Flow<List<Location>> = locationDao.getAllLocations()

    suspend fun getAllLocationsList(): List<Location> = locationDao.getAllLocationsList()

    fun getLocationById(id: Long): Flow<Location?> = locationDao.getLocationById(id)

    suspend fun getLocationByIdSync(id: Long): Location? = locationDao.getLocationByIdSync(id)

    suspend fun insertLocation(location: Location): Long = locationDao.insertLocation(location)

    suspend fun updateLocation(location: Location) {
        val old = locationDao.getLocationByIdSync(location.id)
            ?: throw IllegalArgumentException("房间已删除")
        require(location.name.isNotBlank()) { "请填写房间名称" }
        locationDao.updateLocation(location.copy(name = location.name.trim(), createdAt = old.createdAt,
            updatedAt = System.currentTimeMillis()))
        old.imageUrl?.takeIf { it != location.imageUrl }?.let { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) }
    }

    suspend fun deleteLocation(location: Location, clearContents: Boolean = false) {
        val storage = storageRepository ?: database?.let { StorageRepository(it) }
        if (storage != null) {
            storage.deleteRoom(location, clearContents)
            return
        }
        require(!clearContents) { "清空房间位置需要数据库事务" }
        val count = itemDao.countItemsByLocation(location.id)
        if (count > 0) throw IllegalStateException("该房间下有 $count 条记录，请先移动内容")
        locationDao.deleteLocation(location)
        location.imageUrl?.let { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) }
    }

    suspend fun getLocationByName(name: String): Location? = locationDao.getLocationByName(name)

    fun getItemsByLocationId(locationId: Long): Flow<List<Item>> =
        itemDao.getItemsByLocationId(locationId)

    fun getItemsWithNoLocation(): Flow<List<Item>> =
        itemDao.getItemsWithNoLocation()

    suspend fun countItemsByLocation(locationId: Long): Int =
        itemDao.countItemsByLocation(locationId)

    fun countItemsWithNoLocation(): Flow<Int> = itemDao.countItemsWithNoLocation()

    fun getItemCountsByLocation(): Flow<List<LocationItemCount>> = itemDao.getItemCountsByLocation()

    fun getLocationItemImages(): Flow<List<LocationItemImage>> = itemDao.getLocationItemImages()
}
