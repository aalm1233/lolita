package com.lolita.app.ui.screen.price

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.OrderDetails
import com.lolita.app.data.repository.OrderRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
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
    val error: String? = null,
    val isUpdating: Boolean = false,
    val loadFailed: Boolean = false,
    val parentItemId: Long? = null,
    val needsPurchaseReview: Boolean = false,
    val itemName: String = "",
    val itemSpecification: String = "",
    val reference: ItemReferencePrice? = null
)

data class PriceEditUiState(
    val priceType: PriceType = PriceType.FULL,
    val totalPrice: String = "",
    val shipping: String = "0",
    val stages: List<StageDraft> = listOf(StageDraft(name = "全款")),
    val paidAmount: Double = 0.0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val purchaseRecordStatus: PurchaseRecordStatus = PurchaseRecordStatus.TRACKED,
    val hasRecordedPayments: Boolean = false,
    val loadFailed: Boolean = false,
    val referenceQuote: com.lolita.app.data.file.TaobaoProductQuote? = null,
    val independentReference: ItemReferencePrice? = null,
    val autoFilledTotal: Boolean = false,
    val autoFilledStageIndex: Int? = null,
    val itemName: String = "",
    val isWish: Boolean = false,
    val isSaved: Boolean = false
) {
    val payableTotal: Double? get() = parseMoney(totalPrice, true)?.let { amount -> parseMoney(shipping, true)?.let { moneySum(amount, it) } }
    val calculationHint: String? get() = when {
        autoFilledTotal && totalPrice.isNotBlank() -> "已按阶段合计减邮费补算商品金额，可直接修改。"
        autoFilledStageIndex != null && stages.getOrNull(autoFilledStageIndex)?.amount?.isNotBlank() == true ->
            "已按含邮费总额补算${stages[autoFilledStageIndex].name.ifBlank { "阶段 ${autoFilledStageIndex + 1}" }}，可直接修改。"
        else -> null
    }
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
    val errorMessage: String? = null,
    val purchaseRecordStatus: PurchaseRecordStatus = PurchaseRecordStatus.TRACKED,
    val isWorking: Boolean = false,
    val itemName: String = "",
    val cancellation: OrderCancellation? = null,
    val refundedAmount: Double = 0.0
)

data class PaymentEditUiState(
    val amount: String = "",
    val paidDate: Long? = System.currentTimeMillis(),
    val canRecordUnknownDate: Boolean = false,
    val isPaidDateUnknown: Boolean = false,
    val itemName: String = "",
    val purchaseSource: String? = null,
    val purchaseDescription: String? = null,
    val orderTotal: Double = 0.0,
    val recordedPaidAmount: Double = 0.0,
    val stageId: Long? = null,
    val stages: List<PaymentStage> = emptyList(),
    val remainingAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val purchaseRecordStatus: PurchaseRecordStatus = PurchaseRecordStatus.TRACKED,
    val loadFailed: Boolean = false,
    val suggestedAmount: Double = 0.0,
    val originalAmount: Double = 0.0,
    val stageRemaining: Map<Long, Double> = emptyMap(),
    val isSaved: Boolean = false
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
                combine(
                    orderRepository.currentOrder(parentId ?: itemId),
                    orderRepository.historicalOrders(itemId),
                    itemRepository.getPurchaseReviews(), orderRepository.observeReferencePrice(parentId ?: itemId)
                ) { order, history, reviews, reference ->
                    PriceManageUiState(
                        order, history, parentId != null, parent?.name, false,
                        parentItemId = parentId,
                        needsPurchaseReview = reviews.any { it.item.id == itemId },
                        itemName = item?.name.orEmpty(),
                        itemSpecification = listOfNotNull(item?.size?.takeIf { it.isNotBlank() }).joinToString(" / "), reference = reference
                    )
                }.collect { state ->
                    _uiState.update { state.copy(isUpdating = it.isUpdating, error = it.error) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, loadFailed = true, error = uiErrorText(e, "价格加载失败")) }
            }
        }
    }

    fun archiveCurrentOrder() = changeOrder { orderRepository.archiveCurrentOrder(itemId) }
    fun bindHistoricalOrder(priceId: Long) = changeOrder { orderRepository.bindHistoricalOrder(itemId, priceId) }
    fun confirmHistoricalPurchase() = changeOrder { orderRepository.confirmHistoricalPurchase(itemId) }
    fun confirmTrackedOrder(paymentRecordsVerified: Boolean = false) = changeOrder {
        orderRepository.confirmTrackedOrder(itemId, paymentRecordsVerified)
    }

    private fun changeOrder(action: suspend () -> Unit) {
        val state = _uiState.value
        if (state.isLoading || state.loadFailed || state.isUpdating || state.includedInParentPrice) return
        _uiState.update { it.copy(isUpdating = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = uiErrorText(e, "订单状态更新失败")) }
            } finally { _uiState.update { it.copy(isUpdating = false) } }
        }
    }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}

