package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.ItemWithFullDetails
import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.file.ImageFileHelper
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val paymentRepository: PaymentRepository? = null,
    private val priceRepository: PriceRepository? = null,
    private val database: LolitaDatabase? = null,
    private val paymentDao: PaymentDao? = null
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

    fun getTopBrandsByCount() = itemDao.getTopBrandsByCount()
    fun getOwnedCount() = itemDao.getOwnedCount()
    fun getWishedCount() = itemDao.getWishedCount()

    fun getItemsByBrandName(brandName: String) = itemDao.getItemsByBrandName(brandName)
    fun getItemsByCategoryName(categoryName: String) = itemDao.getItemsByCategoryName(categoryName)
    fun getItemsByStyle(style: String) = itemDao.getItemsByStyle(style)
    fun getItemsBySeason(season: String) = itemDao.getItemsBySeason(season)
    fun getWishlistByPriorityFilter(priority: ItemPriority) = itemDao.getWishlistByPriorityFilter(priority)

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

    suspend fun checkAndUpdatePendingBalanceStatus(itemId: Long) {
        val dao = paymentDao ?: return
        val item = itemDao.getItemById(itemId) ?: return
        if (item.status == ItemStatus.OWNED || item.status == ItemStatus.PENDING_BALANCE) {
            val unpaidCount = dao.countUnpaidBalancePaymentsForItem(itemId)
            val newStatus = if (unpaidCount > 0) ItemStatus.PENDING_BALANCE else ItemStatus.OWNED
            if (item.status != newStatus) {
                itemDao.updateItemStatus(itemId, newStatus.name)
            }
        }
    }
}
