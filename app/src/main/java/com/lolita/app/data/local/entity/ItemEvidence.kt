package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.RecognitionField

/** Accepted evidence stays separate from the display album and from disposable task history. */
@Entity(tableName = "item_evidence", foreignKeys = [ForeignKey(entity = Item::class,
    parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("item_id"), Index("draft_id")])
data class ItemEvidence(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: Long? = null,
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "field_key") val field: RecognitionField,
    @ColumnInfo(name = "candidate_json") val candidateJson: String,
    @ColumnInfo(name = "region_image_paths", defaultValue = "'[]'") val regionImagePaths: List<String>,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
