package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prices",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["type"])
    ]
)
data class Price(
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "type")
    val type: PriceType,

    @ColumnInfo(name = "total_price")
    val totalPrice: Double,

    @ColumnInfo(name = "shipping", defaultValue = "0")
    val shipping: Double = 0.0,

    @ColumnInfo(name = "deposit")
    val deposit: Double? = null,

    @ColumnInfo(name = "balance")
    val balance: Double? = null,

    // Purchase evidence belongs to the order and survives removal of its current binding.
    @ColumnInfo(name = "purchase_record_status", defaultValue = "'NEEDS_REVIEW'")
    val purchaseRecordStatus: PurchaseRecordStatus = PurchaseRecordStatus.TRACKED
)
