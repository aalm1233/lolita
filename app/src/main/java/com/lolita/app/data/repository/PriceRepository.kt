package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.dao.PriceDao
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class PriceRepository(
    private val priceDao: PriceDao,
    private val paymentDao: PaymentDao
) {
    fun getPricesByItem(itemId: Long): Flow<List<Price>> =
        priceDao.getPricesByItem(itemId)

    suspend fun getPricesByItemList(itemId: Long): List<Price> =
        priceDao.getPricesByItemList(itemId)

    suspend fun getPriceById(id: Long): Price? = priceDao.getPriceById(id)

    fun getPriceWithPayments(id: Long): Flow<DaoPriceWithPayments?> =
        priceDao.getPriceWithPayments(id)

    suspend fun insertPrice(price: Price): Long {
        return priceDao.insertPrice(normalizePrice(price))
    }

    suspend fun updatePrice(price: Price) {
        priceDao.updatePrice(normalizePrice(price))
    }

    private fun normalizePrice(price: Price): Price {
        if (price.type == PriceType.DEPOSIT_BALANCE) {
            val deposit = price.deposit ?: 0.0
            val balance = price.balance ?: 0.0
            val sum = deposit + balance
            if (abs(sum - price.totalPrice) > 0.01) {
                return price.copy(totalPrice = sum)
            }
        }
        return price
    }

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
