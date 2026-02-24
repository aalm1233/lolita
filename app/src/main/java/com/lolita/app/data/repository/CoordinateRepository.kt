package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.CoordinateDao
import com.lolita.app.data.local.dao.CoordinateItemCount
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.entity.Coordinate
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.flow.Flow

class CoordinateRepository(
    private val coordinateDao: CoordinateDao,
    private val itemDao: ItemDao,
    private val database: RoomDatabase
) {
    fun getAllCoordinates(): Flow<List<Coordinate>> =
        coordinateDao.getAllCoordinates()

    fun getCoordinateCount(): Flow<Int> = coordinateDao.getCoordinateCount()

    fun getItemCountsByCoordinate(): Flow<List<CoordinateItemCount>> =
        coordinateDao.getItemCountsByCoordinate()

    suspend fun getCoordinateById(id: Long): Coordinate? =
        coordinateDao.getCoordinateById(id)

    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems?> =
        coordinateDao.getCoordinateWithItems(id)

    suspend fun insertCoordinate(coordinate: Coordinate): Long {
        return coordinateDao.insertCoordinate(coordinate)
    }

    suspend fun insertCoordinateWithItems(coordinate: Coordinate, itemIds: Set<Long>): Long {
        return database.withTransaction {
            val id = coordinateDao.insertCoordinate(coordinate)
            itemIds.forEach { itemId ->
                val item = itemDao.getItemById(itemId)
                item?.let { itemDao.updateItem(it.copy(coordinateId = id)) }
            }
            id
        }
    }

    suspend fun updateCoordinateWithItems(
        coordinate: Coordinate,
        addedItemIds: Set<Long>,
        removedItemIds: Set<Long>
    ) {
        val oldCoordinate = coordinateDao.getCoordinateById(coordinate.id)

        database.withTransaction {
            coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))
            removedItemIds.forEach { itemId ->
                val item = itemDao.getItemById(itemId)
                item?.let { itemDao.updateItem(it.copy(coordinateId = null)) }
            }
            addedItemIds.forEach { itemId ->
                val item = itemDao.getItemById(itemId)
                item?.let { itemDao.updateItem(it.copy(coordinateId = coordinate.id)) }
            }
        }

        if (oldCoordinate != null) {
            val removedImages = oldCoordinate.imageUrls.filter { it !in coordinate.imageUrls }
            removedImages.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
        }
    }

    suspend fun updateCoordinate(coordinate: Coordinate) {
        val oldCoordinate = coordinateDao.getCoordinateById(coordinate.id)

        coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))

        if (oldCoordinate != null) {
            val removedImages = oldCoordinate.imageUrls.filter { it !in coordinate.imageUrls }
            removedImages.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
        }
    }

    suspend fun deleteCoordinate(coordinate: Coordinate) {
        database.withTransaction {
            // Unlink all items from this coordinate before deleting (FK RESTRICT)
            val withItems = coordinateDao.getCoordinateWithItemsList(coordinate.id)
            withItems?.items?.forEach { item ->
                itemDao.updateItem(item.copy(coordinateId = null))
            }
            coordinateDao.deleteCoordinate(coordinate)
        }
        // Clean up image files after transaction succeeds
        coordinate.imageUrls.forEach {
            try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {}
        }
    }
}
