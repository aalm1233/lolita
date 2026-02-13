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

    @ColumnInfo(name = "deposit")
    val deposit: Double? = null,

    @ColumnInfo(name = "balance")
    val balance: Double? = null,

    @ColumnInfo(name = "purchase_date")
    val purchaseDate: Long? = null
)
