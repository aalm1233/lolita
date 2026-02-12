package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "brands",
    indices = [Index(value = ["name"], unique = true)]
)
data class Brand(
    @ColumnInfo(name = "is_preset", defaultValue = "0")
    val isPreset: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "id")
    val id: Long = 0
)
