package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitLogDao {
    @Query("SELECT * FROM outfit_logs ORDER BY date DESC")
    fun getAllOutfitLogs(): Flow<List<OutfitLog>>

    @Query("SELECT * FROM outfit_logs WHERE id = :id")
    suspend fun getOutfitLogById(id: Long): OutfitLog?

    @Query("SELECT * FROM outfit_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getOutfitLogsByDateRange(startDate: Long, endDate: Long): Flow<List<OutfitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitLog(outfitLog: OutfitLog): Long

    @Update
    suspend fun updateOutfitLog(outfitLog: OutfitLog)

    @Delete
    suspend fun deleteOutfitLog(outfitLog: OutfitLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Delete
    suspend fun deleteOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Transaction
    @Query("SELECT * FROM outfit_logs WHERE id = :id")
    fun getOutfitLogWithItems(id: Long): Flow<OutfitLogWithItems?>
}

data class OutfitLogWithItems(
    val outfitLog: OutfitLog,
    val items: List<Item>
)
