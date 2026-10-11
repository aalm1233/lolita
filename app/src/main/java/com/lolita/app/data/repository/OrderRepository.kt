package com.lolita.app.data.repository

import android.content.Context
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.usecase.OrderAccounting
import kotlinx.coroutines.flow.*

data class OrderDetails(val price: Price, val stages: List<PaymentStage>, val payments: List<Payment>, val legacyUnpaidPayments: List<Payment> = emptyList()) {
    val stagePaidAmounts: Map<Long, Double> get() = com.lolita.app.domain.usecase.StageAccounting.effectivePaid(stages, payments)
    val paidAmount: Double get() = OrderAccounting.sum(payments.filter { it.isPaid }.map { it.amount })
    val remainingAmount: Double get() = OrderAccounting.remaining(price.totalPrice, paidAmount)
}

/** All ledger mutations commit together. External reminders are reconciled from a durable journal. */
class OrderRepository(private val database: LolitaDatabase, private val context: Context) {
    private val dao = database.orderDao()
    private val prices = database.priceDao()
    private val payments = database.paymentDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun currentOrder(itemId: Long): Flow<OrderDetails?> = combine(
        database.itemDao().getAllItems(), dao.observeBindings()
    ) { items, bindings ->
        val item = items.firstOrNull { it.id == itemId }
        val owner = if (item?.includedInParentPrice == true) item.parentItemId else itemId
        bindings.firstOrNull { it.itemId == owner }?.priceId
    }.distinctUntilChanged().flatMapLatest { id -> if (id == null) flowOf(null) else getOrder(id) }

    fun getOrder(priceId: Long): Flow<OrderDetails?> = combine(prices.getPriceWithPayments(priceId), dao.observeStages(priceId)) { order, stages ->
        order?.let { OrderDetails(it.price, stages, it.payments.filter { p -> p.isPaid }, it.payments.filter { p -> !p.isPaid }) }
    }

    fun historicalOrders(itemId: Long): Flow<List<PriceWithPayments>> = combine(prices.observeAllPricesWithPaymentsByItem(itemId), dao.observeBindings()) { orders, bindings ->
        val active = bindings.firstOrNull { it.itemId == itemId }?.priceId
        orders.filter { it.price.id != active }
    }

    suspend fun getStage(id: Long): PaymentStage? = dao.getStage(id)
    suspend fun getPayment(id: Long): Payment? = payments.getPaymentById(id)

    private val ledger = com.lolita.app.domain.usecase.OrderLedger(database, context)
    suspend fun createOrder(price: Price, stages: List<PaymentStage>, initialPaidDate: Long? = null): Long = ledger.createOrder(price, stages, initialPaidDate)
    suspend fun updateOrder(price: Price, stages: List<PaymentStage>) = ledger.updateOrder(price, stages)
    suspend fun bindHistoricalOrder(itemId: Long, priceId: Long) = ledger.bindHistoricalOrder(itemId, priceId)
    suspend fun archiveCurrentOrder(itemId: Long) = ledger.archiveCurrentOrder(itemId)
    suspend fun saveStage(stage: PaymentStage): Long = ledger.saveStage(stage)
    suspend fun deleteStage(id: Long) = ledger.deleteStage(id)
    suspend fun recordPayment(priceId: Long, amount: Double, paidDate: Long, stageId: Long? = null): Long = ledger.recordPayment(priceId, amount, paidDate, stageId)
    suspend fun editPayment(payment: Payment) = ledger.editPayment(payment)
    suspend fun deletePayment(id: Long) = ledger.deletePayment(id)
    suspend fun reconcileReminders() = ledger.reconcileReminders()
}
