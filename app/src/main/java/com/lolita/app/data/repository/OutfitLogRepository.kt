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
        val imageUrls = outfitLog.imageUrls.toList()
        outfitLogDao.deleteOutfitLog(outfitLog)
        imageUrls.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
    }

    suspend fun deleteOutfitLogById(id: Long) {
        val log = outfitLogDao.getOutfitLogById(id)
        if (log != null) {
            val imageUrls = log.imageUrls.toList()
            outfitLogDao.deleteOutfitLogById(id)
            imageUrls.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
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

    suspend fun getTodayOutfitLog(): OutfitLogWithItems? {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
        return outfitLogDao.getOutfitLogByDay(dayStart, dayEnd)
    }

    suspend fun insertOutfitLogWithItems(log: OutfitLog, itemIds: List<Long>) {
        database.withTransaction {
            val logId = outfitLogDao.insertOutfitLog(log)
            itemIds.forEach { itemId ->
                outfitLogDao.insertOutfitItemCrossRef(
                    OutfitItemCrossRef(itemId = itemId, outfitLogId = logId)
                )
            }
        }
    }

    suspend fun updateOutfitLogWithItems(log: OutfitLog, itemIds: List<Long>) {
        database.withTransaction {
            outfitLogDao.updateOutfitLog(log.copy(updatedAt = System.currentTimeMillis()))
            val existing = outfitLogDao.getOutfitItemCrossRefsByLogId(log.id)
            existing.forEach { outfitLogDao.deleteOutfitItemCrossRef(it) }
            itemIds.forEach { itemId ->
                outfitLogDao.insertOutfitItemCrossRef(
                    OutfitItemCrossRef(itemId = itemId, outfitLogId = log.id)
                )
            }
        }
    }

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
