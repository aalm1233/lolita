package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ImageTaskDao
import com.lolita.app.data.local.entity.ImageTask
import kotlinx.coroutines.flow.Flow

class ImageTaskRepository(private val dao: ImageTaskDao) {
    val tasks: Flow<List<ImageTask>> = dao.observe()
    fun observe(id: String): Flow<ImageTask?> = dao.observe(id)
    suspend fun get(id: String): ImageTask? = dao.get(id)
    suspend fun getAll(): List<ImageTask> = dao.getAll()

    suspend fun insert(task: ImageTask) {
        task.validate()
        dao.insert(task.releaseUnusedSourceImages())
    }

    suspend fun claimQueued(id: String, now: Long = System.currentTimeMillis()): Boolean = dao.claimQueued(id, now) == 1
    suspend fun claimNextQueued(now: Long = System.currentTimeMillis()): ImageTask? = dao.claimNextQueued(now)
    suspend fun interruptUnfinished(now: Long = System.currentTimeMillis()): Int =
        dao.interruptUnfinished(now, "应用已重启，任务已中断；如需继续，请核对后重新发起")
}