class PriceEditViewModel(private val orderRepository: OrderRepository = AppModule.orderRepository(),
    private val itemRepository: com.lolita.app.data.repository.ItemRepository = AppModule.itemRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val _uiState = MutableStateFlow(PriceEditUiState(isSaved = savedStateHandle["priceSaved"] ?: false))
    val uiState: StateFlow<PriceEditUiState> = _uiState.asStateFlow()
    var hasUnsavedChanges: Boolean = false
        private set
    private var originalPrice: Price? = null

    private var loadKey: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var baseline: String = ""

    fun loadPrice(priceId: Long?, itemId: Long = 0L) {
        val key = "${priceId ?: 0}:$itemId"
        if (loadKey == key || _uiState.value.isSaving || _uiState.value.isSaved) return
        loadKey = key
        loadJob?.cancel()
        if (priceId == null) {
            baseline = "new:$itemId"
            try {
                restoreDraft(key)
            } catch (e: Exception) {
                _uiState.update { it.copy(loadFailed = true, error = uiErrorText(e, "草稿暂时无法恢复，请返回后重新编辑")) }
                return
            }
            if (itemId != 0L) {
                _uiState.update { it.copy(isLoading = true) }
                loadJob = viewModelScope.launch {
                    try {
                        val item = itemRepository.getItemById(itemId) ?: error("物品不存在，请返回核对")
                        _uiState.update { it.copy(itemName = item.name, isWish = item.status == ItemStatus.WISHED, isLoading = true) }
                        val reference = orderRepository.referencePrice(itemId)
                        _uiState.update { it.copy(independentReference = reference, isLoading = false) }
                        offerReferenceQuote(item.description)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _uiState.update { it.copy(isLoading = false, loadFailed = true, error = uiErrorText(e, "物品暂时无法读取")) }
                    }
                }
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, loadFailed = false, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val order = orderRepository.getOrder(priceId).first() ?: throw IllegalStateException("订单不存在")
                originalPrice = order.price
                baseline = pricingFingerprint(order)
                val item = orderRepository.getOrderItem(priceId)
                _uiState.value = PriceEditUiState(
                    priceType = order.price.type,
                    totalPrice = moneyText(BigDecimal.valueOf(order.price.totalPrice).subtract(BigDecimal.valueOf(order.price.shipping)).toDouble()),
                    shipping = moneyText(order.price.shipping), stages = order.stages.map { it.draft() },
                    paidAmount = order.paidAmount, purchaseRecordStatus = order.purchaseRecordStatus,
                    hasRecordedPayments = order.payments.isNotEmpty(), itemName = item?.name.orEmpty(), isLoading = true
                )
                restoreDraft(key)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, loadFailed = true, error = uiErrorText(e, "加载失败")) }
            }
        }
    }

    private fun restoreDraft(key: String) {
        val draft = readPricingDraft<PriceEditorDraft>(savedStateHandle, "priceDraft") ?: return
        if (draft.key != key) return
        hasUnsavedChanges = true
        check(draft.baseline == baseline) { "原订单已变更，请返回核对后重新编辑；原付款记录保留。" }
        hasUnsavedChanges = true
        _uiState.update { it.copy(priceType = draft.type, totalPrice = draft.total, shipping = draft.shipping,
            stages = draft.stages, autoFilledTotal = draft.autoTotal, autoFilledStageIndex = draft.autoStage) }
    }

    private fun rememberDraft() {
        val state = _uiState.value
        savedStateHandle["priceDraft"] = pricingJson.toJson(PriceEditorDraft(loadKey ?: "0:0", baseline.ifBlank { "new:0" },
            state.priceType, state.totalPrice, state.shipping, state.stages, state.autoFilledTotal, state.autoFilledStageIndex))
    }

    fun loadReferenceQuote(itemId: Long) {
        if (originalPrice != null) return
        viewModelScope.launch {
            try {
                val item = itemRepository.getItemById(itemId)
                if (item != null) offerReferenceQuote(item.description)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Reference data is optional; a failed lookup must leave manual price entry available.
            }
        }
    }

    fun offerReferenceQuote(description: String) {
        if (originalPrice != null || _uiState.value.isLoading || _uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.update { it.copy(referenceQuote = com.lolita.app.data.file.TaobaoProductQuotes.fromDescription(description)) }
    }

    fun useIndependentReference() {
        val state = _uiState.value
        val reference = state.independentReference ?: return
        if (originalPrice != null || state.totalPrice.isNotBlank() || state.stages.any { it.amount.isNotBlank() }) return
        change { it.copy(totalPrice = moneyText(reference.amount), shipping = "0", autoFilledTotal = false) }
    }

    fun useReferenceQuote() {
        val state = _uiState.value
        val quote = state.referenceQuote ?: return
        if (originalPrice != null || state.totalPrice.isNotBlank()) return
        val amount = com.lolita.app.data.file.TaobaoProductQuotes.amount(quote.amount) ?: return
        change { it.copy(totalPrice = amount, autoFilledTotal = false) }
    }

    private fun change(transform: (PriceEditUiState) -> PriceEditUiState) {
        if (_uiState.value.purchaseRecordStatus != PurchaseRecordStatus.TRACKED || _uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.loadFailed || _uiState.value.isSaved) return
        hasUnsavedChanges = true
        _uiState.update { autofillPriceDraft(transform(it)) }; rememberDraft()
    }
    fun updateTotalPrice(value: String) = change { it.copy(totalPrice = value, autoFilledTotal = false) }
    fun updateShipping(value: String) = change { it.copy(shipping = value) }
    fun updatePriceType(type: PriceType) = change { state ->
        val pristine = state.stages == priceStageTemplate(state.priceType).mapIndexed { index, stage ->
            if (index == state.autoFilledStageIndex) stage.copy(amount = state.stages.getOrNull(index)?.amount.orEmpty()) else stage
        }
        if (pristine) state.copy(priceType = type, stages = priceStageTemplate(type), autoFilledStageIndex = null)
        else state.copy(priceType = type)
    }
    fun updateStage(index: Int, stage: StageDraft) = change { state ->
        if (index !in state.stages.indices) state else state.copy(
            stages = state.stages.toMutableList().also { it[index] = stage },
            autoFilledStageIndex = state.autoFilledStageIndex.takeUnless { it == index && stage.amount != state.stages[index].amount })
    }
    fun addStage() = change { it.copy(priceType = PriceType.MULTI_STAGE, stages = it.stages + StageDraft(name = "第${it.stages.size + 1}段")) }
    fun removeStage(index: Int) = change { state -> state.copy(stages = state.stages.filterIndexed { i, _ -> i != index },
        autoFilledStageIndex = state.autoFilledStageIndex?.let { if (it == index) null else if (it > index) it - 1 else it }) }
    fun applyTemplate() = change { it.copy(stages = priceStageTemplate(it.priceType), autoFilledTotal = false, autoFilledStageIndex = null) }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun isValid(): Boolean = !_uiState.value.isSaved && priceValidation(_uiState.value) == null

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
        if (_uiState.value.isSaving || _uiState.value.isSaved) return Result.failure(IllegalStateException("正在保存或已保存，请返回查看结果"))
        _uiState.update { it.copy(isSaving = true) }
        return try {
            val result = block()
            hasUnsavedChanges = false
            savedStateHandle.remove<String>("priceDraft"); savedStateHandle["priceSaved"] = true
            _uiState.update { it.copy(isSaving = false, isSaved = true) }; Result.success(result)
        }
        catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = uiErrorText(e, "保存失败")) }; Result.failure(e) }
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
                else {
                    val item = orderRepository.getOrderItem(priceId)
                    orderRepository.currentOrder(order.price.itemId).map { current ->
                    PaymentManageUiState(stages = order.stages, payments = order.payments,
                        legacyUnpaidPayments = order.legacyUnpaidPayments, isCurrent = current?.price?.id == priceId,
                        totalPrice = order.price.totalPrice, paidAmount = order.paidAmount, unpaidAmount = order.remainingAmount,
                        purchaseRecordStatus = order.purchaseRecordStatus, isLoading = false, itemName = item?.name.orEmpty(),
                        cancellation = order.cancellation, refundedAmount = order.refundedAmount)
                    }
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, exists = false, errorMessage = uiErrorText(error, "付款资料加载失败")) }
            }.collect { next ->
                _uiState.update { next.copy(errorMessage = it.errorMessage, isWorking = it.isWorking) }
            }
        }
    }
    suspend fun saveStage(draft: StageDraft): Result<Long> = runAction {
        requireStageEditing()
        orderRepository.saveStage(draft.entity(priceId))
    }
    fun deleteStage(stage: PaymentStage) { viewModelScope.launch { runAction {
        requireStageEditing()
        orderRepository.deleteStage(stage.id)
    } } }
    fun deletePayment(payment: Payment) { viewModelScope.launch { runAction {
        require(_uiState.value.purchaseRecordStatus != PurchaseRecordStatus.NEEDS_REVIEW) { "请先核对历史购买状态，再修改实付记录" }
        orderRepository.deletePayment(payment.id)
    } } }
    private fun requireStageEditing() {
        require(_uiState.value.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) { "历史付款阶段只读，请先在价格页核对并跟踪付款" }
    }
    private suspend fun <T> runAction(block: suspend () -> T): Result<T> {
        val state = _uiState.value
        if (state.isWorking) return Result.failure(IllegalStateException("正在处理，请稍候"))
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        return try {
            require(!state.isLoading && state.exists && state.isCurrent) { "请先选择并核对当前订单" }
            Result.success(block())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(errorMessage = uiErrorText(e, "操作失败")) }
            Result.failure(e)
        } finally { _uiState.update { it.copy(isWorking = false) } }
    }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}

