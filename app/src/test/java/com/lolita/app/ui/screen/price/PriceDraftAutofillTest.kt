package com.lolita.app.ui.screen.price

import com.lolita.app.data.local.entity.PriceType
import org.junit.Assert.*
import org.junit.Test

class PriceDraftAutofillTest {
    private fun split(total: String = "680", deposit: String = "200", balance: String = "", shipping: String = "10") =
        PriceEditUiState(priceType = PriceType.DEPOSIT_BALANCE, totalPrice = total, shipping = shipping,
            stages = listOf(StageDraft(name = "定金", amount = deposit), StageDraft(name = "尾款", amount = balance)))

    @Test fun fullPaymentTracksPriceAndShippingWithoutFloatingPointLoss() {
        val full = autofillPriceDraft(PriceEditUiState(totalPrice = "0.1", shipping = "0.2"))
        assertEquals("0.3", full.stages.single().amount)
        assertEquals("0.6", autofillPriceDraft(full.copy(totalPrice = "0.4")).stages.single().amount)
    }
    @Test fun depositAndBalanceCanEachBeTheMissingAmount() {
        assertEquals("490", autofillPriceDraft(split()).stages[1].amount)
        assertEquals("200", autofillPriceDraft(split(deposit = "", balance = "490")).stages[0].amount)
    }
    @Test fun generatedBalanceRecalculatesAfterDepositOrShippingChanges() {
        val state = autofillPriceDraft(split())
        assertEquals("480", autofillPriceDraft(state.copy(shipping = "0")).stages[1].amount)
        val changed = state.copy(stages = listOf(state.stages[0].copy(amount = "210"), state.stages[1]))
        assertEquals("480", autofillPriceDraft(changed).stages[1].amount)
    }
    @Test fun completeStagesInferProductPriceExcludingShipping() {
        val state = autofillPriceDraft(split(total = "", balance = "490"))
        assertEquals("680", state.totalPrice)
        assertTrue(state.autoFilledTotal)
        assertEquals("670", autofillPriceDraft(state.copy(shipping = "20")).totalPrice)
    }
    @Test fun manualAmountsRemainIntactWhenTheyConflict() {
        val state = split(balance = "400")
        assertEquals(state, autofillPriceDraft(state))
        assertEquals("400", autofillPriceDraft(state.copy(totalPrice = "900")).stages[1].amount)
    }
    @Test fun multipleMissingStagesAreNotGuessed() {
        assertTrue(autofillPriceDraft(split(deposit = "")).stages.all { it.amount.isBlank() })
    }
    @Test fun multiStageUsesTheOnlyMissingAmount() {
        val state = split().copy(priceType = PriceType.MULTI_STAGE,
            stages = listOf(StageDraft(amount = "100.01"), StageDraft(amount = ""), StageDraft(amount = "200.02")))
        assertEquals("389.97", autofillPriceDraft(state).stages[1].amount)
    }
    @Test fun invalidPrecisionNegativeRemainderAndZeroRemainderRemainUnknown() {
        listOf(split(deposit = "690"), split(deposit = "700"), split(deposit = "200.001"),
            split(total = "-1"), split(shipping = "?"), split(total = "999999999.99", shipping = "999999999.99"))
            .forEach { assertEquals("", autofillPriceDraft(it).stages[1].amount) }
    }
    @Test fun invalidInputsClearPreviouslyCalculatedOutputWithoutUsingItToInferTotal() {
        val state = autofillPriceDraft(split())
        val cleared = autofillPriceDraft(state.copy(totalPrice = ""))
        assertEquals("", cleared.totalPrice)
        assertEquals("", cleared.stages[1].amount)
        assertEquals("490", autofillPriceDraft(cleared.copy(totalPrice = "680")).stages[1].amount)
    }
    @Test fun inferredTotalClearsWhenARequiredStageIsMissing() {
        val state = autofillPriceDraft(split(total = "", balance = "490"))
        val next = autofillPriceDraft(state.copy(stages = listOf(state.stages[0].copy(amount = ""), state.stages[1])))
        assertEquals("", next.totalPrice)
        assertEquals("", next.stages[0].amount)
    }
    @Test fun datesIdsAndRemindersArePreservedByCalculation() {
        val draft = split().copy(stages = listOf(StageDraft(id = 7, name = "定金", amount = "200", dueDate = 1234, reminderSet = true),
            StageDraft(id = 8, name = "尾款", dueDate = 5678, reminderSet = true)))
        val result = autofillPriceDraft(draft)
        assertEquals(draft.stages[0], result.stages[0])
        assertEquals(draft.stages[1].copy(amount = "490"), result.stages[1])
    }
    @Test fun incompatiblePlanShapeDoesNotGuess() {
        assertEquals("", autofillPriceDraft(split().copy(priceType = PriceType.FULL)).stages[1].amount)
        val inferred = autofillPriceDraft(split(total = "", balance = "490"))
        val changed = autofillPriceDraft(inferred.copy(priceType = PriceType.FULL))
        assertEquals("680", changed.totalPrice)
        assertFalse(changed.autoFilledTotal)
    }
}
