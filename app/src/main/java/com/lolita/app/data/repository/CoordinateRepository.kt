package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.CoordinateDao
import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.entity.Coordinate
import kotlinx.coroutines.flow.Flow

class CoordinateRepository(
    private val coordinateDao: CoordinateDao,
    private val itemDao: ItemDao
) {
    fun getAllCoordinates(): Flow<List<Coordinate>> =
        coordinateDao.getAllCoordinates()

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
        coordinateDao.deleteCoordinate(coordinate)
    }
}

data class CoordinateWithItems(
    val coordinate: Coordinate,
    val items: List<com.lolita.app.data.local.entity.Item>
)
