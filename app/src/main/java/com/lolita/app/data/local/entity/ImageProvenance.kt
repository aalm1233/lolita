package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class ImageSourceType { ORIGINAL, AI_GENERATED }

@Entity(tableName = "image_provenance", primaryKeys = ["image_path", "item_id"],
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("item_id")])
data class ImageProvenance(
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "source_type") val sourceType: ImageSourceType,
    val mode: String,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
