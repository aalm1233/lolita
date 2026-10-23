package com.lolita.app.data.file

import com.lolita.app.domain.usecase.OrderAccounting

internal object AfterSaleBackupValidation {
    fun validate(data: BackupData) {
        fun unique(keys: List<Any>, label: String) { require(keys.distinct().size == keys.size) { "$label 重复" } }
        val items = data.items.map { it.id }.toSet()
        val prices = data.prices.map { it.id }.toSet()
        unique(data.referencePrices.map { it.itemId }, "参考价")
        data.referencePrices.forEach {
            require(it.itemId in items && it.note.length <= 500 && it.revision > 0 && it.updatedAt > 0) { "参考价记录或关联无效" }
            OrderAccounting.validateAmount(it.amount)
        }
        unique(data.orderRefunds.map { it.id }, "退款记录")
        unique(data.orderRefunds.map { it.operationId }, "退款操作")
        data.orderRefunds.forEach {
            require(it.id > 0 && it.priceId in prices && it.receivedAt > 0 && it.createdAt > 0 &&
                (it.voidedAt == null || it.voidedAt >= it.createdAt) && it.note.length <= 500 &&
                it.operationId.isNotBlank() && it.operationId.length <= 100) { "退款记录或关联无效" }
            OrderAccounting.validateAmount(it.amount, true)
        }
        val paid = data.payments.filter { it.isPaid }.groupBy { it.priceId }
        val refunded = data.orderRefunds.filter { it.voidedAt == null }.groupBy { it.priceId }
        refunded.forEach { (id, values) ->
            require(OrderAccounting.cents(OrderAccounting.sum(values.map { it.amount })) <=
                OrderAccounting.cents(OrderAccounting.sum(paid[id].orEmpty().map { it.amount }))) { "退款超过原实付" }
        }
        unique(data.orderCancellations.map { it.priceId }, "取消订单")
        data.orderCancellations.forEach {
            it.expectedRefundTotal?.let { amount -> OrderAccounting.validateAmount(amount) }
            require(it.priceId in prices && it.cancelledAt > 0 && it.revision > 0 && it.note.length <= 500 &&
                data.orderBindings.none { b -> b.priceId == it.priceId } &&
                (it.expectedRefundTotal == null || OrderAccounting.cents(it.expectedRefundTotal) <= OrderAccounting.cents(OrderAccounting.sum(paid[it.priceId].orEmpty().map { p -> p.amount })))) {
                "已取消订单或约定退款无效"
            }
        }
    }
}
