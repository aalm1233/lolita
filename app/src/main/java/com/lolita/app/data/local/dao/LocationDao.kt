package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.Location
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY sort_order ASC, name ASC")
    fun getAllLocations(): Flow<List<Location>>

    @Query("SELECT * FROM locations ORDER BY sort_order ASC, name ASC")
    suspend fun getAllLocationsList(): List<Location>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationById(id: Long): Flow<Location?>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationByIdSync(id: Long): Location?

    @Query("SELECT * FROM locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): Location?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocation(location: Location): Long

    @Update
    suspend fun updateLocation(location: Location)

    @Delete
    suspend fun deleteLocation(location: Location)

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}
