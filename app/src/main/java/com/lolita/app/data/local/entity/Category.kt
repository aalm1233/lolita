package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "is_preset", defaultValue = "0")
    val isPreset: Boolean = false,

    @ColumnInfo(name = "name")
    val name: String
)
