package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID

data class RecognitionRegion(val id: String, val sha256: String, val width: Int, val height: Int, val appearanceOnly: Boolean = false)
enum class RecognitionProvider(val label: String, val model: String) {
    VOLCENGINE_ARK("豆包 Seed 2.1 Pro", "doubao-seed-2-1-pro-260915"),
    DEEPSEEK("DeepSeek Flash", "deepseek-flash")
}

/** Paths and credentials deliberately live outside the versioned request. */
data class RecognitionRequest(
    val version: Int = 1,
    val draftId: String,
    val targetItemId: Long?,
    val regions: List<RecognitionRegion>,
    val baseline: Map<RecognitionField, String>,
    val family: WardrobeFamily = WardrobeFamily.UNCONFIRMED,
    val provider: RecognitionProvider = RecognitionProvider.VOLCENGINE_ARK,
    val model: String = provider.model
) {
    fun validate() {
        require(version == 1 && model == provider.model) { "不支持的识图协议" }
        require(UUID.fromString(draftId).toString() == draftId && (targetItemId == null || targetItemId > 0)) { "草稿身份无效" }
        require(regions.size in 1..5 && regions.map { it.id }.distinct().size == regions.size) { "请选择1至5个识别区域" }
        regions.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.sha256.matches(Regex("[a-f0-9]{64}")))
            require(it.width in 1..4096 && it.height in 1..4096 && it.width.toLong() * it.height <= 8_000_000)
        }
        require(baseline.values.all { it.length <= 4000 })
    }
    fun encode(): String { validate(); return Gson().newBuilder().serializeNulls().create().toJson(this) }

    companion object {
        const val MODEL = "doubao-seed-2-1-pro-260915"
        fun decode(json: String): RecognitionRequest = try {
            require(json.length <= 512 * 1024)
            val row = JsonParser.parseString(json).asJsonObject
            require(row.keySet() == setOf("version", "draftId", "targetItemId", "regions", "baseline", "family", "model", "provider"))
            listOf("draftId", "family", "model", "provider").forEach { require(row[it].isJsonPrimitive && row[it].asJsonPrimitive.isString) }
            require(row["version"].isJsonPrimitive && row["version"].asJsonPrimitive.isNumber && row["version"].asBigDecimal.intValueExact() == 1)
            row["targetItemId"].takeUnless { it.isJsonNull }?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber); it.asBigDecimal.longValueExact() }
            RecognitionProvider.valueOf(row["provider"].asString)
            row["baseline"].asJsonObject.entrySet().forEach { (key, value) ->
                RecognitionField.valueOf(key)
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            }
            row["regions"].asJsonArray.forEach { raw ->
                val region = raw.asJsonObject
                require(region.keySet() == setOf("id", "sha256", "width", "height", "appearanceOnly"))
                listOf("id", "sha256").forEach { require(region[it].isJsonPrimitive && region[it].asJsonPrimitive.isString) }
                require(region["appearanceOnly"].asJsonPrimitive.isBoolean)
                listOf("width", "height").forEach { require(region[it].isJsonPrimitive && region[it].asJsonPrimitive.isNumber); region[it].asBigDecimal.intValueExact() }
            }
            WardrobeFamily.valueOf(row["family"].asString)
            Gson().fromJson(row, RecognitionRequest::class.java).also { it.validate() }
        } catch (_: Exception) { throw IllegalArgumentException("识图请求无法读取，请重新核对区域") }
    }
}
