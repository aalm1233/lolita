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
    val error: String? = null,
    @ColumnInfo(name = "personal_model_id", defaultValue = "NULL") val personalModelId: Long? = null,
    @ColumnInfo(name = "keep_source_images", defaultValue = "0") val keepSourceImages: Boolean = false,
    @ColumnInfo(name = "source_resources_missing", defaultValue = "0") val sourceResourcesMissing: Boolean = false,
    @ColumnInfo(name = "material_id", defaultValue = "NULL") val materialId: String? = null,
    @ColumnInfo(name = "layers_json", defaultValue = "'[]'") val layersJson: String = "[]",
    @ColumnInfo(name = "generated_image_count", defaultValue = "1") val generatedImageCount: Int = 1
) {
    companion object {
        const val SOURCE_RESOURCES_MISSING_MESSAGE = "原输入资源缺失，无法继续生成；原模板与请求快照已保留，请重新选择资料新建任务"
    }

    fun validate() {
        require(id.isNotBlank() && title.isNotBlank()) { "图像任务缺少标识或名称" }
        val request = ImageTaskRequest.decode(requestJson)
        request.validate(kind)
        require(!sourceResourcesMissing || keepSourceImages && request.version >= 4) { "仅不可变输入任务可标记资源缺失" }
        require(keepSourceImages == (request.version >= 4)) { "任务输入保留策略与协议不匹配" }
        if (keepSourceImages && resultImagePath != null) require(resultImagePath !in sourceImagePaths && resultImagePath != request.productOriginalImagePath) { "生成结果不能复用输入或裁切原图" }
        if (retainsSourceImages) {
            if (kind == ImageTaskKind.PERSONAL_MODEL) request.personalModel!!.validatePaths(sourceImagePaths)
            else request.selections(sourceImagePaths)
        }
        require(generatedImageCount in 1..17)
        val layers = com.lolita.app.domain.model.MaterialLayers.decode(layersJson)
        require(layers.isEmpty() || kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && request.materialSpec?.stage == com.lolita.app.domain.model.MaterialStage.EXTRACT_LAYER)
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) {
            require(kind in setOf(ImageTaskKind.MANNEQUIN, ImageTaskKind.PERSONAL_MODEL, ImageTaskKind.SINGLE_ITEM_MATERIAL) && !resultImagePath.isNullOrBlank()) { "待确认任务缺少生成预览" }
        }
    }

    /** Personal generation keeps reference photos for confirmation and explicit retries. */
    val retainsSourceImages: Boolean get() = keepSourceImages || status.retainsSourceImages ||
        (kind == ImageTaskKind.PERSONAL_MODEL && status in setOf(ImageTaskStatus.AWAITING_CONFIRMATION,
            ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED))

    /** Completed paths are informational: the owning Item/TryOnResult/PersonalModel protects its image. */
    fun protectedImagePaths(): Set<String> = buildSet {
        if (retainsSourceImages) addAll(sourceImagePaths.filter { it.isNotBlank() })
        if (keepSourceImages) ImageTaskRequest.decode(requestJson).let { request ->
            request.productOriginalImagePath?.let(::add)
            request.materialSpec?.originalInputPath?.let(::add)
        }
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) com.lolita.app.domain.model.MaterialLayers.decode(layersJson).forEach { add(it.imagePath) }
        if (status == ImageTaskStatus.AWAITING_CONFIRMATION) resultImagePath?.takeIf { it.isNotBlank() }?.let(::add)
    }

    fun releaseUnusedSourceImages(): ImageTask = if (retainsSourceImages || sourceImagePaths.isEmpty()) this
        else copy(sourceImagePaths = emptyList())
}
