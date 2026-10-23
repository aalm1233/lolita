package com.lolita.app.ui.screen.price

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.gson.Gson
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.AfterSaleRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.AfterSaleSnapshot
import com.lolita.app.domain.usecase.OrderAccounting
import com.lolita.app.ui.screen.common.uiErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AfterSaleEditorMode { REFERENCE, REFUND, CANCEL }
data class AfterSaleEditorState(
    val mode: AfterSaleEditorMode = AfterSaleEditorMode.REFERENCE,
    val itemName: String = "", val amount: String = "", val note: String = "",
    val date: Long = System.currentTimeMillis(), val kind: RefundKind = RefundKind.REFUND,
    val confirmed: Boolean = false, val expectedUnknown: Boolean = false, val snapshot: AfterSaleSnapshot? = null,
    val reference: ItemReferencePrice? = null,
    val loading: Boolean = true, val saving: Boolean = false, val failed: Boolean = false,
    val saved: Boolean = false, val dirty: Boolean = false, val error: String? = null
) {
    val amountError: String? get() {
        if (mode == AfterSaleEditorMode.CANCEL && expectedUnknown) return null
        val value = parseMoney(amount, mode != AfterSaleEditorMode.REFUND) ?: return "请输入有效金额，最多两位小数" + if (mode == AfterSaleEditorMode.REFUND) "且大于0" else "（可为0）"
        val data = snapshot ?: return null
        return when {
            mode == AfterSaleEditorMode.REFUND && OrderAccounting.cents(value) > OrderAccounting.cents(data.net) -> "超过可退余额 ${formatPriceAmount(value - data.net)}"
            mode == AfterSaleEditorMode.CANCEL && (OrderAccounting.cents(value) < OrderAccounting.cents(data.refunded) || OrderAccounting.cents(value) > OrderAccounting.cents(data.paid)) -> "约定总额须介于已退回 ${formatPriceAmount(data.refunded)} 与原实付 ${formatPriceAmount(data.paid)} 之间"
            else -> null
        }
    }
    val valid: Boolean get() = !loading && !saving && !failed && !saved && amountError == null && note.length <= 500 &&
        (mode != AfterSaleEditorMode.CANCEL || confirmed) && (mode != AfterSaleEditorMode.REFUND || date in 1..System.currentTimeMillis())
}

