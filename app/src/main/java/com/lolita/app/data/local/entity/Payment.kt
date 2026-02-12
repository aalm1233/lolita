package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Price::class,
            parentColumns = ["id"],
            childColumns = ["price_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["price_id"]),
        Index(value = ["due_date"]),
        Index(value = ["is_paid"])
    ]
)
data class Payment(
    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "custom_reminder_days")
    val customReminderDays: Int? = null,

    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "is_paid", defaultValue = "0")
    val isPaid: Boolean = false,

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "paid_date")
    val paidDate: Long? = null,

    @ColumnInfo(name = "price_id")
    val priceId: Long,

    @ColumnInfo(name = "reminder_set", defaultValue = "0")
    val reminderSet: Boolean = false
)
