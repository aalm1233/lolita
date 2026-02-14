package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.dao.PriceDao
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import kotlinx.coroutines.flow.Flow

class PriceRepository(
    private val priceDao: PriceDao,
    private val paymentDao: PaymentDao
) {
    fun getPricesByItem(itemId: Long): Flow<List<Price>> =
        priceDao.getPricesByItem(itemId)

    suspend fun getPriceById(id: Long): Price? = priceDao.getPriceById(id)

    fun getPriceWithPayments(id: Long): Flow<DaoPriceWithPayments?> =
        priceDao.getPriceWithPayments(id)

    suspend fun insertPrice(price: Price): Long = priceDao.insertPrice(price)

    suspend fun updatePrice(price: Price) = priceDao.updatePrice(price)

    suspend fun deletePrice(price: Price) = priceDao.deletePrice(price)

    fun getPricesWithPaymentsByItem(itemId: Long): Flow<List<DaoPriceWithPayments>> =
        priceDao.getPricesWithPaymentsByItem(itemId)

    fun getTotalSpending(): Flow<Double> = priceDao.getTotalSpending()

    fun getTotalPriceByCoordinate(coordinateId: Long): Flow<Double> =
        priceDao.getTotalPriceByCoordinate(coordinateId)

    fun getPricesWithPaymentsByCoordinate(coordinateId: Long): Flow<List<DaoPriceWithPayments>> =
        priceDao.getPricesWithPaymentsByCoordinate(coordinateId)

    fun getTotalPriceByItemIds(itemIds: List<Long>): Flow<Double> =
        priceDao.getTotalPriceByItemIds(itemIds)

    fun getItemPriceSums(): Flow<List<com.lolita.app.data.local.dao.ItemPriceSum>> =
        priceDao.getItemPriceSums()
}