class AfterSaleEditorViewModel(
    private val repository: AfterSaleRepository = AppModule.afterSaleRepository(),
    private val items: ItemRepository = AppModule.itemRepository(),
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _state = MutableStateFlow(AfterSaleEditorState(saved = savedState["saved"] ?: false))
    val state = _state.asStateFlow()
    private var loaded: String? = null
    private var target: Long = 0
    private var baseline = ""
    private val operation = savedState.get<String>("operation") ?: UUID.randomUUID().toString().also { savedState["operation"] = it }
    fun load(mode: AfterSaleEditorMode, id: Long) {
        val key = "$mode:$id"
        if (loaded != null || _state.value.saved) return
        loaded = key; target = id
        viewModelScope.launch {
            try {
                val snapshot = if (mode != AfterSaleEditorMode.REFERENCE) repository.snapshot(id) ?: error("订单已不存在") else null
                val item = snapshot?.item ?: items.getItemById(id) ?: error("物品已不存在")
                val reference = if (mode == AfterSaleEditorMode.REFERENCE) repository.getReference(id) else null
                require(mode != AfterSaleEditorMode.REFERENCE || !item.includedInParentPrice) { "共价小物请前往主件记录参考价" }
                require(mode != AfterSaleEditorMode.CANCEL || snapshot?.cancellation != null ||
                    (snapshot?.isCurrent == true && snapshot.binding?.purchaseRecordStatus == PurchaseRecordStatus.TRACKED)) { "请先核对并选择当前订单" }
                baseline = snapshot?.fingerprint ?: Gson().toJson(reference)
                val dirty = savedState.get<Boolean>("dirty") ?: false
                val restored = AfterSaleEditorState(mode = mode, itemName = item.name, snapshot = snapshot, reference = reference,
                    amount = if (dirty) savedState["amount"] ?: "" else reference?.amount?.let(::moneyText) ?: snapshot?.cancellation?.takeIf { mode == AfterSaleEditorMode.CANCEL }?.expectedRefundTotal?.let(::moneyText).orEmpty(),
                    note = if (dirty) savedState["note"] ?: "" else reference?.note ?: snapshot?.cancellation?.takeIf { mode == AfterSaleEditorMode.CANCEL }?.note.orEmpty(),
                    date = savedState["date"] ?: System.currentTimeMillis(), kind = savedState.get<String>("kind")?.let(RefundKind::valueOf) ?: RefundKind.REFUND,
                    confirmed = savedState["confirmed"] ?: false, expectedUnknown = if (dirty) savedState["unknown"] ?: false else mode == AfterSaleEditorMode.CANCEL && snapshot?.cancellation?.expectedRefundTotal == null && snapshot?.cancellation != null, dirty = dirty, loading = false)
                _state.value = restored.copy(loading = true)
                require(!dirty || (savedState.get<String>("key") == key && savedState.get<String>("baseline") == baseline)) { "资料已变化，已保留输入，请返回重新核对后编辑" }
                _state.value = restored
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(mode = mode, loading = false, failed = true, error = uiErrorText(e, "资料暂时无法读取，请返回重试")) }
            }
        }
    }
    private fun change(block: (AfterSaleEditorState) -> AfterSaleEditorState) {
        if (_state.value.loading || _state.value.saving || _state.value.saved || _state.value.failed) return
        val state = block(_state.value).copy(dirty = true, error = null)
        _state.value = state
        savedState["dirty"] = true; savedState["key"] = loaded; savedState["baseline"] = baseline
        savedState["amount"] = state.amount; savedState["note"] = state.note; savedState["date"] = state.date
        savedState["unknown"] = state.expectedUnknown; savedState["kind"] = state.kind.name; savedState["confirmed"] = state.confirmed
    }
    fun amount(value: String) = change { it.copy(amount = value) }
    fun note(value: String) = change { it.copy(note = value.take(500)) }
    fun date(value: Long?) { if (value != null) change { it.copy(date = value) } }
    fun kind(value: RefundKind) = change { it.copy(kind = value) }
    fun unknown(value: Boolean) = change { it.copy(expectedUnknown = value) }
    fun confirm(value: Boolean) = change { it.copy(confirmed = value) }
    fun save() {
        val state = _state.value
        if (!state.valid) return
        runSave {
            val amount = if (state.mode == AfterSaleEditorMode.CANCEL && state.expectedUnknown) null else requireNotNull(parseMoney(state.amount, state.mode != AfterSaleEditorMode.REFUND))
            when (state.mode) {
                AfterSaleEditorMode.REFERENCE -> repository.saveReference(target, requireNotNull(amount), state.note, state.reference)
                AfterSaleEditorMode.REFUND -> repository.recordRefund(OrderRefund(priceId = target, amount = requireNotNull(amount), kind = state.kind,
                    receivedAt = state.date, note = state.note, operationId = operation), baseline)
                AfterSaleEditorMode.CANCEL -> repository.cancelOrder(target, amount, state.note, baseline, state.confirmed)
            }
        }
    }
    fun removeReference() {
        val state = _state.value
        if (state.loading || state.failed || state.saving || state.saved || state.mode != AfterSaleEditorMode.REFERENCE) return
        val old = state.reference ?: return
        runSave { repository.removeReference(target, old) }
    }
    private fun runSave(block: suspend () -> Unit) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { block(); savedState["saved"] = true; _state.update { it.copy(saved = true, dirty = false) } }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = uiErrorText(e, "保存未完成，请核对后重试")) } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }
}

object AfterSaleEditorFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        AfterSaleEditorViewModel(savedState = extras.createSavedStateHandle()) as T
}

data class AfterSaleUiState(val snapshot: AfterSaleSnapshot? = null, val loading: Boolean = true, val working: Boolean = false, val error: String? = null)
class AfterSaleViewModel(private val priceId: Long, private val repository: AfterSaleRepository = AppModule.afterSaleRepository()) : ViewModel() {
    private val _state = MutableStateFlow(AfterSaleUiState())
    val state = _state.asStateFlow()
    init { viewModelScope.launch {
        try { repository.observe(priceId).collect { snapshot -> _state.update { it.copy(snapshot = snapshot, loading = false) } } }
        catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(loading = false, error = uiErrorText(e, "售后资料暂时无法读取")) } }
    } }
    fun voidRefund(id: Long, fingerprint: String) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try { repository.voidRefund(id, fingerprint) }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = uiErrorText(e, "更正未完成，请重新核对")) } }
            finally { _state.update { it.copy(working = false) } }
        }
    }
}
class AfterSaleFactory(private val priceId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AfterSaleViewModel(priceId) as T
}
