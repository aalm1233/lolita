package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outfit_logs",
    indices = [
        Index(value = ["date"]),
        Index(value = ["created_at"])
    ]
)
data class OutfitLog(
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
