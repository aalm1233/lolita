package com.lolita.app.ui.screen.price

import com.lolita.app.data.local.entity.PriceType
import java.math.BigDecimal

internal fun priceStageTemplate(type: PriceType): List<StageDraft> = when (type) {
    PriceType.FULL -> listOf(StageDraft(name = "全款"))
    PriceType.DEPOSIT_BALANCE -> listOf(StageDraft(name = "定金"), StageDraft(name = "尾款"))
    PriceType.MULTI_STAGE -> listOf(StageDraft(name = "第1段"), StageDraft(name = "第2段"))
}

/** A draft has at most one calculated amount. Manual amounts never become balancing variables. */
internal fun autofillPriceDraft(draft: PriceEditUiState): PriceEditUiState {
    val shapeMatches = when (draft.priceType) {
        PriceType.FULL -> draft.stages.size == 1
        PriceType.DEPOSIT_BALANCE -> draft.stages.size == 2
        PriceType.MULTI_STAGE -> draft.stages.size >= 2
    }
    // Choosing another type does not replace a manual plan. Keep its amounts until template confirmation.
    if (!shapeMatches) return draft.copy(autoFilledTotal = false, autoFilledStageIndex = null)
    // Remove the last calculated value before considering inputs, so stale output cannot become input.
    val state = draft.copy(
        totalPrice = if (draft.autoFilledTotal) "" else draft.totalPrice,
        stages = draft.stages.mapIndexed { index, stage -> if (index == draft.autoFilledStageIndex) stage.copy(amount = "") else stage }
    )
    val shipping = parseMoney(state.shipping, true)?.let(BigDecimal::valueOf) ?: return state
    val missing = state.stages.indices.filter { state.stages[it].amount.isBlank() }
    val amounts = state.stages.map { parseMoney(it.amount)?.let(BigDecimal::valueOf) }
    if (state.totalPrice.isBlank() && missing.isEmpty() && amounts.all { it != null }) {
        val total = amounts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add).subtract(shipping)
        val text = total.stripTrailingZeros().toPlainString()
        if (parseMoney(text) != null) return state.copy(totalPrice = text, autoFilledTotal = true, autoFilledStageIndex = null)
    } else if (!state.autoFilledTotal && missing.size == 1) {
        val total = parseMoney(state.totalPrice, true)?.let(BigDecimal::valueOf) ?: return state
        if (amounts.indices.any { it != missing.single() && amounts[it] == null }) return state
        val remainder = total.add(shipping).subtract(amounts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add))
        val text = remainder.stripTrailingZeros().toPlainString()
        if (parseMoney(text) != null) return state.copy(stages = state.stages.mapIndexed { index, stage ->
            if (index == missing.single()) stage.copy(amount = text) else stage
        }, autoFilledStageIndex = missing.single())
    }
    return state
}
