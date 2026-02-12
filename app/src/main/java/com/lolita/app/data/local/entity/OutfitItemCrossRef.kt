package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "outfit_item_cross_ref",
    primaryKeys = ["outfit_log_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitLog::class,
            parentColumns = ["id"],
            childColumns = ["outfit_log_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["outfit_log_id"]),
        Index(value = ["item_id"])
    ]
)
data class OutfitItemCrossRef(
    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "outfit_log_id")
    val outfitLogId: Long
)
