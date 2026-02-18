package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.ItemWithFullDetails
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.file.ImageFileHelper
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val paymentRepository: PaymentRepository? = null,
    private val priceRepository: PriceRepository? = null,
    private val database: LolitaDatabase? = null
) {
    fun getAllItems(): Flow<List<Item>> = itemDao.getAllItems()

    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>> =
        itemDao.getItemsByStatus(status)

    fun getWishlistByPriority(): Flow<List<Item>> =
        itemDao.getWishlistByPriority()

    fun searchItemsByName(query: String): Flow<List<Item>> =
        itemDao.searchItemsByName(query)

    suspend fun getItemById(id: Long): Item? = itemDao.getItemById(id)

    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails?> =
        itemDao.getItemWithFullDetails(id)

    suspend fun insertItem(item: Item): Long = itemDao.insertItem(item)

    suspend fun updateItem(item: Item) =
        itemDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteItem(item: Item) {
        val doDelete: suspend () -> Unit = {
            // Clean up AlarmManager reminders and calendar events before CASCADE deletes payments
            if (paymentRepository != null && priceRepository != null) {
                val prices = priceRepository.getPricesByItemList(item.id)
                for (price in prices) {
                    val payments = paymentRepository.getPaymentsByPriceList(price.id)
                    for (payment in payments) {
                        paymentRepository.deletePayment(payment)
                    }
                }
            }
            item.imageUrl?.let { ImageFileHelper.deleteImage(it) }
            item.sizeChartImageUrl?.let { ImageFileHelper.deleteImage(it) }
            itemDao.deleteItem(item)
        }
        if (database != null) {
            database.withTransaction { doDelete() }
        } else {
            doDelete()
        }
    }
}
