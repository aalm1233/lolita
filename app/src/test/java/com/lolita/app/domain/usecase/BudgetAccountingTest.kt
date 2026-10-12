package com.lolita.app.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BudgetAccountingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun date(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    @Test fun monthlyCashFlowKeepsPaidPendingAndUnknownSeparate() {
        val rows = listOf(BudgetEntry(1, "主件", 100.1, date("2026-10-01"), true),
            BudgetEntry(2, "尾款", 50.2, date("2026-10-31"), false),
            BudgetEntry(3, "上月", 20.0, date("2026-09-30"), false),
            BudgetEntry(4, "未来", 30.0, date("2026-11-01"), false),
            BudgetEntry(5, "未定", 40.0, null, false), BudgetEntry(6, "旧实付", 60.0, null, true))
        val result = BudgetAccounting.summarize(YearMonth.of(2026, 10), 20000, rows, zone)
        assertEquals(10010L, result.paidCents)
        assertEquals(5020L, result.pendingCents)
        assertEquals(2000L, result.earlierPendingCents)
        assertEquals(4000L, result.undatedPendingCents)
        assertEquals(6000L, result.undatedPaidCents)
        assertEquals(2970L, result.projectedRemainingCents)
    }
    @Test fun overspendingIsNegativeAndUnsetIsNotZeroBudget() {
        val rows = listOf(BudgetEntry(1, "裙子", 100.0, date("2026-10-10"), true))
        assertEquals(-5000L, BudgetAccounting.summarize(YearMonth.of(2026, 10), 5000, rows, zone).projectedRemainingCents)
        assertNull(BudgetAccounting.summarize(YearMonth.of(2026, 10), null, rows, zone).projectedRemainingCents)
    }
    @Test fun inputUsesExactCentsAndRejectsInvalidMoney() {
        assertEquals(12345L, BudgetAccounting.parseAmount("123.45"))
        assertEquals(0L, BudgetAccounting.parseAmount("0"))
        listOf("NaN", "-1", "1.001", "1e3", "", "90000000000000").forEach { input ->
            try { BudgetAccounting.parseAmount(input); fail(input) } catch (_: IllegalArgumentException) { }
        }
    }
}
