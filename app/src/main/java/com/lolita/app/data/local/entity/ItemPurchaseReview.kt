package com.lolita.app.data.local.entity

import androidx.room.Embedded

/** A read-only review projection. Ownership and record completeness are separate facts. */
data class ItemPurchaseReview(
    @Embedded val item: Item,
    val orderItemId: Long,
    val priceId: Long?,
    val purchaseRecordStatus: PurchaseRecordStatus?,
    val hasUnknownPaymentDates: Boolean
) {
    val needsOrderSelection: Boolean get() = priceId == null
    val needsRecordVerification: Boolean
        get() = priceId != null && purchaseRecordStatus != PurchaseRecordStatus.TRACKED

    val reasonText: String get() = buildList {
        when {
            needsOrderSelection -> add("当前订单未设置，价格与付款资料待补")
            purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED -> add("已确认拥有，付款资料待核对")
            needsRecordVerification -> add("购买状态与付款资料待核对")
        }
        if (hasUnknownPaymentDates) add("实付日期待补")
    }.joinToString("；")
}
