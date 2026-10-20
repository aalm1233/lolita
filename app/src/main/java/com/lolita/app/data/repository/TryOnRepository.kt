package com.lolita.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TryOnRepository(private val context: Context,private val database: LolitaDatabase) {
    val results = database.tryOnDao().observe()

    suspend fun save(name: String,bytes: ByteArray,selected: List<TryOnSelection>,model: String): Long {
        require(name.isNotBlank() && selected.isNotEmpty()) { "试衣记录缺少名称或服饰" }
        val path = ImageFileHelper.saveGeneratedImage(context,bytes)
        var committed = false
        return try {
            withContext(NonCancellable + Dispatchers.IO) {
                database.tryOnDao().insert(TryOnResult(name=name.trim().take(120),imageUrl=path,
                    itemIds=selected.map { it.itemId.toString() },itemNames=selected.map { it.name },model=model)).also { committed=true }
            }
        } catch (failure: Throwable) {
            if (!committed) withContext(NonCancellable) { ImageFileHelper.deleteImage(path) }
            throw failure
        }
    }

    suspend fun rename(id: Long,name: String) {
        require(name.isNotBlank() && name.length <= 120) { "请填写120字以内的名称" }
        database.tryOnDao().rename(id,name.trim())
    }

    suspend fun delete(id: Long) = withContext(NonCancellable + Dispatchers.IO) {
        val path = database.withTransaction {
            database.tryOnDao().get(id)?.let { listOfNotNull(it.imageUrl, it.baseSnapshotImagePath) }.orEmpty().also { database.tryOnDao().delete(id) }
        }
        path.forEach { ImageFileHelper.deleteImage(it) }
    }
}
