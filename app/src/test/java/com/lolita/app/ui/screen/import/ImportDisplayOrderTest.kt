package com.lolita.app.ui.screen.`import`

import com.lolita.app.data.model.TaobaoOrderItem
import org.junit.Assert.*
import org.junit.Test

class ImportDisplayOrderTest {
    private fun row(role: PaymentRole? = null, peer: Int? = null) = ImportItemState(
        originalItem = TaobaoOrderItem("裙子", "", "", 1, 100.0, null, null, null, "order", "2026-10-15", "店铺"),
        name = "裙子", paymentRole = role, pairedWith = peer
    )

    @Test fun emptyAndUnpairedRowsKeepSourceOrder() {
        assertEquals(emptyList<Int>(), importDisplayOrder(emptyList()))
        assertEquals(listOf(0, 1, 2), importDisplayOrder(listOf(row(PaymentRole.BALANCE), row(), row(PaymentRole.DEPOSIT))))
    }
    @Test fun balanceBeforeDepositMovesTogetherAtEarliestSourcePosition() {
        assertEquals(listOf(2, 0, 1), importDisplayOrder(listOf(row(PaymentRole.BALANCE, 2), row(), row(PaymentRole.DEPOSIT, 0))))
    }
    @Test fun interleavedPairsStayAdjacentAndOtherRowsKeepTheirRelativeOrder() {
        val rows = listOf(row(PaymentRole.DEPOSIT, 5), row(PaymentRole.BALANCE, 4), row(),
            row(PaymentRole.DEPOSIT), row(PaymentRole.DEPOSIT, 1), row(PaymentRole.BALANCE, 0))
        assertEquals(listOf(0, 5, 4, 1, 2, 3), importDisplayOrder(rows))
    }
    @Test fun invalidPairsCannotReorderOrDropAnyRecord() {
        val rows = listOf(row(PaymentRole.DEPOSIT, 0), row(PaymentRole.BALANCE, 99),
            row(PaymentRole.DEPOSIT, 3), row(PaymentRole.BALANCE),
            row(PaymentRole.DEPOSIT, 5), row(PaymentRole.DEPOSIT, 4), row(null, 7), row(PaymentRole.BALANCE, 6))
        assertEquals(rows.indices.toList(), importDisplayOrder(rows))
    }
    @Test fun changingAndRemovingPairsDerivesFreshOrderWithoutChangingInput() {
        val rows = listOf(row(PaymentRole.BALANCE, 2), row(), row(PaymentRole.DEPOSIT, 0),
            row(PaymentRole.BALANCE, 5), row(PaymentRole.DEPOSIT), row(PaymentRole.DEPOSIT, 3))
        assertEquals(listOf(2, 0, 1, 5, 3, 4), importDisplayOrder(rows))
        val changed = rows.mapIndexed { i, r -> r.copy(pairedWith = when (i) { 0 -> 5; 5 -> 0; else -> null }) }
        assertEquals(listOf(5, 0, 1, 2, 3, 4), importDisplayOrder(changed))
        assertEquals(rows.indices.toList(), importDisplayOrder(changed.map { it.copy(pairedWith = null) }))
        assertEquals(2, rows[0].pairedWith)
        assertEquals(3, rows[5].pairedWith)
    }
    @Test fun moneyIdentityAndIncompleteDraftsRemainUntouched() {
        val rows = listOf(row(PaymentRole.BALANCE, 2).copy(priceInput = "12..", paidInput = "-", price = Double.NaN),
            row().copy(name = "", brandId = 0, categoryId = 0), row(PaymentRole.DEPOSIT, 0).copy(shippingInput = "0.", financialConfirmed = true))
        val snapshot = rows.toList()
        assertEquals(listOf(2, 0, 1), importDisplayOrder(rows))
        assertEquals(snapshot, rows)
        assertEquals("12..", rows[0].priceInput)
        assertEquals("0.", rows[2].shippingInput)
        assertTrue(rows[2].financialConfirmed)
    }
    @Test fun largeQueueContainsEverySourceExactlyOnce() {
        val count = 1000
        val rows = List(count * 2) { i ->
            if (i < count) row(PaymentRole.DEPOSIT, i + count) else row(PaymentRole.BALANCE, i - count)
        }
        val display = importDisplayOrder(rows)
        assertEquals((0 until count).flatMap { listOf(it, it + count) }, display)
        assertEquals(rows.size, display.distinct().size)
    }
}
