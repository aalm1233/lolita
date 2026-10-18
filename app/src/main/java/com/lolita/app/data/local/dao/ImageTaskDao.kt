package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.ImageTaskExecutionSummary
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ImageTaskDao {
    @Query("SELECT * FROM image_tasks ORDER BY created_at DESC, id DESC")
    abstract fun observe(): Flow<List<ImageTask>>
    @Query("""SELECT active.id AS taskId, active.title AS title, active.status AS status,
        (SELECT COUNT(*) FROM image_tasks WHERE status='QUEUED') AS queuedCount
        FROM (SELECT 1) LEFT JOIN image_tasks AS active ON active.id =
        (SELECT id FROM image_tasks WHERE status IN ('PREPARING','GENERATING','SAVING') LIMIT 1)""")
    abstract fun observeExecutionSummary(): Flow<ImageTaskExecutionSummary>
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

    @Query("SELECT * FROM image_tasks WHERE status='QUEUED' AND id IN (:ids) ORDER BY created_at, id LIMIT 1")
    abstract suspend fun oldestQueued(ids: List<String>): ImageTask?

    @Query("UPDATE image_tasks SET status='PREPARING', updated_at=:now WHERE id=:id AND status='QUEUED'")
    abstract suspend fun claimQueued(id: String, now: Long): Int

    @Transaction
    open suspend fun claimNextQueued(now: Long): ImageTask? {
        val queued = oldestQueued() ?: return null
        return if (claimQueued(queued.id, now) == 1) get(queued.id) else null
    }

    @Query("""UPDATE image_tasks SET status='INTERRUPTED',
        source_image_paths=CASE WHEN kind='PERSONAL_MODEL' OR keep_source_images=1 THEN source_image_paths ELSE '[]' END,
        updated_at=:now, error=:error
        WHERE status IN ('QUEUED','PREPARING','GENERATING','SAVING')""")
    abstract suspend fun interruptUnfinished(now: Long, error: String): Int
}
