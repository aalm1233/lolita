package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.entity.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface CoordinateDao {
    @Query("SELECT * FROM coordinates ORDER BY updated_at DESC")
    fun getAllCoordinates(): Flow<List<Coordinate>>

    @Query("SELECT * FROM coordinates WHERE id = :id")
    suspend fun getCoordinateById(id: Long): Coordinate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinate(coordinate: Coordinate): Long

    @Update
    suspend fun updateCoordinate(coordinate: Coordinate)

    @Delete
    suspend fun deleteCoordinate(coordinate: Coordinate)

    @Transaction
    @Query("SELECT * FROM coordinates WHERE id = :id")
    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems>
}

data class CoordinateWithItems(
    val coordinate: Coordinate,
    val items: List<Item>
)
