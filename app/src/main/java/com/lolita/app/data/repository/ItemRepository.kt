package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.ItemWithFullDetails
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao
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

    suspend fun deleteItem(item: Item) = itemDao.deleteItem(item)
}
