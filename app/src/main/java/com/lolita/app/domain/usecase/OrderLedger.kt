package com.lolita.app.domain.usecase

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Purchase invariants and transactional changes; repositories expose the data stream. */
class OrderLedger(private val database: LolitaDatabase, private val context: Context) {
    private val dao = database.orderDao()
    private val prices = database.priceDao()
    private val payments = database.paymentDao()
    private val mutex = ledgerMutex
    private suspend fun <T> mutate(block: suspend () -> T): T = mutex.withLock {
        val result = database.withTransaction {
            val previous = payments.getAllPaymentsList() + dao.getAllStages().map { stage ->
                Payment(id = -stage.id, priceId = stage.priceId, amount = stage.amount,
                    dueDate = stage.dueDate ?: 0, calendarEventId = stage.calendarEventId)
            }
            val pending = database.restoreJournalDao().get()?.previousPaymentsJson
            val allPrevious = previous + (pending?.let { Gson().fromJson(it, Array<Payment>::class.java).toList() } ?: emptyList())
            database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = Gson().toJson(allPrevious)))
            block().also { dao.refreshStatuses() }
        }
        runCatching { BackupManager(context, database).recoverPendingReminders() }
            .onFailure { Log.w("OrderRepository", "Reminder reconciliation will retry", it) }
        result
    }

    private fun validatePrice(price: Price) {
        OrderAccounting.validateAmount(price.totalPrice, true)
        OrderAccounting.validateAmount(price.shipping)
        require(price.shipping <= price.totalPrice) { "邮费不能超过应付总额" }
    }
    private fun validateStages(price: Price, stages: List<PaymentStage>) {
        require(stages.isNotEmpty()) { "请设置付款阶段" }
        stages.forEach {
            require(it.name.isNotBlank()) { "请填写阶段名称" }
            OrderAccounting.validateAmount(it.amount, true)
            require(it.customReminderDays == null || it.customReminderDays > 0) { "提醒天数必须大于0" }
        }
        require(OrderAccounting.cents(OrderAccounting.sum(stages.map { it.amount })) == OrderAccounting.cents(price.totalPrice)) { "各阶段金额合计必须等于应付总额" }
    }
    private suspend fun requireActive(priceId: Long): Price {
        val price = prices.getPriceById(priceId) ?: error("订单不存在")
        require(dao.getBinding(price.itemId)?.priceId == priceId) { "请先核对并选择当前订单" }
        return price
    }

    suspend fun createOrder(price: Price, stages: List<PaymentStage>, initialPaidDate: Long? = null): Long = mutate {
        validatePrice(price); validateStages(price, stages)
        val item = database.itemDao().getItemById(price.itemId) ?: error("服饰不存在")
        require(!item.includedInParentPrice) { "小物已包含在主件价格中" }
        require(dao.getBinding(item.id) == null && prices.getPricesByItemList(item.id).isEmpty()) { "已有价格，请编辑或核对历史价格" }
        val id = prices.insertPrice(price.copy(id = 0))
        dao.insertBinding(OrderBinding(item.id, id))
        val ids = stages.map { dao.insertStage(it.copy(id = 0, priceId = id, calendarEventId = null)) }
        if (initialPaidDate != null) payments.insertPayment(Payment(priceId = id, stageId = ids.first(), amount = stages.first().amount,
            dueDate = initialPaidDate, isPaid = true, paidDate = initialPaidDate))
        id
    }

    suspend fun updateOrder(price: Price, stages: List<PaymentStage>) = mutate {
        val oldPrice = requireActive(price.id)
        require(oldPrice.itemId == price.itemId) { "不能更换订单所属服饰" }
        validatePrice(price); validateStages(price, stages)
        val allPayments = payments.getPaymentsByPriceList(price.id)
        val actual = allPayments.filter { it.isPaid }
        require(OrderAccounting.cents(OrderAccounting.sum(actual.map { it.amount })) <= OrderAccounting.cents(price.totalPrice)) { "总价低于历史已付金额，请先核对实付" }
        val oldStages = dao.getStages(price.id)
        require(stages.filter { it.id != 0L }.map { it.id }.distinct().size == stages.count { it.id != 0L }) { "付款阶段重复" }
        stages.forEach { stage ->
            if (stage.id == 0L) dao.insertStage(stage.copy(priceId = price.id, calendarEventId = null))
            else {
                val old = oldStages.firstOrNull { it.id == stage.id } ?: error("阶段不属于当前订单")
                val stagePaid = OrderAccounting.sum(actual.filter { it.stageId == stage.id }.map { it.amount })
                require(OrderAccounting.cents(stagePaid) <= OrderAccounting.cents(stage.amount)) { "阶段金额低于历史已付" }
                dao.updateStage(stage.copy(priceId = price.id, calendarEventId = old.calendarEventId))
            }
        }
        oldStages.filter { old -> stages.none { it.id == old.id } }.forEach { old ->
            // Keep actual records, detach only the optional allocation.
            allPayments.filter { it.stageId == old.id }.forEach { payments.updatePayment(it.copy(stageId = null)) }
            dao.deleteStage(old.id)
        }
        prices.updatePrice(price)
    }

    suspend fun bindHistoricalOrder(itemId: Long, priceId: Long) = mutate {
        val item = database.itemDao().getItemById(itemId) ?: error("服饰不存在")
        require(!item.includedInParentPrice) { "小物已共用主件价格" }
        val price = prices.getPriceById(priceId) ?: error("价格不存在")
        require(price.itemId == itemId) { "价格不属于当前服饰" }
        validatePrice(price)
        payments.getPaymentsByPriceList(priceId).forEach { OrderAccounting.validateAmount(it.amount, true) }
        require(dao.getStages(priceId).all { OrderAccounting.hasCentPrecision(it.amount) && it.amount > 0 && it.amount < OrderAccounting.MAX_AMOUNT }) { "历史阶段金额无效，请先核对原始记录" }
        dao.deleteBinding(itemId); dao.insertBinding(OrderBinding(itemId, priceId))
    }

    suspend fun archiveCurrentOrder(itemId: Long) = mutate { dao.deleteBinding(itemId) }

    suspend fun saveStage(stage: PaymentStage): Long = mutate {
        val price = requireActive(stage.priceId)
        val current = dao.getStages(price.id)
        val paid = payments.getPaymentsByPriceList(price.id).filter { it.isPaid && it.stageId == stage.id }
        OrderAccounting.validateAmount(stage.amount, true)
        require(stage.name.isNotBlank() && (stage.customReminderDays == null || stage.customReminderDays > 0)) { "阶段名称或提醒天数无效" }
        require(OrderAccounting.cents(OrderAccounting.sum(paid.map { it.amount })) <= OrderAccounting.cents(stage.amount)) { "阶段金额低于已付" }
        require(OrderAccounting.cents(OrderAccounting.sum(current.filter { it.id != stage.id }.map { it.amount } + stage.amount)) <= OrderAccounting.cents(price.totalPrice)) { "阶段金额合计超过应付总额" }
        val id = if (stage.id == 0L) dao.insertStage(stage.copy(calendarEventId = null))
        else { require(current.any { it.id == stage.id }); dao.updateStage(stage.copy(calendarEventId = current.first { it.id == stage.id }.calendarEventId)); stage.id }
        val count = current.size + if (stage.id == 0L) 1 else 0
        if (price.type == PriceType.FULL && count > 1 || price.type == PriceType.DEPOSIT_BALANCE && count > 2) {
            prices.updatePrice(price.copy(type = PriceType.MULTI_STAGE))
        }
        id
    }
    suspend fun deleteStage(id: Long) = mutate {
        val stage = dao.getStage(id) ?: error("阶段不存在")
        requireActive(stage.priceId)
        payments.getPaymentsByPriceList(stage.priceId).filter { it.stageId == id }.forEach { payments.updatePayment(it.copy(stageId = null)) }
        dao.deleteStage(id)
    }
    suspend fun recordPayment(priceId: Long, amount: Double, paidDate: Long, stageId: Long? = null): Long = mutate {
        val price = requireActive(priceId); OrderAccounting.validateAmount(amount, true)
        val paid = payments.getPaymentsByPriceList(priceId).filter { it.isPaid }
        validateActual(price, paid, amount, stageId)
        payments.insertPayment(Payment(priceId = priceId, amount = amount, stageId = stageId, dueDate = paidDate, paidDate = paidDate, isPaid = true))
    }
    private suspend fun validateActual(price: Price, others: List<Payment>, amount: Double, stageId: Long?) {
        require(OrderAccounting.cents(OrderAccounting.sum(others.map { it.amount } + amount)) <= OrderAccounting.cents(price.totalPrice)) { "付款金额超过未付余额" }
        stageId?.let {
            val stage = dao.getStage(it) ?: error("阶段不存在")
            require(stage.priceId == price.id) { "阶段不属于当前订单" }
            require(OrderAccounting.cents(OrderAccounting.sum(others.filter { p -> p.stageId == it }.map { p -> p.amount } + amount)) <= OrderAccounting.cents(stage.amount)) { "付款超过阶段未付金额" }
        }
    }
    suspend fun editPayment(payment: Payment) = mutate {
        val old = payments.getPaymentById(payment.id) ?: error("付款不存在")
        require(old.priceId == payment.priceId && old.isPaid) { "仅能编辑实际付款" }
        val price = requireActive(payment.priceId); OrderAccounting.validateAmount(payment.amount, true)
        require(payment.isPaid && payment.paidDate != null) { "请填写实付日期" }
        validateActual(price, payments.getPaymentsByPriceList(price.id).filter { it.isPaid && it.id != payment.id }, payment.amount, payment.stageId)
        payments.updatePayment(payment.copy(reminderSet = false, calendarEventId = null))
    }
    suspend fun deletePayment(id: Long) = mutate {
        val old = payments.getPaymentById(id) ?: error("付款不存在")
        requireActive(old.priceId); require(old.isPaid) { "历史计划请在付款阶段中管理" }
        payments.deletePayment(old)
    }
    suspend fun reconcileReminders() {
        database.withTransaction {
            dao.refreshStatuses()
            if (database.restoreJournalDao().get() == null) {
                val previous = payments.getAllPaymentsList() + dao.getAllStages().map { stage ->
                    Payment(id=-stage.id,priceId=stage.priceId,amount=stage.amount,
                        dueDate=stage.dueDate ?: 0,calendarEventId=stage.calendarEventId)
                }
                database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson=Gson().toJson(previous)))
            }
        }
        BackupManager(context, database).recoverPendingReminders()
    }
    companion object { private val ledgerMutex = Mutex() }

}
