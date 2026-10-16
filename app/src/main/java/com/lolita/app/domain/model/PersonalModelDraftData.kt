package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser

/** Raw editor fields survive incomplete input; paths live exclusively on PersonalModelDraft. */
data class PersonalModelDraftData(
    val version: Int = 1,
    val name: String = "我的模特",
    val mode: PersonalModelMode = PersonalModelMode.PHOTO,
    val heightCm: String = "",
    val weightKg: String = "",
    val bustCm: String = "",
    val waistCm: String = "",
    val hipCm: String = "",
    val shoulderCm: String = "",
    val inseamCm: String = "",
    val photoRoles: List<PersonalModelPhotoRole> = emptyList(),
    val appearance: PersonalModelAppearance = PersonalModelAppearance.FEMALE,
    val instructions: String = ""
) {
    init {
        require(version == 1) { "不支持的人物草稿版本" }
        require(photoRoles.size <= 4 && photoRoles.distinct().size == photoRoles.size) { "人物草稿照片角色重复或过多" }
    }

    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(json: String): PersonalModelDraftData = try {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.keySet() == setOf("version", "name", "mode", "heightCm", "weightKg", "bustCm", "waistCm",
                "hipCm", "shoulderCm", "inseamCm", "photoRoles", "appearance", "instructions")) { "人物草稿字段无效" }
            fun string(key: String): String = root.get(key).asJsonPrimitive.let { require(it.isString); it.asString }
            val version = root.get("version").asJsonPrimitive.let { require(it.isNumber); it.asBigDecimal.intValueExact() }
            val roles = root.getAsJsonArray("photoRoles").map { entry ->
                entry.asJsonPrimitive.let { require(it.isString); PersonalModelPhotoRole.valueOf(it.asString) }
            }
            PersonalModelDraftData(version, string("name"), PersonalModelMode.valueOf(string("mode")), string("heightCm"),
                string("weightKg"), string("bustCm"), string("waistCm"), string("hipCm"), string("shoulderCm"),
                string("inseamCm"), roles, PersonalModelAppearance.valueOf(string("appearance")), string("instructions"))
        } catch (failure: Exception) {
            throw IllegalArgumentException("人物草稿数据无效", failure)
        }
    }
}
