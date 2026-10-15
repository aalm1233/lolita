package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.domain.model.ImageTaskRequest
import com.lolita.app.domain.model.ImageTaskStatus

/** Historical snapshots intentionally have no foreign keys to mutable wardrobe/results. */
@Entity(tableName = "image_tasks")
data class ImageTask(
    @PrimaryKey val id: String,
    val kind: ImageTaskKind,
    val provider: ImageGenerationProvider,
    val title: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "source_image_paths", defaultValue = "'[]'") val sourceImagePaths: List<String>,
    val status: ImageTaskStatus = ImageTaskStatus.QUEUED,
    @ColumnInfo(name = "request_sent", defaultValue = "0") val requestSent: Boolean = false,
    @ColumnInfo(name = "result_image_path") val resultImagePath: String? = null,
    @ColumnInfo(name = "try_on_result_id") val tryOnResultId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    val error: String? = null
) {
    fun validate() {
        require(id.isNotBlank() && title.isNotBlank()) { "图像任务缺少标识或名称" }
        val request = ImageTaskRequest.decode(requestJson)
        request.validate(kind)
        if (status.retainsSourceImages) request.selections(sourceImagePaths)
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) {
            require(kind == ImageTaskKind.MANNEQUIN && !resultImagePath.isNullOrBlank()) { "待确认任务缺少人台预览" }
        }
    }

    /** Completed paths are informational: the owning Item/TryOnResult protects its image. */
    fun protectedImagePaths(): Set<String> = buildSet {
        if (status.retainsSourceImages) addAll(sourceImagePaths.filter { it.isNotBlank() })
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) resultImagePath?.takeIf { it.isNotBlank() }?.let(::add)
    }

    fun releaseUnusedSourceImages(): ImageTask = if (status.retainsSourceImages || sourceImagePaths.isEmpty()) this
        else copy(sourceImagePaths = emptyList())
}
