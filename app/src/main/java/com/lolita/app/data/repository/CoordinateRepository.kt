package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.CoordinateDao
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.entity.Coordinate
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CoordinateRepository(
    private val coordinateDao: CoordinateDao,
    private val itemDao: ItemDao,
    private val database: RoomDatabase
) {
    fun getAllCoordinates(): Flow<List<Coordinate>> =
        coordinateDao.getAllCoordinates()

    fun getCoordinateCount(): Flow<Int> = coordinateDao.getCoordinateCount()

    suspend fun getCoordinateById(id: Long): Coordinate? =
        coordinateDao.getCoordinateById(id)

    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems?> =
        coordinateDao.getCoordinateWithItems(id)

    suspend fun insertCoordinate(coordinate: Coordinate): Long {
        return coordinateDao.insertCoordinate(coordinate)
    }

    suspend fun updateCoordinate(coordinate: Coordinate) =
        coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteCoordinate(coordinate: Coordinate) {
        database.withTransaction {
            // Unlink all items from this coordinate before deleting (FK RESTRICT)
            val withItems = coordinateDao.getCoordinateWithItems(coordinate.id).first()
            withItems?.items?.forEach { item ->
                itemDao.updateItem(item.copy(coordinateId = null))
            }
            coordinateDao.deleteCoordinate(coordinate)
        }
    }
}
