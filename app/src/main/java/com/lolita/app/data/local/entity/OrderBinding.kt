package com.lolita.app.data.local.entity

import androidx.room.*

/** Historical prices remain intact; this binding selects one current purchase per item. */
@Entity(tableName = "order_bindings", foreignKeys = [
    ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Price::class, parentColumns = ["id"], childColumns = ["price_id"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["price_id"], unique = true)])
data class OrderBinding(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "price_id") val priceId: Long,
    @ColumnInfo(name = "purchase_record_status", defaultValue = "'TRACKED'")
    val purchaseRecordStatus: PurchaseRecordStatus = PurchaseRecordStatus.TRACKED
)
