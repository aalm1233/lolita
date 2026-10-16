package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class PersonalModelMode { PHOTO, PARAMETERS }
enum class PersonalModelPhotoRole { FACE, FRONT, SIDE, VIRTUAL_FACE }
enum class PersonalModelAppearance { FEMALE, MALE, NEUTRAL }

/** Values are independent measurements; missing optional values remain unknown. */
data class BodyMeasurements(
    val heightCm: Double,
    val weightKg: Double,
    val bustCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val shoulderCm: Double? = null,
    val inseamCm: Double? = null
) {
    fun validate() {
        requireBound(heightCm, 50.0..260.0)
        requireBound(weightKg, 10.0..500.0)
        listOfNotNull(bustCm, waistCm, hipCm).forEach { requireBound(it, 10.0..350.0) }
        shoulderCm?.let { requireBound(it, 10.0..120.0) }
        inseamCm?.let { requireBound(it, 10.0..180.0) }
    }

    private fun requireBound(value: Double, bounds: ClosedFloatingPointRange<Double>) {
        require(value.isFinite() && value in bounds) { "身体参数超出支持范围" }
    }
}

/** Image paths are stored separately in role order, never inside request JSON. */
data class PersonalModelRequest(
    val name: String,
    val mode: PersonalModelMode,
    val measurements: BodyMeasurements,
    val photoRoles: List<PersonalModelPhotoRole> = emptyList(),
    val appearance: PersonalModelAppearance = PersonalModelAppearance.FEMALE,
    val instructions: String = ""
) {
    init { validate() }

    fun validate() {
        require(name == name.trim() && name.isNotBlank() && name.length <= 60) { "模特名称须为1至60字，且不含首尾空格" }
        require(instructions.length <= 200) { "补充说明最多200字" }
        measurements.validate()
        require(photoRoles.distinct().size == photoRoles.size) { "照片用途不能重复" }
        when (mode) {
            PersonalModelMode.PHOTO -> {
                require(PersonalModelPhotoRole.VIRTUAL_FACE !in photoRoles) { "照片方式不能使用虚拟面容参考" }
                require(photoRoles.any { it == PersonalModelPhotoRole.FACE || it == PersonalModelPhotoRole.FRONT }) { "请至少选择脸部照或正面全身照" }
            }
            PersonalModelMode.PARAMETERS -> require(photoRoles.isEmpty() || photoRoles == listOf(PersonalModelPhotoRole.VIRTUAL_FACE)) { "参数方式只能使用一张明确的虚拟面容参考" }
        }
    }

    fun validatePaths(paths: List<String>) {
        validate()
        require(paths.size == photoRoles.size && paths.all { it.isNotBlank() }) { "模特参考照片与用途不匹配" }
    }

    fun encode(): String = GsonBuilder().serializeNulls().create().toJson(this)

    fun prompt(): String {
        validate()
        val references = photoRoles.mapIndexed { index, role -> mapOf("image" to index + 2, "role" to role.name) }
        val identity = when (mode) {
            PersonalModelMode.PHOTO -> "Personal FACE/FRONT/SIDE photos determine identity, face, skin tone and hair; photos supplement posture. Explicit measurements constrain the target proportions even when they differ from old photos."
            PersonalModelMode.PARAMETERS -> "Generate a fictional ADULT appearance (${appearance.name})." +
                if (PersonalModelPhotoRole.VIRTUAL_FACE in photoRoles) " Preserve the approved VIRTUAL_FACE reference's face while applying the target measurements." else " Generate a new virtual face."
        }
        return """
            Create one photorealistic ADULT full-body personal wardrobe model photograph.
            Image 1 is only a layout/pose template, not a face or body authority. Do not inherit its identity or body proportions.
            $identity
            No automatic slimming or leg elongation. Never infer missing measurements or impose fixed body ratios.
            Use a neutral studio, simple top and shorts, neutral shoes, natural standing pose and full head-to-foot framing.
            Do not transfer garments, accessories or backgrounds from personal photos. No extra accessories, text, watermark, panels or collage.
            Reference role metadata (image order is authoritative): ${Gson().toJson(references)}
            Target measurements (cm and kg; absent fields are unknown): ${Gson().toJson(measurements)}
            Explicit user description: ${Gson().toJson(instructions)}
        """.trimIndent()
    }

    companion object {
        fun decode(json: String): PersonalModelRequest = try {
            val root = JsonParser.parseString(json).asJsonObject
            root.requireFields("name", "mode", "measurements", "photoRoles", "appearance", "instructions")
            val size = root.getAsJsonObject("measurements")
            size.requireFields("heightCm", "weightKg", "bustCm", "waistCm", "hipCm", "shoulderCm", "inseamCm")
            val measurements = BodyMeasurements(size.number("heightCm"), size.number("weightKg"),
                size.optionalNumber("bustCm"), size.optionalNumber("waistCm"), size.optionalNumber("hipCm"),
                size.optionalNumber("shoulderCm"), size.optionalNumber("inseamCm"))
            PersonalModelRequest(root.strictString("name"), PersonalModelMode.valueOf(root.strictString("mode")), measurements,
                root.getAsJsonArray("photoRoles").map { value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    PersonalModelPhotoRole.valueOf(value.asString)
                }, PersonalModelAppearance.valueOf(root.strictString("appearance")), root.strictString("instructions"))
        } catch (failure: Exception) {
            throw IllegalArgumentException("个人模特请求无效", failure)
        }

        private fun JsonObject.optionalNumber(name: String): Double? = if (get(name).isJsonNull) null else number(name)
        private fun JsonObject.number(name: String): Double = get(name).asJsonPrimitive.let {
            require(it.isNumber); it.asDouble.also { value -> require(value.isFinite()) }
        }
    }
}

/** Durable identity label; the actual model image is a separate owned path. */
data class TryOnModelSnapshot(val personalModelId: Long, val name: String) {
    init {
        require(personalModelId > 0 && name.isNotBlank()) { "试衣模特资料无效" }
    }
}

internal fun JsonObject.requireFields(vararg fields: String) {
    require(keySet() == fields.toSet()) { "请求字段无效" }
}

internal fun JsonObject.strictString(name: String): String = get(name).asJsonPrimitive.let {
    require(it.isString); it.asString
}
