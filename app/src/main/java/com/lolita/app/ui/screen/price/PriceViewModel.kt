package com.lolita.app.ui.screen.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.OrderDetails
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

internal fun parseMoney(value: String, allowZero: Boolean = false): Double? = try {
    val amount = BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY)
    if (amount < BigDecimal.ZERO || (!allowZero && amount.signum() == 0) || amount > BigDecimal("999999999.99")) null
    else amount.toDouble()
} catch (_: Exception) { null }

internal fun moneyText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
internal fun moneySum(a: Double, b: Double): Double = BigDecimal.valueOf(a).add(BigDecimal.valueOf(b)).toDouble()

// Drafts retain persisted IDs so editing a plan cannot recreate actual payments.
data class StageDraft(
    val id: Long = 0,
    val name: String = "",
    val amount: String = "",
    val dueDate: Long? = null,
    val reminderSet: Boolean = false,
    val reminderDays: String = "1",
    val calendarEventId: Long? = null
) {
    fun entity(priceId: Long): PaymentStage = PaymentStage(
        id = id, priceId = priceId, name = name.trim(),
        amount = parseMoney(amount) ?: throw IllegalArgumentException("阶段金额须大于 0，最多两位小数"),
        dueDate = dueDate, reminderSet = reminderSet && dueDate != null,
        customReminderDays = if (reminderSet && dueDate != null) reminderDays.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("提醒天数必须大于 0") else null,
        calendarEventId = calendarEventId
    )
}

private fun PaymentStage.draft() = StageDraft(id, name, moneyText(amount), dueDate, reminderSet, customReminderDays?.toString() ?: "1", calendarEventId)

data class PriceManageUiState(
    val order: OrderDetails? = null,
    val historicalOrders: List<PriceWithPayments> = emptyList(),
    val includedInParentPrice: Boolean = false,
    val parentName: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class PriceEditUiState(
    val priceType: PriceType = PriceType.FULL,
    val totalPrice: String = "",
    val shipping: String = "0",
    val stages: List<StageDraft> = listOf(StageDraft(name = "全款")),
    val paidAmount: Double = 0.0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val payableTotal: Double? get() = parseMoney(totalPrice, true)?.let { amount -> parseMoney(shipping, true)?.let { moneySum(amount, it) } }
}

data class PaymentManageUiState(
    val stages: List<PaymentStage> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val legacyUnpaidPayments: List<Payment> = emptyList(),
    val isCurrent: Boolean = true,
    val totalPrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val exists: Boolean = true,
    val errorMessage: String? = null
)

data class PaymentEditUiState(
    val amount: String = "",
    val paidDate: Long? = System.currentTimeMillis(),
    val stageId: Long? = null,
    val stages: List<PaymentStage> = emptyList(),
    val remainingAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

class PriceManageViewModel(
    private val orderRepository: OrderRepository = AppModule.orderRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val itemId: Long
) : ViewModel() {
    private val _uiState = MutableStateFlow(PriceManageUiState())
    val uiState: StateFlow<PriceManageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val item = itemRepository.getItemById(itemId)
                val parentId = item?.parentItemId?.takeIf { item.includedInParentPrice }
                val parent = parentId?.let { itemRepository.getItemById(it) }
                combine(orderRepository.currentOrder(parentId ?: itemId), orderRepository.historicalOrders(itemId)) { order, history ->
                    PriceManageUiState(order, history, parentId != null, parent?.name, false)
                }.collect { state -> _uiState.value = state }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message ?: "价格加载失败") } }
        }
    }

    fun archiveCurrentOrder() {
        viewModelScope.launch {
            try { orderRepository.archiveCurrentOrder(itemId) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message ?: "归档失败") } }
        }
    }
    fun bindHistoricalOrder(priceId: Long) {
        viewModelScope.launch {
            try { orderRepository.bindHistoricalOrder(itemId, priceId) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message ?: "选择订单失败") } }
        }
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}

