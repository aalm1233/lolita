package com.lolita.app.data.local.entity

import androidx.room.*
import com.lolita.app.domain.model.*

@Entity(tableName = "item_try_on_materials",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["item_id"], unique = true), Index(value = ["source_task_id"], unique = true)])
data class ItemTryOnMaterial(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "source_image_path") val sourceImagePath: String,
    @ColumnInfo(name = "input_image_path") val inputImagePath: String,
    @ColumnInfo(name = "result_sha256") val resultSha256: String,
    @ColumnInfo(name = "source_task_id") val sourceTaskId: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long = createdAt
) {
    fun protectedImagePaths(): Set<String> = setOf(imagePath, sourceImagePath, inputImagePath)
    fun validate() {
        require(java.util.UUID.fromString(id).toString() == id && itemId > 0 && sourceTaskId.isNotBlank())
        require(protectedImagePaths().none(String::isBlank) && resultSha256.matches(Regex("[a-f0-9]{64}")))
        val request = ImageTaskRequest.decode(requestJson)
        require(request.version == 5 && request.materialSpec != null && request.garments.single().itemId == itemId)
    }
}
