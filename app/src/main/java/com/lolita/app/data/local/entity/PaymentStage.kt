package com.lolita.app.data.local.entity

import androidx.room.*

@Entity(tableName = "payment_stages", foreignKeys = [
    ForeignKey(entity = Price::class, parentColumns = ["id"], childColumns = ["price_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["price_id"])])
data class PaymentStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "price_id") val priceId: Long,
    val name: String,
    val amount: Double,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "reminder_set", defaultValue = "0") val reminderSet: Boolean = false,
    @ColumnInfo(name = "custom_reminder_days") val customReminderDays: Int? = null,
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: Long? = null
)
