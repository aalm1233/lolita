package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Historical snapshots intentionally have no FK to mutable/deletable wardrobe records. */
@Entity(tableName = "try_on_results")
data class TryOnResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "item_ids", defaultValue = "'[]'") val itemIds: List<String>,
    @ColumnInfo(name = "item_names", defaultValue = "'[]'") val itemNames: List<String>,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "personal_model_id", defaultValue = "NULL") val personalModelId: Long? = null,
    @ColumnInfo(name = "personal_model_name", defaultValue = "NULL") val personalModelName: String? = null,
    @ColumnInfo(name = "request_snapshot_json", defaultValue = "NULL") val requestSnapshotJson: String? = null
)
