package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.OutfitLogDao
import com.lolita.app.data.local.dao.OutfitLogItemCount
import com.lolita.app.data.local.dao.OutfitLogWithItems
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.file.ImageFileHelper
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class OutfitLogRepository(
    private val outfitLogDao: OutfitLogDao,
    private val database: RoomDatabase
) {
    fun getAllOutfitLogs(): Flow<List<OutfitLog>> = outfitLogDao.getAllOutfitLogs()

    suspend fun getOutfitLogById(id: Long): OutfitLog? = outfitLogDao.getOutfitLogById(id)

    fun getOutfitLogsByDateRange(startDate: Long, endDate: Long): Flow<List<OutfitLog>> =
        outfitLogDao.getOutfitLogsByDateRange(startDate, endDate)

    fun getOutfitLogWithItems(id: Long): Flow<OutfitLogWithItems?> =
        outfitLogDao.getOutfitLogWithItems(id)

    suspend fun insertOutfitLog(outfitLog: OutfitLog): Long =
        outfitLogDao.insertOutfitLog(outfitLog)

    suspend fun updateOutfitLog(outfitLog: OutfitLog) =
        outfitLogDao.updateOutfitLog(outfitLog.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteOutfitLog(outfitLog: OutfitLog) {
        outfitLog.imageUrls.forEach { ImageFileHelper.deleteImage(it) }
        outfitLogDao.deleteOutfitLog(outfitLog)
    }

    suspend fun deleteOutfitLogById(id: Long) {
        val log = outfitLogDao.getOutfitLogById(id)
        if (log != null) {
            log.imageUrls.forEach { ImageFileHelper.deleteImage(it) }
            outfitLogDao.deleteOutfitLogById(id)
        }
    }

    suspend fun linkItemToOutfitLog(outfitLogId: Long, itemId: Long) {
        outfitLogDao.insertOutfitItemCrossRef(
            OutfitItemCrossRef(outfitLogId = outfitLogId, itemId = itemId)
        )
    }

    suspend fun unlinkItemFromOutfitLog(outfitLogId: Long, itemId: Long) {
        outfitLogDao.deleteOutfitItemCrossRef(
            OutfitItemCrossRef(outfitLogId = outfitLogId, itemId = itemId)
        )
    }

    fun getItemCountsByOutfitLog(): Flow<List<OutfitLogItemCount>> =
        outfitLogDao.getItemCountsByOutfitLog()

    suspend fun saveOutfitLogWithItems(
        outfitLog: OutfitLog,
        isNew: Boolean,
        addedItemIds: Set<Long>,
        removedItemIds: Set<Long>
    ): Long {
        return database.withTransaction {
            val logId = if (isNew) {
                outfitLogDao.insertOutfitLog(outfitLog)
            } else {
                outfitLogDao.updateOutfitLog(outfitLog.copy(updatedAt = System.currentTimeMillis()))
                outfitLog.id
            }
            removedItemIds.forEach { itemId ->
                outfitLogDao.deleteOutfitItemCrossRef(
                    OutfitItemCrossRef(outfitLogId = logId, itemId = itemId)
                )
            }
            addedItemIds.forEach { itemId ->
                outfitLogDao.insertOutfitItemCrossRef(
                    OutfitItemCrossRef(outfitLogId = logId, itemId = itemId)
                )
            }
            logId
        }
    }
}