class PriceEditViewModel(private val orderRepository: OrderRepository = AppModule.orderRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(PriceEditUiState())
    val uiState: StateFlow<PriceEditUiState> = _uiState.asStateFlow()
    var hasUnsavedChanges: Boolean = false
        private set
    private var originalPrice: Price? = null

    fun loadPrice(priceId: Long?) {
        if (priceId == null) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val order = orderRepository.getOrder(priceId).first() ?: throw IllegalStateException("订单不存在")
                originalPrice = order.price
                _uiState.value = PriceEditUiState(
                    priceType = order.price.type,
                    totalPrice = moneyText(BigDecimal.valueOf(order.price.totalPrice).subtract(BigDecimal.valueOf(order.price.shipping)).toDouble()),
                    shipping = moneyText(order.price.shipping),
                    stages = order.stages.map { it.draft() },
                    paidAmount = order.paidAmount
                )
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") } }
        }
    }

    private fun change(transform: (PriceEditUiState) -> PriceEditUiState) { hasUnsavedChanges = true; _uiState.update(transform) }
    fun updateTotalPrice(value: String) = change { it.copy(totalPrice = value) }
    fun updateShipping(value: String) = change { it.copy(shipping = value) }
    fun updatePriceType(type: PriceType) = change { it.copy(priceType = type) }
    fun updateStage(index: Int, stage: StageDraft) = change { it.copy(stages = it.stages.toMutableList().also { list -> list[index] = stage }) }
    fun addStage() = change { it.copy(priceType = PriceType.MULTI_STAGE, stages = it.stages + StageDraft(name = "第${it.stages.size + 1}段")) }
    fun removeStage(index: Int) = change { it.copy(stages = it.stages.filterIndexed { i, _ -> i != index }) }
    fun applyTemplate() = change { state ->
        val total = state.payableTotal?.let(::moneyText) ?: ""
        state.copy(stages = when (state.priceType) {
            PriceType.FULL -> listOf(StageDraft(name = "全款", amount = total))
            PriceType.DEPOSIT_BALANCE -> listOf(StageDraft(name = "定金"), StageDraft(name = "尾款"))
            PriceType.MULTI_STAGE -> listOf(StageDraft(name = "第1段"), StageDraft(name = "第2段"))
        })
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun isValid(): Boolean {
        val state = _uiState.value
        val total = state.payableTotal ?: return false
        if (state.isLoading || total <= 0 || total + 0.005 < state.paidAmount) return false
        if (state.stages.any { it.name.isBlank() || parseMoney(it.amount) == null || (it.reminderSet && it.dueDate != null && (it.reminderDays.toIntOrNull() ?: 0) < 1) }) return false
        if (state.stages.isEmpty()) return false
        if (state.priceType == PriceType.FULL && state.stages.size != 1) return false
        if (state.priceType == PriceType.DEPOSIT_BALANCE && state.stages.size != 2) return false
        if (state.priceType == PriceType.MULTI_STAGE && state.stages.size < 2) return false
        val sum = state.stages.fold(BigDecimal.ZERO) { acc, stage -> acc.add(BigDecimal.valueOf(parseMoney(stage.amount)!!)) }
        return sum.compareTo(BigDecimal.valueOf(total)) == 0
    }

    suspend fun save(itemId: Long): Result<Long> = saving {
        val (price, stages) = build(itemId)
        orderRepository.createOrder(price, stages)
    }
    suspend fun update(priceId: Long): Result<Unit> = saving {
        val original = originalPrice ?: throw IllegalStateException("订单未加载完成")
        check(original.id == priceId) { "订单不匹配" }
        val (price, stages) = build(original.itemId)
        orderRepository.updateOrder(price, stages)
    }
    private fun build(itemId: Long): Pair<Price, List<PaymentStage>> {
        require(isValid()) { "请核对金额、阶段合计和提醒天数；应付总额不能小于已付金额" }
        val state = _uiState.value
        val shipping = parseMoney(state.shipping, true)!!
        val price = (originalPrice ?: Price(itemId = itemId, type = state.priceType, totalPrice = state.payableTotal!!)).copy(
            type = state.priceType, totalPrice = state.payableTotal!!, shipping = shipping,
            deposit = if (state.priceType == PriceType.DEPOSIT_BALANCE) state.stages.firstOrNull()?.amount?.let { parseMoney(it) } else null,
            balance = if (state.priceType == PriceType.DEPOSIT_BALANCE) state.stages.drop(1).sumOf { parseMoney(it.amount) ?: 0.0 } else null
        )
        return price to state.stages.map { it.entity(price.id) }
    }
    private suspend fun <T> saving(block: suspend () -> T): Result<T> {
        if (_uiState.value.isSaving) return Result.failure(IllegalStateException("正在保存"))
        _uiState.update { it.copy(isSaving = true) }
        return try { val result = block(); hasUnsavedChanges = false; _uiState.update { it.copy(isSaving = false) }; Result.success(result) }
        catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = e.message ?: "保存失败") }; Result.failure(e) }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaymentManageViewModel(private val orderRepository: OrderRepository = AppModule.orderRepository(), private val priceId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentManageUiState())
    val uiState: StateFlow<PaymentManageUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            orderRepository.getOrder(priceId).flatMapLatest { order ->
                if (order == null) flowOf(PaymentManageUiState(isLoading = false, exists = false))
                else orderRepository.currentOrder(order.price.itemId).map { current ->
                    PaymentManageUiState(stages = order.stages, payments = order.payments,
                        legacyUnpaidPayments = order.legacyUnpaidPayments, isCurrent = current?.price?.id == priceId,
                        totalPrice = order.price.totalPrice, paidAmount = order.paidAmount, unpaidAmount = order.remainingAmount, isLoading = false)
                }
            }.catch { error -> _uiState.update { it.copy(isLoading = false, errorMessage = error.message) } }.collect { next ->
                _uiState.update { next.copy(errorMessage = it.errorMessage) }
            }
        }
    }
    suspend fun saveStage(draft: StageDraft): Result<Long> = runAction { orderRepository.saveStage(draft.entity(priceId)) }
    fun deleteStage(stage: PaymentStage) { viewModelScope.launch { runAction { orderRepository.deleteStage(stage.id) } } }
    fun deletePayment(payment: Payment) { viewModelScope.launch { runAction { orderRepository.deletePayment(payment.id) } } }
    private suspend fun <T> runAction(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message ?: "操作失败") }; Result.failure(e) }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}

