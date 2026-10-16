package com.lolita.app.data.repository

import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.PersonalModelDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PersonalModelRepository(
    private val database: LolitaDatabase,
    private val deleteImage: suspend (String) -> Unit = { ImageFileHelper.deleteImage(it) }
) {
    fun observe() = database.personalModelDao().observe()
    suspend fun get(id: Long) = database.personalModelDao().get(id)
    suspend fun getAll() = database.personalModelDao().getAll()
    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank() && trimmed.length <= 60) { "请填写60字以内的名称" }
        database.personalModelDao().rename(id, trimmed, System.currentTimeMillis())
    }
    suspend fun delete(id: Long) = withContext(NonCancellable + Dispatchers.IO) {
        val paths = database.withTransaction {
            database.personalModelDao().get(id)?.protectedImagePaths().orEmpty().also { database.personalModelDao().delete(id) }
        }
        paths.forEach { deleteImage(it) }
    }
    suspend fun getDraft(id: String) = database.personalModelDao().getDraft(id)
    suspend fun getDrafts() = database.personalModelDao().getDrafts()
    suspend fun saveDraft(draft: PersonalModelDraft) {
        draft.validate()
        withContext(NonCancellable + Dispatchers.IO) {
            val old = database.withTransaction {
                database.personalModelDao().getDraft(draft.id)?.sourceImagePaths.orEmpty().also {
                    database.personalModelDao().upsertDraft(draft)
                }
            }
            (old - draft.sourceImagePaths.toSet()).distinct().forEach { deleteImage(it) }
        }
    }
    suspend fun deleteDraft(id: String) = withContext(NonCancellable + Dispatchers.IO) {
        val paths = database.withTransaction {
            database.personalModelDao().getDraft(id)?.sourceImagePaths.orEmpty().also { database.personalModelDao().deleteDraft(id) }
        }
        paths.distinct().forEach { deleteImage(it) }
    }
}
