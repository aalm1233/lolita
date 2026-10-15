package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.TryOnResult
import kotlinx.coroutines.flow.Flow

@Dao
interface TryOnDao {
    @Query("SELECT * FROM try_on_results ORDER BY created_at DESC,id DESC") fun observe(): Flow<List<TryOnResult>>
    @Query("SELECT * FROM try_on_results ORDER BY created_at DESC,id DESC") suspend fun getAll(): List<TryOnResult>
    @Query("SELECT * FROM try_on_results WHERE id=:id") suspend fun get(id: Long): TryOnResult?
    @Insert suspend fun insert(result: TryOnResult): Long
    @Query("UPDATE try_on_results SET name=:name WHERE id=:id") suspend fun rename(id: Long,name: String)
    @Query("DELETE FROM try_on_results WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM try_on_results") suspend fun deleteAll()
}
