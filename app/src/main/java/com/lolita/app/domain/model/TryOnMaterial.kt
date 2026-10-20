package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser

enum class MaterialStage { FRONT_VIEW, EXTRACT_LAYER, TRANSPARENT }

/** Local-only preparation receipt. The exact file is previewed before the paid submission. */
data class PreparedMaterialInput(val imagePath: String, val sha256: String,
    val sourcePath: String, val sourceSha256: String)

data class MaterialTaskSpec(val stage: MaterialStage, val expectedMaterialId: String? = null,
    val originalInputPath: String? = null, val parentTaskId: String? = null,
    val originalInputSha256: String? = null) {
    fun validate() {
        require((originalInputPath == null) == (originalInputSha256 == null))
        require(originalInputPath == null || originalInputPath.isNotBlank() && originalInputSha256!!.matches(Regex("[a-f0-9]{64}")))
        require(stage != MaterialStage.EXTRACT_LAYER || !parentTaskId.isNullOrBlank()) { "请先核对已生成的主视图" }
    }
    companion object {
        fun decode(raw: String): MaterialTaskSpec {
            val row = JsonParser.parseString(raw).asJsonObject
            row.requireFields("stage", "expectedMaterialId", "originalInputPath", "parentTaskId", "originalInputSha256")
            fun optional(key: String) = row[key]?.takeUnless { it.isJsonNull }?.let { row.strictString(key) }
            return MaterialTaskSpec(MaterialStage.valueOf(row.strictString("stage")), optional("expectedMaterialId"),
                optional("originalInputPath"), optional("parentTaskId"), optional("originalInputSha256")).also { it.validate() }
        }
    }
}

enum class AccessoryBaseType { TRY_ON_RESULT, PERSONAL_MODEL }
data class AccessoryBaseSnapshot(val type: AccessoryBaseType, val id: Long, val name: String,
    val clothingConfirmed: Boolean) {
    fun validate() { require(id > 0 && name.isNotBlank() && name.length <= 160 && clothingConfirmed) { "请确认要保留的底图与现有服装" } }
    companion object {
        fun decode(raw: String): AccessoryBaseSnapshot {
            val row = JsonParser.parseString(raw).asJsonObject
            row.requireFields("type", "id", "name", "clothingConfirmed")
            require(row["id"].asJsonPrimitive.isNumber && row["clothingConfirmed"].asJsonPrimitive.isBoolean)
            return AccessoryBaseSnapshot(AccessoryBaseType.valueOf(row.strictString("type")),
                row["id"].asBigDecimal.longValueExact(), row.strictString("name"), row["clothingConfirmed"].asBoolean).also { it.validate() }
        }
    }
}

data class MaterialLayerCandidate(val imagePath: String, val sha256: String, val zIndex: Int,
    val name: String, val description: String, val normalizedBounds: List<Int>)

object MaterialLayers {
    fun encode(rows: List<MaterialLayerCandidate>): String = Gson().toJson(rows)
    fun decode(raw: String): List<MaterialLayerCandidate> {
        val list = JsonParser.parseString(raw).asJsonArray
        require(list.size() <= 16) { "图层数量超限" }
        return list.map { value ->
            val row = value.asJsonObject
            row.requireFields("imagePath", "sha256", "zIndex", "name", "description", "normalizedBounds")
            val bounds = row["normalizedBounds"].asJsonArray.map { it.asBigDecimal.intValueExact() }
            val z = row["zIndex"].asBigDecimal.intValueExact()
            require(z in 1..16 && bounds.size == 4 && bounds.all { it in 0..1000 } && bounds[0] < bounds[2] && bounds[1] < bounds[3])
            MaterialLayerCandidate(row.strictString("imagePath"), row.strictString("sha256"), z,
                row.strictString("name"), row.strictString("description"), bounds).also {
                require(it.imagePath.isNotBlank() && it.sha256.matches(Regex("[a-f0-9]{64}")) && it.name.length <= 160 && it.description.length <= 600)
            }
        }.also { require(it.map { row -> row.zIndex }.distinct().size == it.size && it.map { row -> row.imagePath }.distinct().size == it.size) }
    }
}
