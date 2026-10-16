package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A task fixes its provider separately; request metadata never contains files or credentials. */
enum class ImageTaskKind { TRY_ON, MANNEQUIN }

enum class ImageTaskStatus {
    QUEUED, PREPARING, GENERATING, SAVING, AWAITING_CONFIRMATION,
    COMPLETED, FAILED, INTERRUPTED, CANCELLED, DISCARDED;

    val isActive: Boolean get() = this == PREPARING || this == GENERATING || this == SAVING
    val retainsSourceImages: Boolean get() = this == QUEUED || isActive
    val isTerminal: Boolean get() = this in setOf(COMPLETED, FAILED, INTERRUPTED, CANCELLED, DISCARDED)
    val isFinished: Boolean get() = isTerminal

    /** Recovery deliberately never resumes a potentially paid request. */
    fun interruptedOnRestart(): ImageTaskStatus = if (retainsSourceImages) INTERRUPTED else this
}

data class ImageTaskGarment(val itemId: Long, val name: String, val role: TryOnRole)

data class ImageTaskRequest(
    val garments: List<ImageTaskGarment>,
    val instructions: String = "",
    val version: Int = 1
) {
    init {
        require(version == 1) { "不支持的图像任务请求版本" }
        require(garments.size in 1..8) { "请选择1至8件服饰和小物" }
        require(garments.all { it.itemId > 0 && it.name.isNotBlank() }) { "图像任务缺少服饰资料" }
        require(garments.map { it.itemId }.distinct().size == garments.size) { "同一件服饰只能选择一次" }
        require(instructions.length <= 200) { "补充说明最多200字" }
    }

    fun validate(kind: ImageTaskKind) {
        when (kind) {
            ImageTaskKind.TRY_ON -> require(garments.any { it.role.garment }) { "请至少选择一件服装，并核对穿戴位置" }
            ImageTaskKind.MANNEQUIN -> require(garments.size == 1 && garments.single().role == TryOnRole.MAIN) { "人台图只能选择一件裙子" }
        }
    }

    fun selections(paths: List<String>): List<TryOnSelection> {
        require(paths.size == garments.size && paths.all { it.isNotBlank() }) { "图像任务参考图片不完整" }
        return garments.mapIndexed { index, garment -> TryOnSelection(garment.itemId, garment.name, paths[index], garment.role) }
    }

    fun encode(): String = Gson().toJson(this)

    companion object {
        /** Parse explicitly so Gson cannot bypass constructors or accept unknown enum values. */
        fun decode(json: String): ImageTaskRequest = try {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.keySet() == setOf("version", "garments", "instructions")) { "图像任务请求字段无效" }
            val version = root.get("version").asJsonPrimitive.let {
                require(it.isNumber); it.asBigDecimal.intValueExact()
            }
            val garments = root.getAsJsonArray("garments").map { entry ->
                val row = entry.asJsonObject
                require(row.keySet() == setOf("itemId", "name", "role")) { "图像任务服饰字段无效" }
                val id = row.get("itemId").asJsonPrimitive.let {
                    require(it.isNumber); it.asBigDecimal.longValueExact()
                }
                ImageTaskGarment(id, row.string("name"), TryOnRole.valueOf(row.string("role")))
            }
            ImageTaskRequest(garments, root.string("instructions"), version)
        } catch (failure: Exception) {
            throw IllegalArgumentException("图像任务请求无效", failure)
        }

        private fun JsonObject.string(name: String): String = get(name).asJsonPrimitive.let {
            require(it.isString); it.asString
        }
    }
}

/** Compact durable notification state; no request, image paths or credentials. */
data class ImageTaskExecutionSummary(
    val taskId: String?,
    val title: String?,
    val status: ImageTaskStatus?,
    val queuedCount: Int
)
