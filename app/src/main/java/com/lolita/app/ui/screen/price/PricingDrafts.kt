package com.lolita.app.ui.screen.price

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.repository.OrderDetails
import java.math.BigDecimal

internal val pricingJson = Gson()
internal data class PriceEditorDraft(val key: String, val baseline: String, val type: PriceType,
    val total: String, val shipping: String, val stages: List<StageDraft>, val autoTotal: Boolean, val autoStage: Int?)
internal data class PaymentEditorDraft(val key: String, val baseline: String, val amount: String,
    val date: Long?, val unknown: Boolean, val stageId: Long?)
internal inline fun <reified T> readPricingDraft(handle: SavedStateHandle, key: String): T? =
    handle.get<String>(key)?.let { runCatching { pricingJson.fromJson(it, T::class.java) }.getOrNull() }

// Only compact inputs go into saved state. Re-read the ledger and reject changed evidence on restore.
internal fun pricingFingerprint(order: OrderDetails): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(pricingJson.toJson(order.copy(payments = order.payments.sortedBy { it.id },
        legacyUnpaidPayments = order.legacyUnpaidPayments.sortedBy { it.id })).toByteArray())
    .joinToString("") { "%02x".format(it) }

internal val PriceEditorFactory = viewModelFactory {
    initializer { PriceEditViewModel(savedStateHandle = createSavedStateHandle()) }
}
internal val PaymentEditorFactory = viewModelFactory {
    initializer { PaymentEditViewModel(savedStateHandle = createSavedStateHandle()) }
}

internal fun paymentAmountError(state: PaymentEditUiState): String? {
    if (state.amount.isBlank()) return "请填写本次实际付出的金额"
    val amount = parseMoney(state.amount) ?: return "实付金额须大于0，最多两位小数，且不超过999999999.99元"
    return if (amount > state.remainingAmount + 0.005)
        "最多可记录${formatPriceAmount(state.remainingAmount.coerceAtLeast(0.0))}，当前超出${formatPriceAmount(amount - state.remainingAmount)}"
    else null
}
internal fun paymentValidation(state: PaymentEditUiState): String? = when {
    state.isLoading || state.loadFailed -> "请先读取完整付款资料"
    state.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW -> "请先核对购买状态"
    paymentAmountError(state) != null -> paymentAmountError(state)
    state.stageId != null && state.stages.none { it.id == state.stageId } -> "所选阶段已不存在，请重新选择"
    state.paidDate == null && !(state.canRecordUnknownDate && state.isPaidDateUnknown) -> "请选择实际付款日期；记不清的历史日期须明确勾选"
    else -> null
}

internal fun priceValidation(state: PriceEditUiState): String? {
    if (state.isLoading || state.loadFailed) return "请先读取完整订单资料"
    if (state.purchaseRecordStatus != PurchaseRecordStatus.TRACKED) return "历史价格与计划只读，请先核对账本"
    val total = state.payableTotal ?: return "请填写有效的商品金额和邮费，最多两位小数"
    if (total <= 0 || total > 999999999.99) return "含邮费总额须大于0且不超过999999999.99元"
    if (total + 0.005 < state.paidAmount) return "总额不能小于已录实付${formatPriceAmount(state.paidAmount)}"
    if (state.stages.isEmpty()) return "请安排付款阶段"
    val count = when (state.priceType) { PriceType.FULL -> 1; PriceType.DEPOSIT_BALANCE -> 2; PriceType.MULTI_STAGE -> null }
    if ((count != null && state.stages.size != count) || (count == null && state.stages.size < 2))
        return "${state.priceType.priceLabel()}需要${count ?: "至少2"}个阶段；已保留原计划，请调整或明确重新拆分"
    state.stages.forEachIndexed { index, stage ->
        if (stage.name.isBlank()) return "请填写阶段${index + 1}的名称"
        if (parseMoney(stage.amount) == null) return "${stage.name}金额须大于0且最多两位小数"
        if (stage.reminderSet && stage.dueDate != null && (stage.reminderDays.toIntOrNull() ?: 0) < 1)
            return "${stage.name}的提前提醒天数须大于0"
    }
    val sum = state.stages.fold(BigDecimal.ZERO) { acc, stage -> acc.add(BigDecimal.valueOf(parseMoney(stage.amount)!!)) }
    val difference = sum.subtract(BigDecimal.valueOf(total))
    return if (difference.signum() == 0) null else "阶段合计${if (difference.signum() > 0) "超出" else "还差"}${formatPriceAmount(difference.abs().toDouble())}，请核对各阶段金额"
}
