package com.lolita.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.usecase.*
import kotlinx.coroutines.flow.*

class AfterSaleRepository(private val database: LolitaDatabase, context: Context) {
    private val dao = database.afterSaleDao()
    private val ledger = OrderLedger(database, context)
    private val orders = OrderRepository(database, context)
    fun reference(itemId: Long) = dao.reference(itemId)
    suspend fun getReference(itemId: Long) = dao.getReference(itemId)
    suspend fun saveReference(itemId: Long, amount: Double, note: String, expected: ItemReferencePrice?) = database.withTransaction {
        OrderAccounting.validateAmount(amount)
        require(note.length <= 500) { "备注最多500字" }
        val item = database.itemDao().getItemById(itemId) ?: error("物品已不存在")
        require(!item.includedInParentPrice) { "共价小物请前往主件记录参考价" }
        val old = dao.getReference(itemId)
        require(old == expected) { "参考价已变化，请返回重新核对" }
        val value = ItemReferencePrice(itemId, amount, note.trim(), (old?.revision ?: 0) + 1)
        if (old == null) dao.insertReference(value) else dao.updateReference(value)
    }
    suspend fun removeReference(itemId: Long, expected: ItemReferencePrice) = database.withTransaction {
        require(dao.getReference(itemId) == expected) { "参考价已变化，请返回重新核对" }
        dao.deleteReference(itemId)
    }
    fun observe(priceId: Long): Flow<AfterSaleSnapshot?> = combine(
        orders.getOrder(priceId), dao.refunds(priceId), dao.cancellation(priceId),
        database.itemDao().getAllItems(), database.orderDao().observeBindings()
    ) { _, _, _, _, _ -> snapshot(priceId) }
    suspend fun snapshot(priceId: Long): AfterSaleSnapshot? = database.withTransaction {
        val price = database.priceDao().getPriceById(priceId) ?: return@withTransaction null
        val item = database.itemDao().getItemById(price.itemId) ?: return@withTransaction null
        AfterSaleSnapshot(item, price, database.paymentDao().getPaymentsByPriceList(priceId), database.orderDao().getStages(priceId),
            database.orderDao().getBinding(item.id), dao.getRefunds(priceId), dao.getCancellation(priceId))
    }
    fun totalRefunds() = dao.totalRefunds()
    suspend fun recordRefund(value: OrderRefund, fingerprint: String) = ledger.recordRefund(value, fingerprint)
    suspend fun voidRefund(id: Long, fingerprint: String) = ledger.voidRefund(id, fingerprint)
    suspend fun cancelOrder(priceId: Long, expected: Double?, note: String, fingerprint: String, confirmed: Boolean) =
        ledger.cancelOrder(priceId, expected, note, fingerprint, confirmed)
}
