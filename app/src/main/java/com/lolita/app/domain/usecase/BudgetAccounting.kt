package com.lolita.app.domain.usecase

import java.math.BigDecimal
import java.time.*

data class BudgetEntry(val id: Long, val itemName: String, val amount: Double, val date: Long?, val isPaid: Boolean, val itemId: Long = 0, val priceId: Long = 0)
data class BudgetSummary(
    val paidCents: Long, val pendingCents: Long, val earlierPendingCents: Long,
    val undatedPendingCents: Long, val undatedPaidCents: Long, val projectedRemainingCents: Long?,
    val entries: List<BudgetEntry>, val undatedEntries: List<BudgetEntry> = emptyList()
)
object BudgetAccounting {
    fun parseAmount(text: String): Long {
        val input = text.trim()
        require(input.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) { "请输入非负金额，最多两位小数" }
        val amount = BigDecimal(input)
        require(amount < BigDecimal.valueOf(OrderAccounting.MAX_AMOUNT)) { "金额超出支持范围" }
        return amount.movePointRight(2).longValueExact()
    }
    fun validate(month: String, cents: Long) {
        require(month.matches(Regex("[0-9]{4}-[0-9]{2}"))) { "月份格式无效" }
        YearMonth.parse(month)
        require(cents >= 0 && cents < OrderAccounting.cents(OrderAccounting.MAX_AMOUNT)) { "预算金额无效" }
    }
    fun summarize(month: YearMonth, limit: Long?, rows: List<BudgetEntry>, zone: ZoneId = ZoneId.systemDefault()): BudgetSummary {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun sum(values: List<BudgetEntry>) = values.fold(0L) { total, entry -> Math.addExact(total, OrderAccounting.cents(entry.amount)) }
        val dated = rows.filter { it.date != null && it.date >= start && it.date < end }
        val paid = sum(dated.filter { it.isPaid })
        val pending = sum(dated.filter { !it.isPaid })
        val earlier = rows.filter { !it.isPaid && it.date != null && it.date < start }
        val carried = sum(earlier)
        return BudgetSummary(paid, pending, carried, sum(rows.filter { !it.isPaid && it.date == null }),
            sum(rows.filter { it.isPaid && it.date == null }),
            limit?.let { Math.subtractExact(Math.subtractExact(Math.subtractExact(it, paid), pending), carried) },
            (dated + earlier).sortedWith(compareBy<BudgetEntry> { it.date }.thenBy { it.id }), rows.filter { it.date == null }.sortedBy { it.itemName })
    }
}
