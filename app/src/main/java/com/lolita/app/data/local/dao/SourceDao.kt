package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY name ASC")
    fun getAllSources(): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getSourceById(id: Long): Source?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(source: Source): Long

    @Update
    suspend fun updateSource(source: Source)

    @Delete
    suspend fun deleteSource(source: Source)

    @Query("SELECT * FROM sources WHERE name = :name LIMIT 1")
    suspend fun getSourceByName(name: String): Source?

    @Query("SELECT * FROM sources ORDER BY name ASC")
    suspend fun getAllSourcesList(): List<Source>

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}