class PaymentEditViewModel(private val orderRepository: OrderRepository = AppModule.orderRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentEditUiState())
    val uiState: StateFlow<PaymentEditUiState> = _uiState.asStateFlow()
    private var originalPayment: Payment? = null
    var hasUnsavedChanges: Boolean = false
        private set
    fun loadPayment(priceId: Long, paymentId: Long?) {
        viewModelScope.launch {
            try {
                val order = orderRepository.getOrder(priceId).first() ?: throw IllegalStateException("订单不存在")
                val payment = paymentId?.let { orderRepository.getPayment(it) ?: throw IllegalStateException("付款记录不存在") }
                require(payment == null || payment.priceId == priceId) { "付款记录不属于此订单" }
                require(payment == null || payment.isPaid) { "旧未付款记录已保留，请通过付款阶段核对" }
                originalPayment = payment
                _uiState.value = PaymentEditUiState(
                    amount = payment?.amount?.let(::moneyText) ?: "", paidDate = if (payment == null) System.currentTimeMillis() else payment.paidDate,
                    stageId = payment?.stageId, stages = order.stages,
                    remainingAmount = order.remainingAmount + (payment?.amount ?: 0.0), isLoading = false
                )
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") } }
        }
    }
    fun updateAmount(value: String) { hasUnsavedChanges = true; _uiState.update { it.copy(amount = value) } }
    fun updatePaidDate(value: Long?) { hasUnsavedChanges = true; _uiState.update { it.copy(paidDate = value) } }
    fun updateStageId(value: Long?) { hasUnsavedChanges = true; _uiState.update { it.copy(stageId = value) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun isValid(): Boolean = _uiState.value.let { !it.isLoading && it.paidDate != null && parseMoney(it.amount)?.let { amount -> amount <= it.remainingAmount + 0.005 } == true }
    suspend fun save(priceId: Long): Result<Long> = saving { amount, state -> orderRepository.recordPayment(priceId, amount, state.paidDate!!, state.stageId) }
    suspend fun update(paymentId: Long): Result<Unit> = saving { amount, state ->
        val payment = originalPayment ?: throw IllegalStateException("付款记录未加载")
        require(payment.id == paymentId) { "付款记录不匹配" }
        orderRepository.editPayment(payment.copy(amount = amount, paidDate = state.paidDate, stageId = state.stageId))
    }
    private suspend fun <T> saving(block: suspend (Double, PaymentEditUiState) -> T): Result<T> {
        if (_uiState.value.isSaving) return Result.failure(IllegalStateException("正在保存"))
        _uiState.update { it.copy(isSaving = true) }
        return try {
            require(isValid()) { "实付金额须大于 0、最多两位小数，且不能超过可付余额；请选择实际付款日期" }
            val state = _uiState.value
            val result = block(parseMoney(state.amount)!!, state)
            hasUnsavedChanges = false; _uiState.update { it.copy(isSaving = false) }; Result.success(result)
        } catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = e.message ?: "保存失败") }; Result.failure(e) }
    }
}
