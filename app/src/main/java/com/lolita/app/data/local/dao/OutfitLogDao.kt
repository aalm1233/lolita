package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Query
import androidx.room.Relation
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

    @Query("DELETE FROM outfit_logs WHERE id = :id")
    suspend fun deleteOutfitLogById(id: Long)

    @Query("SELECT * FROM outfit_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getOutfitLogsByDateRange(startDate: Long, endDate: Long): Flow<List<OutfitLog>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutfitLog(outfitLog: OutfitLog): Long

    @Update
    suspend fun updateOutfitLog(outfitLog: OutfitLog)

    @Delete
    suspend fun deleteOutfitLog(outfitLog: OutfitLog)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Delete
    suspend fun deleteOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Transaction
    @Query("SELECT * FROM outfit_logs WHERE id = :id")
    fun getOutfitLogWithItems(id: Long): Flow<OutfitLogWithItems?>

    @Query("SELECT * FROM outfit_logs ORDER BY date DESC")
    suspend fun getAllOutfitLogsList(): List<OutfitLog>

    @Query("SELECT * FROM outfit_item_cross_ref")
    suspend fun getAllOutfitItemCrossRefsList(): List<OutfitItemCrossRef>

    @Query("SELECT outfit_log_id, COUNT(item_id) as itemCount FROM outfit_item_cross_ref GROUP BY outfit_log_id")
    fun getItemCountsByOutfitLog(): Flow<List<OutfitLogItemCount>>

    @Query("DELETE FROM outfit_logs")
    suspend fun deleteAllOutfitLogs()

    @Query("DELETE FROM outfit_item_cross_ref")
    suspend fun deleteAllOutfitItemCrossRefs()

    @Query("SELECT DISTINCT item_id FROM outfit_item_cross_ref WHERE outfit_log_id IN (SELECT outfit_log_id FROM outfit_item_cross_ref WHERE item_id = :itemId) AND item_id != :itemId")
    suspend fun getCoOccurringItemIds(itemId: Long): List<Long>

    @Transaction
    @Query("SELECT * FROM outfit_logs WHERE date BETWEEN :dayStart AND :dayEnd LIMIT 1")
    suspend fun getOutfitLogByDay(dayStart: Long, dayEnd: Long): OutfitLogWithItems?
}

data class OutfitLogWithItems(
    @Embedded val outfitLog: OutfitLog,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OutfitItemCrossRef::class,
            parentColumn = "outfit_log_id",
            entityColumn = "item_id"
        )
    )
    val items: List<Item>
)

data class OutfitLogItemCount(
    @androidx.room.ColumnInfo(name = "outfit_log_id") val outfitLogId: Long,
    val itemCount: Int
)
