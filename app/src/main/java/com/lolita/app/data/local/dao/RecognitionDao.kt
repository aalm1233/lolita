package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionDao {
    @Query("SELECT * FROM recognition_tasks WHERE draft_id=:draftId ORDER BY created_at DESC") fun observe(draftId: String): Flow<List<RecognitionTask>>
    @Query("SELECT * FROM recognition_tasks ORDER BY created_at DESC") suspend fun getAll(): List<RecognitionTask>
    @Query("SELECT * FROM recognition_tasks WHERE id=:id") suspend fun get(id: String): RecognitionTask?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(task: RecognitionTask)
    @Update suspend fun update(task: RecognitionTask)
    @Query("DELETE FROM recognition_tasks WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM recognition_tasks") suspend fun deleteAll()
    @Query("UPDATE recognition_tasks SET status='INTERRUPTED', error='识图已中断，请核对后主动重新提交',updated_at=:now WHERE status IN ('RUNNING','PREPARED')") suspend fun interruptUnfinished(now: Long)
    @Query("SELECT * FROM item_evidence ORDER BY created_at") suspend fun getEvidence(): List<ItemEvidence>
    @Query("SELECT * FROM item_evidence WHERE item_id=:id ORDER BY created_at") fun observeEvidence(id: Long): Flow<List<ItemEvidence>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEvidence(evidence: ItemEvidence)
    @Query("UPDATE item_evidence SET item_id=:itemId WHERE draft_id=:draftId AND item_id IS NULL") suspend fun attachEvidenceToItem(draftId: String, itemId: Long)
    @Query("DELETE FROM item_evidence WHERE draft_id=:draftId AND item_id IS NULL") suspend fun deleteDraftEvidence(draftId: String)
    @Query("DELETE FROM item_evidence WHERE id=:id") suspend fun deleteEvidence(id: String)
    @Query("DELETE FROM item_evidence") suspend fun deleteAllEvidence()
}
