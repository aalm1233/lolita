package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.ImageTask
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ImageTaskDao {
    @Query("SELECT * FROM image_tasks ORDER BY created_at DESC, id DESC")
    abstract fun observe(): Flow<List<ImageTask>>
    @Query("SELECT * FROM image_tasks WHERE id=:id")
    abstract fun observe(id: String): Flow<ImageTask?>
    @Query("SELECT * FROM image_tasks ORDER BY created_at DESC, id DESC")
    abstract suspend fun getAll(): List<ImageTask>
    @Query("SELECT * FROM image_tasks WHERE id=:id")
    abstract suspend fun get(id: String): ImageTask?
    @Insert abstract suspend fun insert(task: ImageTask)
    @Update abstract suspend fun update(task: ImageTask): Int
    @Query("DELETE FROM image_tasks WHERE id=:id") abstract suspend fun delete(id: String)
    @Query("DELETE FROM image_tasks") abstract suspend fun deleteAll()

    @Query("SELECT * FROM image_tasks WHERE status='QUEUED' ORDER BY created_at, id LIMIT 1")
    abstract suspend fun oldestQueued(): ImageTask?

    @Query("UPDATE image_tasks SET status='PREPARING', updated_at=:now WHERE id=:id AND status='QUEUED'")
    abstract suspend fun claimQueued(id: String, now: Long): Int

    @Transaction
    open suspend fun claimNextQueued(now: Long): ImageTask? {
        val queued = oldestQueued() ?: return null
        return if (claimQueued(queued.id, now) == 1) get(queued.id) else null
    }

    @Query("""UPDATE image_tasks SET status='INTERRUPTED', source_image_paths='[]', updated_at=:now, error=:error
        WHERE status IN ('QUEUED','PREPARING','GENERATING','SAVING')""")
    abstract suspend fun interruptUnfinished(now: Long, error: String): Int
}
