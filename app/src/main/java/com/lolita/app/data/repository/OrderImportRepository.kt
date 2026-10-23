package com.lolita.app.data.repository

import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*

class OrderImportRepository(private val database: LolitaDatabase) {
    private val dao
        get() = database.orderImportDao()

    fun observeSessions() = dao.observeSessions()

    suspend fun session(id: String) = dao.session(id)

    suspend fun rows(id: String) = dao.rows(id)

    suspend fun files(id: String) = dao.files(id)

    suspend fun receipts() = dao.receipts()

    suspend fun commits(id: String) = dao.commits(id)

    fun observeSources(itemId: Long) = dao.observeSources(itemId)

    suspend fun save(
        session: OrderImportSession,
        rows: List<OrderImportRow>,
        files: List<OrderImportFile>,
    ) =
        database.withTransaction {
            val current = dao.session(session.id)
            require(
                if (current == null) session.revision == 1L
                else current.revision == session.revision - 1
            ) {
                "此会话已有更新，请重新打开草稿"
            }
            dao.saveSession(session)
            dao.clearRows(session.id)
            dao.saveRows(rows)
            dao.clearFiles(session.id)
            dao.saveFiles(files)
        }

    suspend fun discard(id: String): List<String> =
        database.withTransaction {
            val images = dao.rows(id).flatMap { it.imagePaths }
            dao.deleteSession(id)
            images
        }
}
