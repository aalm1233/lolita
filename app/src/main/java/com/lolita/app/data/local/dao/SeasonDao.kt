package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Season
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons ORDER BY name ASC")
    fun getAllSeasons(): Flow<List<Season>>

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun getSeasonById(id: Long): Season?

    @Query("SELECT * FROM seasons WHERE is_preset = 1 ORDER BY name ASC")
    fun getPresetSeasons(): Flow<List<Season>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeason(season: Season): Long

    @Update
    suspend fun updateSeason(season: Season)

    @Delete
    suspend fun deleteSeason(season: Season)

    @Query("SELECT * FROM seasons WHERE name = :name LIMIT 1")
    suspend fun getSeasonByName(name: String): Season?

    @Query("SELECT * FROM seasons ORDER BY name ASC")
    suspend fun getAllSeasonsList(): List<Season>
}
