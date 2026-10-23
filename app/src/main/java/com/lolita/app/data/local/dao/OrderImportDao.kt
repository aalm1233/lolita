package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderImportDao {
    @Query("SELECT * FROM order_import_sessions ORDER BY updated_at DESC")
    fun observeSessions(): Flow<List<OrderImportSession>>

    @Query("SELECT * FROM order_import_sessions ORDER BY updated_at DESC")
    suspend fun sessions(): List<OrderImportSession>

    @Query("SELECT * FROM order_import_sessions WHERE id=:id")
    suspend fun session(id: String): OrderImportSession?

    @Upsert suspend fun saveSession(value: OrderImportSession)

    @Query("DELETE FROM order_import_sessions WHERE id=:id") suspend fun deleteSession(id: String)

    @Query("SELECT * FROM order_import_files") suspend fun files(): List<OrderImportFile>

    @Query("SELECT * FROM order_import_files WHERE session_id=:id")
    suspend fun files(id: String): List<OrderImportFile>

    @Upsert suspend fun saveFiles(values: List<OrderImportFile>)

    @Query("DELETE FROM order_import_files WHERE session_id=:id") suspend fun clearFiles(id: String)

    @Query("SELECT * FROM order_import_rows") suspend fun rows(): List<OrderImportRow>

    @Query("SELECT * FROM order_import_rows WHERE session_id=:id ORDER BY position")
    suspend fun rows(id: String): List<OrderImportRow>

    @Upsert suspend fun saveRows(values: List<OrderImportRow>)

    @Query("DELETE FROM order_import_rows WHERE session_id=:id") suspend fun clearRows(id: String)

    @Query("SELECT * FROM order_import_receipts") suspend fun receipts(): List<OrderImportReceipt>

    @Query(
        "SELECT * FROM order_import_receipts WHERE source_key=:key ORDER BY generation DESC LIMIT 1"
    )
    suspend fun receipt(key: String): OrderImportReceipt?

    @Insert suspend fun insertReceipts(values: List<OrderImportReceipt>)

    @Query("SELECT * FROM order_import_commits") suspend fun commits(): List<OrderImportCommit>

    @Query("SELECT * FROM order_import_commits WHERE id=:id")
    suspend fun commit(id: String): OrderImportCommit?

    @Query("SELECT * FROM order_import_commits WHERE session_id=:id ORDER BY created_at DESC")
    suspend fun commits(id: String): List<OrderImportCommit>

    @Insert suspend fun insertCommit(value: OrderImportCommit)

    @Update suspend fun updateCommit(value: OrderImportCommit)

    @Query("SELECT * FROM item_source_records") suspend fun sources(): List<ItemSourceRecord>

    @Query("SELECT * FROM item_source_records WHERE item_id=:itemId ORDER BY created_at DESC")
    fun observeSources(itemId: Long): Flow<List<ItemSourceRecord>>

    @Insert suspend fun insertSources(values: List<ItemSourceRecord>)

    @Query("DELETE FROM order_import_receipts") suspend fun clearReceipts()

    @Query("DELETE FROM order_import_commits") suspend fun clearCommits()

    @Query("DELETE FROM order_import_sessions") suspend fun clearSessions()

    @Query("DELETE FROM item_source_records") suspend fun clearSources()
}
