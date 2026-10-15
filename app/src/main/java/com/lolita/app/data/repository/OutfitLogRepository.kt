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
import java.util.Calendar
import java.util.TimeZone

data class OutfitDayRange(val start: Long, val endExclusive: Long)

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

    suspend fun updateOutfitLog(outfitLog: OutfitLog) {
        val oldImages = database.withTransaction {
            val original = requireNotNull(outfitLogDao.getOutfitLogById(outfitLog.id)) { "原穿搭已删除，无法保存" }
            outfitLogDao.updateOutfitLog(outfitLog.copy(updatedAt = System.currentTimeMillis()))
            original.imageUrls
        }
        ImageFileHelper.cleanupUnreferencedImages(oldImages - outfitLog.imageUrls.toSet())
    }

    suspend fun deleteOutfitLog(outfitLog: OutfitLog) {
        database.withTransaction {
            outfitLogDao.deleteOutfitItemCrossRefsByLogId(outfitLog.id)
            outfitLogDao.deleteOutfitLog(outfitLog)
        }
        outfitLog.imageUrls.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
    }

    suspend fun deleteOutfitLogById(id: Long) {
        val log = outfitLogDao.getOutfitLogById(id)
        if (log != null) {
            database.withTransaction {
                outfitLogDao.deleteOutfitItemCrossRefsByLogId(id)
                outfitLogDao.deleteOutfitLogById(id)
            }
            log.imageUrls.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
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

    fun dayRangeAt(
        timeMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): OutfitDayRange {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        // A local day can be 23 or 25 hours when daylight saving changes.
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return OutfitDayRange(start, calendar.timeInMillis)
    }

    fun observeOutfitLogByDay(range: OutfitDayRange): Flow<OutfitLogWithItems?> =
        outfitLogDao.observeOutfitLogByDay(range.start, range.endExclusive)

    suspend fun getTodayOutfitLog(
        timeMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): OutfitLogWithItems? {
        val range = dayRangeAt(timeMillis, timeZone)
        return outfitLogDao.getOutfitLogByDay(range.start, range.endExclusive)
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
        val oldImages = database.withTransaction {
            val original = requireNotNull(outfitLogDao.getOutfitLogById(log.id)) { "原穿搭已删除，无法保存" }
            outfitLogDao.updateOutfitLog(log.copy(updatedAt = System.currentTimeMillis()))
            val existing = outfitLogDao.getOutfitItemCrossRefsByLogId(log.id)
            existing.forEach { outfitLogDao.deleteOutfitItemCrossRef(it) }
            itemIds.forEach { itemId ->
                outfitLogDao.insertOutfitItemCrossRef(
                    OutfitItemCrossRef(itemId = itemId, outfitLogId = log.id)
                )
            }
            original.imageUrls
        }
        ImageFileHelper.cleanupUnreferencedImages(oldImages - log.imageUrls.toSet())
    }

    suspend fun saveOutfitLogWithItems(
        outfitLog: OutfitLog,
        isNew: Boolean,
        addedItemIds: Set<Long>,
        removedItemIds: Set<Long>
    ): Long {
        var oldImages: List<String> = emptyList()
        val savedId = database.withTransaction {
            if (!isNew) {
                oldImages = requireNotNull(outfitLogDao.getOutfitLogById(outfitLog.id)) { "原穿搭已删除，无法保存" }.imageUrls
            }
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
        ImageFileHelper.cleanupUnreferencedImages(oldImages - outfitLog.imageUrls.toSet())
        return savedId
    }
}
