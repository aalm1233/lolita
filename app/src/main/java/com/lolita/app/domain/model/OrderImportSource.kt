package com.lolita.app.domain.model

import com.google.gson.Gson
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer

/** A missing/invalid value never becomes evidence of a free order. */
sealed interface ImportMoney {
    data class Valid(val cents: Long) : ImportMoney

    data object Missing : ImportMoney

    data class Invalid(val raw: String) : ImportMoney
}

object OrderImportSource {
    fun money(raw: String): ImportMoney {
        val text = raw.trim().removePrefix("￥").removePrefix("¥").trim()
        if (text.isEmpty()) return ImportMoney.Missing
        if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?").matches(text))
            return ImportMoney.Invalid(raw)
        val cents =
            runCatching {
                    text
                        .replace(",", "")
                        .toBigDecimal()
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact()
                }
                .getOrNull()
        return if (cents != null && cents in 0..99_999_999_999L) ImportMoney.Valid(cents)
        else ImportMoney.Invalid(raw)
    }

    fun normalized(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    fun publicUrl(raw: String): String? =
        runCatching {
                val uri = URI(raw.trim())
                require(uri.scheme in setOf("https", "http") && uri.userInfo == null)
                val host = uri.host.lowercase()
                require(host == "item.taobao.com" || host == "detail.tmall.com")
                val id =
                    uri.rawQuery
                        .orEmpty()
                        .split('&')
                        .map { it.split('=', limit = 2) }
                        .firstOrNull { it.size == 2 && it[0] == "id" }
                        ?.get(1)
                require(id != null && id.matches(Regex("[0-9]{1,30}")))
                "https://$host/item.htm?id=$id"
            }
            .getOrNull()

    fun group(item: TaobaoOrderItem): String =
        hash(
            listOf(
                    item.orderId.trim(),
                    publicUrl(item.productUrl) ?: normalized(item.name),
                    normalized(item.styleSpec),
                )
                .joinToString("\u001f")
        )

    fun key(item: TaobaoOrderItem, ordinal: Int): String = "taobao:v1:${group(item)}:$ordinal"

    private fun moneyIdentity(raw: String?, value: Double): String =
        amount(raw, value)?.let { BigDecimal.valueOf(it).setScale(2).toPlainString() }
            ?: "unknown:${raw.orEmpty()}"

    private fun unitIdentity(item: TaobaoOrderItem): String =
        Gson()
            .toJson(
                listOf(
                    normalized(item.name),
                    moneyIdentity(item.rawPrice, item.price),
                    item.actualPaidDate?.trim(),
                    item.requestedRoomName,
                    item.requestedStoragePath,
                    item.specificationsJson,
                )
            )

    /** Source units stay stable when an exporter rearranges equal-product rows. */
    fun ordinals(order: TaobaoOrder): Map<Pair<Int, Int>, Int> = buildMap {
        order.items
            .flatMapIndexed { line, item ->
                List(item.quantity) { unit -> Triple(line, unit, item) }
            }
            .groupBy { group(it.third) }
            .values
            .forEach { pieces ->
                pieces
                    .sortedBy { unitIdentity(it.third) }
                    .forEachIndexed { ordinal, piece -> put(piece.first to piece.second, ordinal) }
            }
    }

    fun snapshot(order: TaobaoOrder): String =
        hash(
            Gson()
                .toJson(
                    listOf(
                        order.orderId,
                        order.orderStatus,
                        order.orderTime,
                        normalized(order.shopName),
                        moneyIdentity(order.rawPaid, order.totalPaid),
                        moneyIdentity(order.rawShipping, order.shipping),
                        order.items.groupBy(::group).toSortedMap().map { (key, rows) ->
                            listOf(
                                key,
                                rows.sumOf { it.quantity },
                                rows
                                    .flatMap { row -> List(row.quantity) { unitIdentity(row) } }
                                    .sorted(),
                            )
                        },
                    )
                )
        )

    fun amount(raw: String?, fallback: Double): Double? =
        when (val v = raw?.let(::money)) {
            null -> fallback.takeIf { it.isFinite() && it >= 0 }
            is ImportMoney.Valid -> BigDecimal.valueOf(v.cents, 2).toDouble()
            else -> null
        }
}
