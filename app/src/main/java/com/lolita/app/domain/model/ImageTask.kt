package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/** A task fixes its provider separately; request metadata never contains files or credentials. */
enum class ImageTaskKind { TRY_ON, MANNEQUIN, PERSONAL_MODEL, SINGLE_ITEM_MATERIAL }

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

data class ImageTaskGarment(val itemId: Long, val name: String, val role: TryOnRole,
    val semantic: CategorySemantic? = null, val composition: ItemComposition? = null, val materialId: String? = null)

data class ImageTaskRequest(
    val garments: List<ImageTaskGarment>,
    val instructions: String = "",
    val version: Int = 1,
    val personalModel: PersonalModelRequest? = null,
    val tryOnModel: TryOnModelSnapshot? = null,
    val mannequinSubject: MannequinSubject? = null,
    val promptVersion: Int? = null,
    val promptSnapshot: String? = null,
    val serviceModel: String? = null,
    val inputSha256s: List<String>? = null,
    val wigHeadTemplate: WigHeadTemplateSnapshot? = null,
    val productOriginalImagePath: String? = null,
    val materialSpec: MaterialTaskSpec? = null,
    val accessoryBase: AccessoryBaseSnapshot? = null
) {
    init {
        require(version in 1..6) { "不支持的图像任务请求版本" }
        require(version >= 4 || ((version == 3) == (mannequinSubject != null))) { "展示图分类快照必须使用新版请求" }
        require(version != 3 || (personalModel == null && tryOnModel == null && garments.size == 1 && garments.single().role == TryOnRole.MAIN)) { "新版展示图请求不能混入其他任务资料" }
        require(version in setOf(2, 4, 5) || (personalModel == null && tryOnModel == null)) { "个人模特资料必须使用新版请求" }
        require(garments.size in (if (personalModel == null) 1..8 else 0..0)) { "请选择1至8件服饰和小物；模特生成不接受服饰" }
        require(garments.all { it.itemId > 0 && it.name.isNotBlank() }) { "图像任务缺少服饰资料" }
        require(garments.map { it.itemId }.distinct().size == garments.size) { "同一件服饰只能选择一次" }
        require(instructions.length <= 200) { "补充说明最多200字" }
        require(personalModel == null || tryOnModel == null) { "模特生成与试衣资料不能混用" }
        if (version < 4) {
            require(promptVersion == null && promptSnapshot == null && serviceModel == null && inputSha256s == null && wigHeadTemplate == null && productOriginalImagePath == null)
            require(mannequinSubject?.semantic == null && mannequinSubject?.presentation != MannequinPresentation.WIG_HEAD) { "旧展示任务不能使用新模板" }
            require(garments.all { it.role.ordinal < 8 && it.semantic == null && it.composition == null }) { "旧任务不能使用新角色或语义" }
        } else {
            require(productOriginalImagePath == null || productOriginalImagePath.isNotBlank() && (mannequinSubject != null || materialSpec != null)) { "裁切来源快照无效" }
            require(personalModel == null && promptVersion in 1..TryOnRequest.PROMPT_VERSION && !promptSnapshot.isNullOrBlank() && promptSnapshot.length <= 30000 && !serviceModel.isNullOrBlank()) { "不可变请求缺少服务或提示词快照" }
            require(promptVersion == 1 || mannequinSubject == null) { "穿戴提示词修订仅用于试衣" }
            require(inputSha256s != null && inputSha256s.size in 1..9 && inputSha256s.all { it.matches(Regex("[a-f0-9]{64}")) }) { "不可变输入校验信息无效" }
            require(mannequinSubject == null || mannequinSubject.semantic != null && mannequinSubject.semantic != CategorySemantic.UNCONFIRMED) { "展示图品类语义未确认" }
            require(wigHeadTemplate == null || mannequinSubject?.presentation == MannequinPresentation.WIG_HEAD)
            require(wigHeadTemplate == null || wigHeadTemplate.sha256 == inputSha256s.first()) { "头模图片校验与实际输入不一致" }
            require(mannequinSubject?.presentation != MannequinPresentation.WIG_HEAD || wigHeadTemplate != null) { "请选择本次头模" }
        }
        require(version >= 5 || (materialSpec == null && accessoryBase == null && garments.all { it.materialId == null })) { "新素材用途须使用新协议" }
        if (version == 5) {
            require(personalModel == null && mannequinSubject == null && wigHeadTemplate == null)
            require(materialSpec != null || accessoryBase != null || garments.any { it.materialId != null })
            require(materialSpec == null || accessoryBase == null && garments.size == 1 && garments.single().semantic == CategorySemantic.WAIST_ACCESSORY)
            materialSpec?.validate()
            accessoryBase?.validate()
        }
        if (version == 6) {
            require(materialSpec != null && accessoryBase == null && personalModel == null && tryOnModel == null && mannequinSubject == null && wigHeadTemplate == null && promptVersion == MaterialPreparation.PROMPT_VERSION)
            val garment = garments.single()
            require(garment.semantic?.supportsMaterialPreparation == true && garment.role == garment.semantic.defaultWearRole && garment.composition != null && garment.materialId == null) { "素材品类、角色或组合无效" }
            materialSpec.validate()
        }
        personalModel?.validate()
    }

    fun validate(kind: ImageTaskKind) {
        when (kind) {
            ImageTaskKind.TRY_ON -> {
                require(version !in setOf(3, 6) && mannequinSubject == null) { "展示图请求不能用于试衣" }
                require(personalModel == null) { "试衣任务不能包含模特生成资料" }
                require(materialSpec == null)
                if (accessoryBase != null) {
                    require(version == 5 && garments.size == 1 && garments.single().semantic == CategorySemantic.WAIST_ACCESSORY && garments.single().role == TryOnRole.WAIST && !garments.single().materialId.isNullOrBlank()) { "加配饰须选择一件已确认腰饰素材" }
                    accessoryBase.validate()
                    return
                }
                require(garments.any { it.role.garment }) { "请至少选择一件服装，并核对穿戴位置" }
                if (version >= 4) TryOnRequest(garments.map { TryOnSelection(it.itemId, it.name, "snapshot", it.role, it.semantic, it.composition) }, instructions = instructions)
            }
            ImageTaskKind.MANNEQUIN -> {
                require(version < 5) { "试衣素材协议不能用于人台图" }
                require(promptVersion == null || promptVersion == 1) { "穿戴提示词修订仅用于试衣" }
                require(personalModel == null && tryOnModel == null) { "人台图不能包含个人模特资料" }
                require(garments.size == 1 && garments.single().role == TryOnRole.MAIN) { "展示图只能选择一件物品" }
            }
            ImageTaskKind.SINGLE_ITEM_MATERIAL -> {
                require(version in 5..6 && materialSpec != null && accessoryBase == null && tryOnModel == null)
                require(garments.size == 1 && (version == 6 || garments.single().semantic == CategorySemantic.WAIST_ACCESSORY))
            }
            ImageTaskKind.PERSONAL_MODEL -> {
                require(version == 2 && garments.isEmpty() && personalModel != null && tryOnModel == null) { "模特生成任务资料无效" }
                personalModel.validate()
            }
        }
    }

    private fun validatePaths(paths: List<String>) {
        if (version >= 4) {
            require(paths.size == inputSha256s!!.size && paths.all { it.isNotBlank() }) { "不可变输入图片不完整" }
            val expected = if (materialSpec != null) 1 else mannequinSubject?.presentation?.inputCount ?: (garments.size + 1)
            require(paths.size == expected) { "实际输入与本次模式不匹配" }
            require(wigHeadTemplate == null || wigHeadTemplate.imagePath == paths.first()) { "头模实际输入与快照不匹配" }
            return
        }
        if (personalModel != null) personalModel.validatePaths(paths)
        else require(paths.size == garments.size + (if (tryOnModel != null) 1 else 0) && paths.all { it.isNotBlank() }) { "图像任务参考图片不完整" }
    }

    fun modelImagePath(paths: List<String>): String? {
        validatePaths(paths)
        return if (tryOnModel != null || (version >= 4 && mannequinSubject == null && materialSpec == null)) paths.first() else null
    }

    fun selections(paths: List<String>): List<TryOnSelection> {
        validatePaths(paths)
        val offset = if (version >= 4) paths.size - garments.size else if (tryOnModel != null) 1 else 0
        return garments.mapIndexed { index, garment -> TryOnSelection(garment.itemId, garment.name, paths[index + offset], garment.role, garment.semantic, garment.composition, garment.materialId) }
    }

    fun validateInputImages(paths: List<String>) {
        validatePaths(paths)
        if (version >= 5) materialSpec?.originalInputPath?.let { path ->
            require(runCatching { imageSha256(path) == materialSpec?.originalInputSha256 }.getOrDefault(false)) { "原始输入校验失败" }
        }
        if (version >= 4) paths.forEachIndexed { index, path ->
            require(runCatching { imageSha256(path) == inputSha256s!![index] }.getOrDefault(false)) { "任务实际输入已丢失或校验失败；原请求未被替换" }
        }
    }

    fun mapImagePaths(map: (String) -> String): ImageTaskRequest = copy(
        wigHeadTemplate = wigHeadTemplate?.let { it.copy(imagePath = map(it.imagePath)) },
        productOriginalImagePath = productOriginalImagePath?.let(map),
        materialSpec = materialSpec?.copy(originalInputPath = materialSpec.originalInputPath?.let(map)))

    /** History retains labels and roles after task deletion; images belong to the task. */
    fun historySnapshot(): String = mapImagePaths { "task-owned-input" }.encode()

    fun encode(): String {
        val gson = if (version == 1) Gson() else GsonBuilder().serializeNulls().create()
        val root = gson.toJsonTree(this).asJsonObject
        if (version < 5) {
            root.remove("materialSpec"); root.remove("accessoryBase")
            root["garments"].asJsonArray.forEach { it.asJsonObject.remove("materialId") }
        }
        if (version < 4) {
            listOf("promptVersion", "promptSnapshot", "serviceModel", "inputSha256s", "wigHeadTemplate", "productOriginalImagePath").forEach(root::remove)
            root["garments"].asJsonArray.forEach { entry -> entry.asJsonObject.apply { remove("semantic"); remove("composition") } }
            root["mannequinSubject"]?.takeUnless { it.isJsonNull }?.asJsonObject?.remove("semantic")
        }
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
            // Early v4 drafts did not yet contain the optional crop-source identity.
            if (version == 4 && !root.has("productOriginalImagePath")) root.add("productOriginalImagePath", com.google.gson.JsonNull.INSTANCE)
            when (version) {
                1 -> root.requireFields("version", "garments", "instructions")
                2 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel")
                3 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel", "mannequinSubject")
                4 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel", "mannequinSubject", "promptVersion", "promptSnapshot", "serviceModel", "inputSha256s", "wigHeadTemplate", "productOriginalImagePath")
                5, 6 -> root.requireFields("version", "garments", "instructions", "personalModel", "tryOnModel", "mannequinSubject", "promptVersion", "promptSnapshot", "serviceModel", "inputSha256s", "wigHeadTemplate", "productOriginalImagePath", "materialSpec", "accessoryBase")
                else -> throw IllegalArgumentException("不支持的图像任务请求版本")
            }
            val garments = root.getAsJsonArray("garments").map { entry ->
                val row = entry.asJsonObject
                if (version < 4) row.requireFields("itemId", "name", "role")
                else if (version == 4) row.requireFields("itemId", "name", "role", "semantic", "composition")
                else row.requireFields("itemId", "name", "role", "semantic", "composition", "materialId")
                val id = row.get("itemId").asJsonPrimitive.let {
                    require(it.isNumber); it.asBigDecimal.longValueExact()
                }
                ImageTaskGarment(id, row.strictString("name"), TryOnRole.valueOf(row.strictString("role")),
                    row["semantic"]?.takeUnless { it.isJsonNull }?.let { CategorySemantic.valueOf(row.strictString("semantic")) },
                    row["composition"]?.takeUnless { it.isJsonNull }?.let { ItemComposition.valueOf(row.strictString("composition")) },
                    row["materialId"]?.takeUnless { it.isJsonNull }?.let { row.strictString("materialId") })
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
            ImageTaskRequest(garments, root.strictString("instructions"), version, personal, model, subject,
                root["promptVersion"]?.takeUnless { it.isJsonNull }?.asJsonPrimitive?.let { require(it.isNumber); it.asBigDecimal.intValueExact() },
                root["promptSnapshot"]?.takeUnless { it.isJsonNull }?.let { root.strictString("promptSnapshot") },
                root["serviceModel"]?.takeUnless { it.isJsonNull }?.let { root.strictString("serviceModel") },
                root["inputSha256s"]?.takeUnless { it.isJsonNull }?.asJsonArray?.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString },
                root["wigHeadTemplate"]?.takeUnless { it.isJsonNull }?.let { WigHeadTemplateSnapshot.decode(it.toString()) },
                root["productOriginalImagePath"]?.takeUnless { it.isJsonNull }?.let { root.strictString("productOriginalImagePath") },
                root["materialSpec"]?.takeUnless { it.isJsonNull }?.let { MaterialTaskSpec.decode(it.toString()) },
                root["accessoryBase"]?.takeUnless { it.isJsonNull }?.let { AccessoryBaseSnapshot.decode(it.toString()) })
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