class PaymentEditViewModel(private val orderRepository: OrderRepository = AppModule.orderRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentEditUiState(isSaved = savedStateHandle["paymentSaved"] ?: false))
    val uiState: StateFlow<PaymentEditUiState> = _uiState.asStateFlow()
    private var originalPayment: Payment? = null
    private var loadedPriceId: Long? = null
    var hasUnsavedChanges: Boolean = false
        private set
    private var loadKey: String? = null
    private var baseline = ""
    private var loadJob: kotlinx.coroutines.Job? = null
    fun loadPayment(priceId: Long, paymentId: Long?, suggestedStageId: Long? = null) {
        val key = "$priceId:${paymentId ?: 0}"
        if (loadKey == key || _uiState.value.isSaving || _uiState.value.isSaved) return
        loadKey = key
        loadJob?.cancel()
        loadedPriceId = null
        _uiState.update { it.copy(isLoading = true, loadFailed = false, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val order = orderRepository.getOrder(priceId).first() ?: throw IllegalStateException("订单不存在")
                val payment = paymentId?.let { orderRepository.getPayment(it) ?: throw IllegalStateException("付款记录不存在") }
                require(payment == null || payment.priceId == priceId) { "付款记录不属于此订单" }
                require(payment == null || payment.isPaid) { "旧未付款记录已保留，请通过付款阶段核对" }
                val item = orderRepository.getOrderItem(priceId)
                originalPayment = payment
                loadedPriceId = priceId
                baseline = pricingFingerprint(order)
                val suggested = order.stages.firstOrNull { it.id == suggestedStageId }
                val remainingByStage = com.lolita.app.domain.usecase.StageAccounting.remainingByStage(order.stages, order.payments, order.price.totalPrice)
                val suggestedRemaining = suggested?.let { remainingByStage[it.id] ?: 0.0 }
                require(suggestedStageId == null || (suggested != null && suggestedRemaining!! > 0.005)) { "该付款阶段已变更或付清，请返回核对订单。" }
                _uiState.value = PaymentEditUiState(
                    amount = payment?.amount?.let(::moneyText) ?: "",
                    paidDate = if (payment != null) payment.paidDate
                        else if (order.purchaseRecordStatus == PurchaseRecordStatus.TRACKED) System.currentTimeMillis() else null,
                    canRecordUnknownDate = order.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED ||
                        (payment != null && payment.paidDate == null),
                    isPaidDateUnknown = payment != null && payment.paidDate == null,
                    itemName = item?.name.orEmpty(), purchaseSource = item?.source?.takeIf { it.isNotBlank() },
                    purchaseDescription = item?.description?.takeIf { it.contains("淘宝订单") || it.contains("订单号") },
                    orderTotal = order.price.totalPrice, recordedPaidAmount = order.paidAmount,
                    stageId = payment?.stageId ?: suggested?.id, stages = order.stages,
                    stageRemaining = remainingByStage, suggestedAmount = suggestedRemaining ?: order.remainingAmount.coerceAtLeast(0.0), originalAmount = payment?.amount ?: 0.0,
                    remainingAmount = order.remainingAmount + (payment?.amount ?: 0.0), isLoading = true,
                    purchaseRecordStatus = order.purchaseRecordStatus
                )
                restoreDraft(key)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, loadFailed = true, error = uiErrorText(e, "加载失败")) }
            }
        }
    }
    private fun restoreDraft(key: String) {
        val draft = readPricingDraft<PaymentEditorDraft>(savedStateHandle, "paymentDraft") ?: return
        if (draft.key != key) return
        hasUnsavedChanges = true
        check(draft.baseline == baseline) { "付款资料已变更，请返回核对是否已记账，再重新编辑。" }
        require(draft.stageId == null || _uiState.value.stages.any { it.id == draft.stageId }) { "付款阶段已变更，请返回核对。" }
        hasUnsavedChanges = true
        _uiState.update { it.copy(amount = draft.amount, paidDate = draft.date, isPaidDateUnknown = draft.unknown, stageId = draft.stageId,
            suggestedAmount = draft.stageId?.let { id -> it.stageRemaining[id] ?: 0.0 } ?: it.remainingAmount) }
    }

    private fun rememberDraft() {
        val state = _uiState.value
        savedStateHandle["paymentDraft"] = pricingJson.toJson(PaymentEditorDraft(loadKey.orEmpty(), baseline,
            state.amount, state.paidDate, state.isPaidDateUnknown, state.stageId))
    }
    private fun changePayment(transform: (PaymentEditUiState) -> PaymentEditUiState) {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.loadFailed || _uiState.value.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW || _uiState.value.isSaved) return
        hasUnsavedChanges = true
        _uiState.update(transform); rememberDraft()
    }
    fun updateAmount(value: String) = changePayment { it.copy(amount = value) }
    fun fillUnrecordedAmount() {
        if (originalPayment != null) return
        changePayment { it.copy(amount = it.suggestedAmount.takeIf { value -> value > 0 }?.let(::moneyText).orEmpty()) }
    }
    fun updatePaidDate(value: Long?) = changePayment { it.copy(paidDate = value, isPaidDateUnknown = false) }
    fun updatePaidDateUnknown(unknown: Boolean) {
        if (!_uiState.value.canRecordUnknownDate) return
        changePayment { it.copy(isPaidDateUnknown = unknown, paidDate = if (unknown) null else it.paidDate) }
    }
    fun updateStageId(value: Long?) = changePayment { it.copy(stageId = value, suggestedAmount = value?.let { id -> it.stageRemaining[id] ?: 0.0 } ?: it.remainingAmount) }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun isValid(): Boolean = !_uiState.value.isSaved && paymentValidation(_uiState.value) == null
    suspend fun save(priceId: Long): Result<Long> = saving { amount, state ->
        require(loadedPriceId == priceId && originalPayment == null) { "付款订单不匹配，请返回后重试" }
        if (state.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED)
            orderRepository.recordHistoricalPayment(priceId, amount, state.paidDate, state.stageId)
        else orderRepository.recordPayment(priceId, amount, state.paidDate!!, state.stageId)
    }
    suspend fun update(paymentId: Long): Result<Unit> = saving { amount, state ->
        val payment = originalPayment ?: throw IllegalStateException("付款记录未加载")
        require(payment.id == paymentId) { "付款记录不匹配" }
        orderRepository.editPayment(payment.copy(amount = amount, paidDate = state.paidDate, stageId = state.stageId))
    }
    private suspend fun <T> saving(block: suspend (Double, PaymentEditUiState) -> T): Result<T> {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return Result.failure(IllegalStateException("正在保存或已保存，请返回查看结果"))
        _uiState.update { it.copy(isSaving = true) }
        return try {
            require(isValid()) { "请核对实付金额与可记录金额上限，并选择实际付款日期；历史日期记不清时请明确勾选。" }
            val state = _uiState.value
            val result = block(parseMoney(state.amount)!!, state)
            hasUnsavedChanges = false
            savedStateHandle.remove<String>("paymentDraft"); savedStateHandle["paymentSaved"] = true
            _uiState.update { it.copy(isSaving = false, isSaved = true) }; Result.success(result)
        } catch (e: Exception) { _uiState.update { it.copy(isSaving = false, error = uiErrorText(e, "保存失败")) }; Result.failure(e) }
    }
}
