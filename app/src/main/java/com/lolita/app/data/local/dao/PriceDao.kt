package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    fun getPricesByItem(itemId: Long): Flow<List<Price>>

    @Query("SELECT * FROM prices WHERE id = :id")
    suspend fun getPriceById(id: Long): Price?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: Price): Long

    @Update
    suspend fun updatePrice(price: Price)

    @Delete
    suspend fun deletePrice(price: Price)

    @Transaction
    @Query("SELECT * FROM prices WHERE id = :id")
    fun getPriceWithPayments(id: Long): Flow<PriceWithPayments?>
}

data class PriceWithPayments(
    val price: Price,
    val payments: List<Payment>
)
