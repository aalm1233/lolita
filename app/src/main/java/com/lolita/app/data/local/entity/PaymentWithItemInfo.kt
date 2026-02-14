package com.lolita.app.data.local.entity

data class PaymentWithItemInfo(
    val paymentId: Long,
    val amount: Double,
    val dueDate: Long,
    val isPaid: Boolean,
    val paidDate: Long?,
    val priceType: PriceType,
    val itemName: String,
    val itemId: Long
)
