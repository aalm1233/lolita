package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.OutfitLogDao
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import kotlinx.coroutines.flow.Flow

class OutfitLogRepository(
    private val outfitLogDao: OutfitLogDao
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

    suspend fun deleteOutfitLog(outfitLog: OutfitLog) =
        outfitLogDao.deleteOutfitLog(outfitLog)

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
}

data class OutfitLogWithItems(
    val outfitLog: OutfitLog,
    val items: List<Item>
)
