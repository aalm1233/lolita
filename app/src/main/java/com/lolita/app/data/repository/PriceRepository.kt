package com.lolita.app.data.repository

import android.content.Context
import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.dao.PriceDao
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.OrderBinding

class PriceRepository(
    private val priceDao: PriceDao,
    private val paymentDao: PaymentDao,
    private val context: Context,
    private val database: LolitaDatabase? = null
) {
    fun getPricesByItem(itemId: Long): Flow<List<Price>> =
        priceDao.getPricesByItem(itemId)

    suspend fun getPricesByItemList(itemId: Long): List<Price> =
        priceDao.getPricesByItemList(itemId)

    suspend fun getPriceById(id: Long): Price? = priceDao.getPriceById(id)

    fun getPriceWithPayments(id: Long): Flow<DaoPriceWithPayments?> =
        priceDao.getPriceWithPayments(id)

    suspend fun insertPrice(price: Price): Long {
        val db = database ?: return priceDao.insertPrice(price)
        return db.withTransaction {
            require(db.itemDao().getItemById(price.itemId)?.includedInParentPrice != true) { "共用主件价格的小物没有独立价格" }
            require(priceDao.getPricesByItemList(price.itemId).isEmpty()) { "已有价格，请编辑或核对历史价格" }
            val id = priceDao.insertPrice(price)
            db.orderDao().insertBinding(OrderBinding(price.itemId, id, price.purchaseRecordStatus))
            id
        }
    }

    suspend fun updatePrice(price: Price) {
        error("请在价格与付款中编辑订单，保留实际付款记录")
    }

    suspend fun deletePrice(price: Price) {
        error("请将当前订单保留为历史记录，不能删除付款历史")
    }

    fun getPricesWithPaymentsByItem(itemId: Long): Flow<List<DaoPriceWithPayments>> =
        priceDao.getPricesWithPaymentsByItem(itemId)

    fun getTotalSpending(): Flow<Double> = priceDao.getTotalSpending()

    fun getAllRecordedSpending(): Flow<Double> = priceDao.getAllRecordedSpending()

    fun getIncompleteCurrentOrderCount(): Flow<Int> = priceDao.getIncompleteCurrentOrderCount()

    fun getAverageOwnedPrice(): Flow<Double> = priceDao.getAverageOwnedPrice()

    fun getTotalPriceByCoordinate(coordinateId: Long): Flow<Double> =
        priceDao.getTotalPriceByCoordinate(coordinateId)

    fun getPricesWithPaymentsByCoordinate(coordinateId: Long): Flow<List<DaoPriceWithPayments>> =
        priceDao.getPricesWithPaymentsByCoordinate(coordinateId)

    fun getTotalPriceByItemIds(itemIds: List<Long>): Flow<Double> =
        priceDao.getTotalPriceByItemIds(itemIds)

    fun getItemPriceSums(): Flow<List<com.lolita.app.data.local.dao.ItemPriceSum>> =
        priceDao.getItemPriceSums()

    fun getSpendingByBrand() = priceDao.getSpendingByBrand()
    fun getSpendingByCategory() = priceDao.getSpendingByCategory()
    fun getSpendingByStyle() = priceDao.getSpendingByStyle()
    fun getSpendingBySeasonRaw() = priceDao.getSpendingBySeasonRaw()
    fun getMonthlySpending() = priceDao.getMonthlySpending()
    fun getUndatedSpending() = priceDao.getUndatedSpending()
    fun getMostExpensiveItem() = priceDao.getMostExpensiveItem()
    fun getWishlistTotalBudget() = priceDao.getWishlistTotalBudget()
    fun getWishlistByPriorityStats() = priceDao.getWishlistByPriorityStats()

    fun getItemsByPurchaseMonth(yearMonth: String) = priceDao.getItemsByPurchaseMonth(yearMonth)

    fun getPricesWithStatusByDateRange(startDate: Long, endDate: Long, now: Long) =
        priceDao.getPricesWithStatusByDateRange(startDate, endDate, now)

    fun getPaymentsWithItemInfoByDateRange(startDate: Long, endDate: Long) =
        paymentDao.getPaymentsWithItemInfoByDateRange(startDate, endDate)
}
