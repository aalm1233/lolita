package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.PriceDao
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

class PriceRepository(
    private val priceDao: PriceDao
) {
    fun getPricesByItem(itemId: Long): Flow<List<Price>> =
        priceDao.getPricesByItem(itemId)

    suspend fun getPriceById(id: Long): Price? = priceDao.getPriceById(id)

    fun getPriceWithPayments(id: Long): Flow<PriceWithPayments?> =
        priceDao.getPriceWithPayments(id)

    suspend fun insertPrice(price: Price): Long = priceDao.insertPrice(price)

    suspend fun updatePrice(price: Price) = priceDao.updatePrice(price)

    suspend fun deletePrice(price: Price) = priceDao.deletePrice(price)
}

data class PriceWithPayments(
    val price: Price,
    val payments: List<com.lolita.app.data.local.entity.Payment>
)
