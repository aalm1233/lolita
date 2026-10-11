package com.lolita.app.ui.screen.`import`

import com.lolita.app.data.model.TaobaoOrderItem
import org.junit.Assert.*
import org.junit.Test

class ImportAccountingTest {
    @Test fun allocationConservesOddCentsAndSkippedShares() {
        val shares = allocateImportMoney(0.05, listOf(1.0, 1.0, 1.0))
        assertEquals(listOf(0.02, 0.02, 0.01), shares)
        assertEquals(5L, importCents(importSum(shares)))
        // Selecting only the last piece must keep its original share, not absorb the whole fee.
        assertEquals(0.01, shares[2], 0.0)
    }

    @Test fun allocationUsesRealDiscountedPaymentAndHandlesZeroWeights() {
        val paid = allocateImportMoney(930.0, listOf(1000.0, 100.0))
        assertEquals(listOf(845.45, 84.55), paid)
        assertEquals(93000L, importCents(importSum(paid)))
        assertEquals(listOf(0.34, 0.33, 0.33), allocateImportMoney(1.0, listOf(0.0, 0.0, 0.0)))
        assertEquals(listOf(0.0, 0.0), allocateImportMoney(0.0, listOf(2.0, 3.0)))
    }

    @Test fun malformedMoneyAndDatesCannotBecomeActualPayments() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.001).forEach { amount ->
            assertTrue(runCatching { importCents(amount) }.isFailure)
        }
        assertNull(parseImportDate("2026-02-30"))
        assertNull(parseImportDate("2026-10-11 garbage"))
        assertNotNull(parseImportDate("2026-10-11 08:30:05"))
        assertFalse(isImportItemReady(0, listOf(row().copy(purchaseDate = ""))))
        assertFalse(isImportItemReady(0, listOf(row().copy(paidAmount = 100.01))))
    }

    @Test fun pairedImportRequiresBothFinancialConfirmations() {
        val deposit = row().copy(paymentRole = PaymentRole.DEPOSIT, pairedWith = 1)
        val balance = row().copy(paymentRole = PaymentRole.BALANCE, pairedWith = 0, brandId = 0, categoryId = 0)
        assertTrue(isImportItemReady(0, listOf(deposit, balance)))
        assertTrue(isImportItemReady(1, listOf(deposit, balance)))
        assertFalse(isImportItemReady(0, listOf(deposit, balance.copy(financialConfirmed = false))))
        assertFalse(isImportItemReady(1, listOf(deposit, balance.copy(financialConfirmed = false))))
        assertFalse(isImportItemReady(0, listOf(row().copy(paymentRole = PaymentRole.BALANCE))))
    }

    @Test fun futureTailCanRemainUndatedAndUnpaid() {
        val deposit = row().copy(paymentRole = PaymentRole.DEPOSIT, manualBalance = 700.0, balanceDueDate = null)
        assertTrue(isImportItemReady(0, listOf(deposit)))
        assertTrue(isImportItemReady(0, listOf(row().copy(paidAmount = 0.0, purchaseDate = ""))))
        assertFalse(isImportItemReady(0, listOf(row().copy(financialConfirmed = false))))
    }

    private fun row() = ImportItemState(
        originalItem = TaobaoOrderItem("裙子", "", "", 1, 100.0, null, null, null, "order-1", "2026-10-11", "店铺"),
        name = "裙子", brandId = 1, categoryId = 1, price = 100.0, paidAmount = 100.0,
        purchaseDate = "2026-10-11", financialConfirmed = true
    )
}
