package com.lolita.app.domain.usecase

import com.google.gson.Gson
import com.lolita.app.data.local.entity.*
import java.security.MessageDigest

data class AfterSaleSnapshot(
    val item: Item, val price: Price, val payments: List<Payment>, val stages: List<PaymentStage>,
    val binding: OrderBinding?, val refunds: List<OrderRefund>, val cancellation: OrderCancellation?
) {
    val paid: Double get() = OrderAccounting.sum(payments.filter { it.isPaid }.map { it.amount })
    val refunded: Double get() = OrderAccounting.sum(refunds.filter { it.voidedAt == null }.map { it.amount })
    val net: Double get() = (OrderAccounting.cents(paid) - OrderAccounting.cents(refunded)) / 100.0
    val pendingRefund: Double? get() = cancellation?.expectedRefundTotal?.let { OrderAccounting.remaining(it, refunded) }
    val isCurrent: Boolean get() = binding?.priceId == price.id && cancellation == null
    val fingerprint: String get() {
        // Include every source which can change the allowed operation or its visible consequences.
        val normalized = copy(payments = payments.sortedBy { it.id }, stages = stages.sortedBy { it.id }, refunds = refunds.sortedBy { it.id })
        return MessageDigest.getInstance("SHA-256").digest(Gson().toJson(normalized).toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
