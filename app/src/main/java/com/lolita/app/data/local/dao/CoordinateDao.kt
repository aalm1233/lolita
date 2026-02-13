package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCoordinate(coordinate: Coordinate): Long

    @Update
    suspend fun updateCoordinate(coordinate: Coordinate)

    @Delete
    suspend fun deleteCoordinate(coordinate: Coordinate)

    @Transaction
    @Query("SELECT * FROM coordinates WHERE id = :id")
    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems?>

    @Query("SELECT * FROM coordinates ORDER BY updated_at DESC")
    suspend fun getAllCoordinatesList(): List<Coordinate>

    @Query("SELECT COUNT(*) FROM coordinates")
    fun getCoordinateCount(): Flow<Int>

    @Query("SELECT coordinate_id, COUNT(*) as itemCount FROM items WHERE coordinate_id IS NOT NULL GROUP BY coordinate_id")
    fun getItemCountsByCoordinate(): Flow<List<CoordinateItemCount>>
}

data class CoordinateItemCount(
    val coordinate_id: Long,
    val itemCount: Int
)

data class CoordinateWithItems(
    @Embedded val coordinate: Coordinate,
    @Relation(
        parentColumn = "id",
        entityColumn = "coordinate_id"
    )
    val items: List<Item>
)
