package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lolita.app.data.local.entity.Style
import kotlinx.coroutines.flow.Flow

@Dao
interface StyleDao {
    @Query("SELECT * FROM styles ORDER BY name ASC")
    fun getAllStyles(): Flow<List<Style>>

    @Query("SELECT * FROM styles WHERE id = :id")
    suspend fun getStyleById(id: Long): Style?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStyle(style: Style): Long

    @Update
    suspend fun updateStyle(style: Style)

    @Delete
    suspend fun deleteStyle(style: Style)

    @Query("SELECT * FROM styles WHERE name = :name LIMIT 1")
    suspend fun getStyleByName(name: String): Style?

    @Query("SELECT * FROM styles ORDER BY name ASC")
    suspend fun getAllStylesList(): List<Style>

    @Query("DELETE FROM styles")
    suspend fun deleteAllStyles()
}
