package com.lolita.app.data.file

import java.math.BigDecimal

/** A webpage reference, never a payment or an order until the user confirms it. */
data class TaobaoProductQuote(val amount: String, val specification: String = "", val label: String = "")

object TaobaoProductQuotes {
    private const val prefix = "淘宝款式参考报价："
    fun amount(value: String): String? {
        val text = value.trim()
        if (!Regex("\\d{1,9}(?:\\.\\d{1,2})?").matches(text)) return null
        val number = text.toBigDecimalOrNull() ?: return null
        return number.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString()
    }
    fun withQuote(description: String, quote: TaobaoProductQuote?): String {
        val content = description.lines().filterNot { it.startsWith(prefix) }.joinToString("\n")
        if (quote == null) return content
        val amount = amount(quote.amount) ?: return content
        val detail = listOf(quote.label, quote.specification).filter(String::isNotBlank)
            .joinToString("；").replace('\n', ' ').replace('\r', ' ')
        return "$prefix¥$amount${if (detail.isBlank()) "" else "（$detail）"}\n$content"
    }
    fun fromDescription(description: String): TaobaoProductQuote? {
        val line = description.lines().filter { it.startsWith(prefix) }.singleOrNull() ?: return null
        val match = Regex("^${prefix}¥(\\d{1,9}(?:\\.\\d{1,2})?)(?:（([^\\r\\n]*)）)?$").matchEntire(line) ?: return null
        return amount(match.groupValues[1])?.let { TaobaoProductQuote(it, match.groupValues[2]) }
    }
}
