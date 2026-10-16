package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.data.local.entity.PersonalModelDraft
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalModelDao {
    @Query("SELECT * FROM personal_models ORDER BY created_at DESC, id DESC") fun observe(): Flow<List<PersonalModel>>
    @Query("SELECT * FROM personal_models WHERE id=:id") suspend fun get(id: Long): PersonalModel?
    @Query("SELECT * FROM personal_models ORDER BY created_at DESC, id DESC") suspend fun getAll(): List<PersonalModel>
    @Insert suspend fun insert(model: PersonalModel): Long
    @Update suspend fun update(model: PersonalModel): Int
    @Query("UPDATE personal_models SET name=:name, updated_at=:now WHERE id=:id") suspend fun rename(id: Long, name: String, now: Long): Int
    @Query("DELETE FROM personal_models WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM personal_models") suspend fun deleteAll()
    @Query("SELECT * FROM personal_model_drafts WHERE id=:id") suspend fun getDraft(id: String): PersonalModelDraft?
    @Query("SELECT * FROM personal_model_drafts ORDER BY updated_at DESC, id DESC") suspend fun getDrafts(): List<PersonalModelDraft>
    @Upsert suspend fun upsertDraft(draft: PersonalModelDraft)
    @Query("DELETE FROM personal_model_drafts WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM personal_model_drafts") suspend fun deleteAllDrafts()
}
