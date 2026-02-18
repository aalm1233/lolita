package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.OutfitLogDao
import com.lolita.app.data.local.dao.CoordinateDao
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus

class RecommendationRepository(
    private val itemDao: ItemDao,
    private val outfitLogDao: OutfitLogDao,
    private val coordinateDao: CoordinateDao
) {
    suspend fun getOwnedItemsExcluding(itemId: Long): List<Item> {
        return itemDao.getOwnedItemsExcluding(ItemStatus.OWNED, itemId)
    }

    suspend fun getCoOccurringItemIds(itemId: Long): Set<Long> {
        val fromOutfitLogs = outfitLogDao.getCoOccurringItemIds(itemId)
        val item = itemDao.getItemById(itemId)
        val fromCoordinate = if (item?.coordinateId != null) {
            val coordWithItems = coordinateDao.getCoordinateWithItemsList(item.coordinateId)
            coordWithItems?.items?.map { it.id }?.filter { it != itemId } ?: emptyList()
        } else emptyList()
        return (fromOutfitLogs + fromCoordinate).toSet()
    }

    suspend fun getItemById(itemId: Long): Item? {
        return itemDao.getItemById(itemId)
    }
}
