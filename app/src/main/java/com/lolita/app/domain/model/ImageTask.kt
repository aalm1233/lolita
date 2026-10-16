package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/** A task fixes its provider separately; request metadata never contains files or credentials. */
enum class ImageTaskKind { TRY_ON, MANNEQUIN, PERSONAL_MODEL }

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
    val version: Int = 1,
    val personalModel: PersonalModelRequest? = null,
    val tryOnModel: TryOnModelSnapshot? = null,
    val mannequinSubject: MannequinSubject? = null
) {
    init {
        require(version in 1..3) { "不支持的图像任务请求版本" }
        require((version == 3) == (mannequinSubject != null)) { "展示图分类快照必须使用新版请求" }
        require(version != 3 || (personalModel == null && tryOnModel == null && garments.size == 1 && garments.single().role == TryOnRole.MAIN)) { "新版展示图请求不能混入其他任务资料" }
        require(version == 2 || (personalModel == null && tryOnModel == null)) { "个人模特资料必须使用新版请求" }
        require(garments.size in (if (personalModel == null) 1..8 else 0..0)) { "请选择1至8件服饰和小物；模特生成不接受服饰" }
        require(garments.all { it.itemId > 0 && it.name.isNotBlank() }) { "图像任务缺少服饰资料" }
        require(garments.map { it.itemId }.distinct().size == garments.size) { "同一件服饰只能选择一次" }
        require(instructions.length <= 200) { "补充说明最多200字" }
        require(personalModel == null || tryOnModel == null) { "模特生成与试衣资料不能混用" }
        personalModel?.validate()
    }

    fun validate(kind: ImageTaskKind) {
        when (kind) {
            ImageTaskKind.TRY_ON -> {
                require(version != 3) { "展示图请求不能用于试衣" }
                require(personalModel == null) { "试衣任务不能包含模特生成资料" }
                require(garments.any { it.role.garment }) { "请至少选择一件服装，并核对穿戴位置" }
            }
            ImageTaskKind.MANNEQUIN -> {
                require(personalModel == null && tryOnModel == null) { "人台图不能包含个人模特资料" }
                require(garments.size == 1 && garments.single().role == TryOnRole.MAIN) { "展示图只能选择一件物品" }
            }
            ImageTaskKind.PERSONAL_MODEL -> {
                require(version == 2 && garments.isEmpty() && personalModel != null && tryOnModel == null) { "模特生成任务资料无效" }
                personalModel.validate()
            }
        }
    }

    private fun validatePaths(paths: List<String>) {
        if (personalModel != null) personalModel.validatePaths(paths)
        else require(paths.size == garments.size + (if (tryOnModel != null) 1 else 0) && paths.all { it.isNotBlank() }) { "图像任务参考图片不完整" }
    }

    fun modelImagePath(paths: List<String>): String? {
        validatePaths(paths)
        return if (tryOnModel != null) paths.first() else null
    }

    fun selections(paths: List<String>): List<TryOnSelection> {
        validatePaths(paths)
        val offset = if (tryOnModel != null) 1 else 0
        return garments.mapIndexed { index, garment -> TryOnSelection(garment.itemId, garment.name, paths[index + offset], garment.role) }
    }

    fun encode(): String {
        if (version == 1) return Gson().toJson(this)
        val gson = GsonBuilder().serializeNulls().create()
        val root = gson.toJsonTree(this).asJsonObject
        if (version == 2) root.remove("mannequinSubject")
        return gson.toJson(root)
    }

    companion object {
        /** Parse explicitly so Gson cannot bypass constructors or accept unknown enum values. */
        fun decode(json: String): ImageTaskRequest = try {
            val root = JsonParser.parseString(json).asJsonObject
            val version = root.get("version").asJsonPrimitive.let {
                require(it.isNumber); it.asBigDecimal.intValueExact()
            }
            when (version) {
                1 -> root.requireFields("version", "garments", "instructions")
                2 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel")
                3 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel", "mannequinSubject")
                else -> throw IllegalArgumentException("不支持的图像任务请求版本")
            }
            val garments = root.getAsJsonArray("garments").map { entry ->
                val row = entry.asJsonObject
                row.requireFields("itemId", "name", "role")
                val id = row.get("itemId").asJsonPrimitive.let {
                    require(it.isNumber); it.asBigDecimal.longValueExact()
                }
                ImageTaskGarment(id, row.strictString("name"), TryOnRole.valueOf(row.strictString("role")))
            }
            val personal = root.get("personalModel")?.takeUnless { it.isJsonNull }?.let { PersonalModelRequest.decode(it.toString()) }
            val model = root.get("tryOnModel")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { row ->
                row.requireFields("personalModelId", "name")
                val id = row.get("personalModelId").asJsonPrimitive.let {
                    require(it.isNumber); it.asBigDecimal.longValueExact()
                }
                TryOnModelSnapshot(id, row.strictString("name"))
            }
            val subject = root.get("mannequinSubject")?.takeUnless { it.isJsonNull }?.let { MannequinSubject.decode(it.toString()) }
            ImageTaskRequest(garments, root.strictString("instructions"), version, personal, model, subject)
        } catch (failure: Exception) {
            throw IllegalArgumentException("图像任务请求无效", failure)
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
