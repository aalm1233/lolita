package com.lolita.app.data.local.entity

import androidx.room.*

/** A quote is not a purchase obligation. One independently editable quote per item. */
@Entity(tableName = "item_reference_prices", foreignKeys = [ForeignKey(entity = Item::class,
    parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE)])
data class ItemReferencePrice(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: Long,
    val amount: Double,
    val note: String = "",
    val revision: Long = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

enum class RefundKind { REFUND, PRICE_DIFFERENCE }

/** Positive, received cash. Voiding corrects an entry and preserves the original evidence. */
@Entity(tableName = "order_refunds", foreignKeys = [ForeignKey(entity = Price::class,
    parentColumns = ["id"], childColumns = ["price_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("price_id"), Index(value = ["operation_id"], unique = true)])
data class OrderRefund(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "price_id") val priceId: Long,
    val amount: Double,
    val kind: RefundKind,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    val note: String = "",
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "voided_at") val voidedAt: Long? = null
)

/** Cancellation keeps the original order, payments and agreed total refund. */
@Entity(tableName = "order_cancellations", foreignKeys = [ForeignKey(entity = Price::class,
    parentColumns = ["id"], childColumns = ["price_id"], onDelete = ForeignKey.CASCADE)])
data class OrderCancellation(
    @PrimaryKey @ColumnInfo(name = "price_id") val priceId: Long,
    @ColumnInfo(name = "expected_refund_total") val expectedRefundTotal: Double?,
    @ColumnInfo(name = "cancelled_at") val cancelledAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val revision: Long = 1
)
