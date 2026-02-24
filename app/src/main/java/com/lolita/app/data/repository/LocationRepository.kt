package com.lolita.app.data.repository

import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.LocationDao
import com.lolita.app.data.local.dao.LocationItemCount
import com.lolita.app.data.local.dao.LocationItemImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.Location
import kotlinx.coroutines.flow.Flow

class LocationRepository(
    private val locationDao: LocationDao,
    private val itemDao: ItemDao
) {
    fun getAllLocations(): Flow<List<Location>> = locationDao.getAllLocations()

    suspend fun getAllLocationsList(): List<Location> = locationDao.getAllLocationsList()

    fun getLocationById(id: Long): Flow<Location?> = locationDao.getLocationById(id)

    suspend fun getLocationByIdSync(id: Long): Location? = locationDao.getLocationByIdSync(id)

    suspend fun insertLocation(location: Location): Long = locationDao.insertLocation(location)

    suspend fun updateLocation(location: Location) =
        locationDao.updateLocation(location.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteLocation(location: Location) {
        location.imageUrl?.let { ImageFileHelper.deleteImage(it) }
        locationDao.deleteLocation(location)
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
