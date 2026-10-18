package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Only this allowlist may leave the recognition boundary. No accounting or storage fields. */
enum class RecognitionField(val label: String, val textOnly: Boolean = false, val unitRequired: Boolean = false) {
    NAME("名称"), CATEGORY("类型"), BRAND("品牌", true), COLOR("颜色"), PATTERN("图案"), SIZE("尺码", true),
    WIG_COLOR("主要发色"), WIG_LENGTH("发长", true, true), WIG_CURL("直卷"), WIG_BANGS("刘海"),
    WIG_PARTING("分缝"), WIG_MATERIAL("假发材质", true), WIG_HEAT("耐热说明", true),
    WIG_HEAD_RANGE("头围适配", true), WIG_COMPONENTS("假发组件", true),
    HOSIERY_LENGTH("袜长类型"), HOSIERY_PATTERN("袜装花纹"), HOSIERY_THICKNESS("厚薄说明", true),
    HOSIERY_DEN("DEN 标识", true), HOSIERY_FOOT_RANGE("足长适配", true), HOSIERY_MATERIAL("袜装材质", true),
    HOSIERY_TRANSPARENCY("透肤说明", true),
    SHOE_SIZE_SYSTEM("鞋码体系", true), SHOE_HEEL("跟高", true, true),
    SHOE_INNER_LENGTH("鞋内长", true, true), SHOE_SHAFT("筒高", true, true), SHOE_TOE("鞋头"), SHOE_FASTENING("扣带"),
    PETTICOAT_LENGTH("裙撑长度", true, true), PETTICOAT_SHAPE("裙撑轮廓"), PETTICOAT_VOLUME("蓬度"),
    PETTICOAT_WAIST("裙撑腰围", true), PETTICOAT_LAYERS("层数", true), PETTICOAT_MATERIAL("裙撑材质", true),
    ACCESSORY_POSITION("配饰位置"), ACCESSORY_DIMENSIONS("配饰尺寸", true),
    ACCESSORY_FASTENING("固定方式"), ACCESSORY_MATERIAL("配饰材质", true),
    CARRY_METHOD("携带方式"), CARRY_DIMENSIONS("尺寸", true), CARRY_CAPACITY("容量说明", true),
    CARRY_FOLDABLE("可折叠说明", true), STYLE("风格建议"), SEASON("季节建议"),
    CLOTHING_LENGTH("衣长", true, true), CLOTHING_BUST("胸围", true, true),
    CLOTHING_WAIST("腰围", true, true), CLOTHING_HIP("臀围", true, true),
    CLOTHING_SHOULDER("肩宽", true, true), CLOTHING_SLEEVE("袖长", true, true),
    PRICE_NOTE("标价原文（仅参考）", true);
}

enum class RecognitionSource(val label: String) { TEXT("图片明文"), VISUAL("外观推测"), UNKNOWN("未能判断") }
enum class RecognitionStatus { PREPARED, RUNNING, AWAITING_REVIEW, APPLIED, INTERRUPTED, FAILED, DISCARDED }

enum class RecognitionMeasurementTarget { UNKNOWN, HAIR_LENGTH, HEAD_CIRCUMFERENCE, FOOT_LENGTH, HEEL_HEIGHT,
    SHOE_INNER_LENGTH, BOOT_SHAFT_HEIGHT, PETTICOAT_LENGTH, WAIST_CIRCUMFERENCE, ACCESSORY_DIMENSIONS,
    CARRY_DIMENSIONS, CLOTHING_LENGTH, BUST_CIRCUMFERENCE, HIP_CIRCUMFERENCE, SHOULDER_WIDTH, SLEEVE_LENGTH }
enum class RecognitionMeasurementBasis { UNKNOWN, PRODUCT_MEASURED, FLAT_WIDTH, BODY_FIT, STRETCHED_RANGE }
enum class RecognitionMeasurementUnit { UNKNOWN, CM, MM, INCH }
data class RecognitionMeasurement(val target: RecognitionMeasurementTarget, val basis: RecognitionMeasurementBasis,
    val unit: RecognitionMeasurementUnit)

/** Units and scope stay explicit; raw values are never converted or doubled. */
private fun RecognitionCandidate.validMeasurement(): Boolean {
    val expected = when (field) {
        RecognitionField.WIG_LENGTH -> RecognitionMeasurementTarget.HAIR_LENGTH
        RecognitionField.WIG_HEAD_RANGE -> RecognitionMeasurementTarget.HEAD_CIRCUMFERENCE
        RecognitionField.HOSIERY_FOOT_RANGE -> RecognitionMeasurementTarget.FOOT_LENGTH
        RecognitionField.SHOE_HEEL -> RecognitionMeasurementTarget.HEEL_HEIGHT
        RecognitionField.SHOE_INNER_LENGTH -> RecognitionMeasurementTarget.SHOE_INNER_LENGTH
        RecognitionField.SHOE_SHAFT -> RecognitionMeasurementTarget.BOOT_SHAFT_HEIGHT
        RecognitionField.PETTICOAT_LENGTH -> RecognitionMeasurementTarget.PETTICOAT_LENGTH
        RecognitionField.PETTICOAT_WAIST, RecognitionField.CLOTHING_WAIST -> RecognitionMeasurementTarget.WAIST_CIRCUMFERENCE
        RecognitionField.ACCESSORY_DIMENSIONS -> RecognitionMeasurementTarget.ACCESSORY_DIMENSIONS
        RecognitionField.CARRY_DIMENSIONS -> RecognitionMeasurementTarget.CARRY_DIMENSIONS
        RecognitionField.CLOTHING_LENGTH -> RecognitionMeasurementTarget.CLOTHING_LENGTH
        RecognitionField.CLOTHING_BUST -> RecognitionMeasurementTarget.BUST_CIRCUMFERENCE
        RecognitionField.CLOTHING_HIP -> RecognitionMeasurementTarget.HIP_CIRCUMFERENCE
        RecognitionField.CLOTHING_SHOULDER -> RecognitionMeasurementTarget.SHOULDER_WIDTH
        RecognitionField.CLOTHING_SLEEVE -> RecognitionMeasurementTarget.SLEEVE_LENGTH
        else -> return true
    }
    val measure = measurement ?: return false
    if (measure.target != expected || source != RecognitionSource.TEXT || quote.isBlank()) return false
    val allowedBasis = when (expected) {
        RecognitionMeasurementTarget.HEAD_CIRCUMFERENCE, RecognitionMeasurementTarget.FOOT_LENGTH -> setOf(RecognitionMeasurementBasis.BODY_FIT)
        RecognitionMeasurementTarget.WAIST_CIRCUMFERENCE -> setOf(RecognitionMeasurementBasis.PRODUCT_MEASURED, RecognitionMeasurementBasis.STRETCHED_RANGE)
        else -> setOf(RecognitionMeasurementBasis.PRODUCT_MEASURED)
    }
    if (measure.basis !in allowedBasis) return false
    val subject = when (expected) {
        RecognitionMeasurementTarget.HAIR_LENGTH -> "发长|长度|hair.?length"
        RecognitionMeasurementTarget.HEAD_CIRCUMFERENCE -> "头围|head.?circumference"
        RecognitionMeasurementTarget.FOOT_LENGTH -> "足长|脚长|foot.?length"
        RecognitionMeasurementTarget.HEEL_HEIGHT -> "跟高|鞋跟|heel"
        RecognitionMeasurementTarget.SHOE_INNER_LENGTH -> "内长|鞋内|insole|inner.?length"
        RecognitionMeasurementTarget.BOOT_SHAFT_HEIGHT -> "筒高|靴高|shaft"
        RecognitionMeasurementTarget.PETTICOAT_LENGTH -> "撑长|裙撑|长度|length"
        RecognitionMeasurementTarget.WAIST_CIRCUMFERENCE -> "腰围|waist"
        RecognitionMeasurementTarget.CLOTHING_LENGTH -> "衣长|裙长|length"
        RecognitionMeasurementTarget.BUST_CIRCUMFERENCE -> "胸围|bust|chest"
        RecognitionMeasurementTarget.HIP_CIRCUMFERENCE -> "臀围|hip"
        RecognitionMeasurementTarget.SHOULDER_WIDTH -> "肩宽|shoulder"
        RecognitionMeasurementTarget.SLEEVE_LENGTH -> "袖长|sleeve"
        else -> "尺寸|长|宽|高|dimension|size|length|width|height"
    }
    if (!Regex(subject, RegexOption.IGNORE_CASE).containsMatchIn(quote)) return false
    if (expected == RecognitionMeasurementTarget.SHOE_INNER_LENGTH && Regex("足长|脚长|foot.?length|适合", RegexOption.IGNORE_CASE).containsMatchIn(quote)) return false
    if (expected in setOf(RecognitionMeasurementTarget.BUST_CIRCUMFERENCE, RecognitionMeasurementTarget.WAIST_CIRCUMFERENCE,
            RecognitionMeasurementTarget.HIP_CIRCUMFERENCE) && Regex("平铺|半围|半胸|半腰|flat|half", RegexOption.IGNORE_CASE).containsMatchIn(quote)) return false
    val unitPattern = when (measure.unit) {
        RecognitionMeasurementUnit.CM -> "(?:cm|厘米|公分)"
        RecognitionMeasurementUnit.MM -> "(?:mm|毫米)"
        RecognitionMeasurementUnit.INCH -> "(?:in|inch|inches|英寸|吋)"
        RecognitionMeasurementUnit.UNKNOWN -> return false
    }
    val number = "(?:[0-9]+(?:\\.[0-9]+)?)"
    val dimension = expected in setOf(RecognitionMeasurementTarget.ACCESSORY_DIMENSIONS, RecognitionMeasurementTarget.CARRY_DIMENSIONS)
    val separator = if (dimension) "[-–—~～至×xX*]" else "[-–—~～至]"
    if (!Regex("(?i)^\\s*(?:约\\s*)?$number(?:\\s*$unitPattern)?(?:\\s*$separator\\s*$number(?:\\s*$unitPattern)?){0,2}\\s*$unitPattern\\s*$").matches(value)) return false
    val numbers = Regex(number).findAll(value).map { it.value.toBigDecimalOrNull() }.toList()
    if (numbers.isEmpty() || numbers.any { it == null || it.signum() <= 0 }) return false
    if (!dimension && numbers.size > 2) return false
    if (!dimension && numbers.size == 2 && numbers[0]!! > numbers[1]!!) return false
    // The source must contain the same measurement, not an unrelated unit elsewhere in the image.
    val normalise: (String) -> String = { it.lowercase().replace(Regex("\\s+"), "").replace("厘米", "cm").replace("公分", "cm").replace("毫米", "mm").replace("英寸", "in").replace("吋", "in").removePrefix("约") }
    return normalise(quote).contains(normalise(value))
}

data class RecognitionCandidate(
    val id: String,
    val field: RecognitionField,
    val value: String,
    val source: RecognitionSource,
    val regionIds: List<String>,
    val quote: String,
    val variant: String? = null,
    val sizeRow: String? = null,
    val measurement: RecognitionMeasurement? = null
) {
    fun eligible(selectedVariant: String?, selectedSize: String?): Boolean {
        if (value.isBlank() || source == RecognitionSource.UNKNOWN || field == RecognitionField.PRICE_NOTE) return false
        if (source == RecognitionSource.TEXT && quote.isBlank()) return false
        if (field.textOnly && source != RecognitionSource.TEXT) return false
        if (!validMeasurement()) return false
        if (field == RecognitionField.WIG_HEAT && value.any(Char::isDigit) &&
            (!Regex("(?i)^\\s*(?:最高|不超过|耐热|耐温)?\\s*[1-9][0-9]{0,2}\\s*(?:℃|°C|摄氏度)\\s*$").matches(value) ||
                !quote.lowercase().replace(" ", "").contains(value.lowercase().replace(" ", "")))) return false
        if (field == RecognitionField.HOSIERY_DEN && (!Regex("(?i)^\\s*[1-9][0-9]{0,3}\\s*(?:D|DEN)\\s*$").matches(value) || !quote.lowercase().replace(" ", "").contains(value.lowercase().replace(" ", "")))) return false
        if (field == RecognitionField.PETTICOAT_LAYERS && (!Regex("(?i)^\\s*(?:[1-9][0-9]{0,2}|[一二三四五六七八九十百]{1,4})\\s*(?:层|layers?)\\s*$").matches(value) || !quote.replace(" ", "").contains(value.replace(" ", "")))) return false
        if (!variant.isNullOrBlank() && selectedVariant != variant) return false
        if (!sizeRow.isNullOrBlank() && selectedSize != sizeRow) return false
        return true
    }

    fun preselect(current: String): Boolean = current.isBlank() && source == RecognitionSource.TEXT &&
        field !in setOf(RecognitionField.CATEGORY, RecognitionField.BRAND, RecognitionField.STYLE,
            RecognitionField.SEASON, RecognitionField.PRICE_NOTE) && eligible(null, null)
}

data class RecognitionResult(val version: Int = 1, val candidates: List<RecognitionCandidate>) {
    fun encode(): String = Gson().newBuilder().serializeNulls().create().toJson(this)

    companion object {
        private val candidateFields = setOf("id", "field", "value", "source", "regionIds", "quote", "variant", "sizeRow", "measurement")
        fun decode(json: String, regionIds: Set<String>): RecognitionResult = try {
            require(json.length <= 1024 * 1024) { "识别结果过大" }
            val root = JsonParser.parseString(json).asJsonObject
            require(root.keySet() == setOf("version", "candidates") && root["version"].isJsonPrimitive && root["version"].asJsonPrimitive.isNumber && root["version"].asBigDecimal.intValueExact() == 1) { "不支持的识别协议" }
            val rows = root["candidates"].asJsonArray
            require(rows.size() <= 120) { "识别建议过多，请缩小区域" }
            val parsed = rows.map { element ->
                val row = element.asJsonObject
                require(row.keySet() == candidateFields || row.keySet() == candidateFields - "measurement") { "识别结果包含未支持字段" }
                fun text(key: String, limit: Int): String {
                    val raw = row[key]
                    require(raw.isJsonPrimitive && raw.asJsonPrimitive.isString) { "识别字段格式有误" }
                    return raw.asString.also { require(it.length <= limit && !it.contains('\u0000')) { "识别字段过长" } }
                }
                fun optional(key: String): String? = if (row[key].isJsonNull) null else text(key, 240).takeIf(String::isNotBlank)
                val refs = row["regionIds"].asJsonArray.map { ref ->
                    require(ref.isJsonPrimitive && ref.asJsonPrimitive.isString)
                    ref.asString.also { require(it in regionIds) { "识别证据不属于本次区域" } }
                }
                require(refs.isNotEmpty() && refs.size <= 5 && refs.distinct().size == refs.size) { "缺少有效证据区域" }
                RecognitionCandidate(text("id", 80).also { require(it.isNotBlank()) },
                    RecognitionField.valueOf(text("field", 80)), text("value", 2000),
                    RecognitionSource.valueOf(text("source", 20)), refs, text("quote", 3000), optional("variant"), optional("sizeRow"), row["measurement"]?.takeUnless { it.isJsonNull }?.let { raw ->
                        val metadata = raw.asJsonObject
                        require(metadata.keySet() == setOf("target", "basis", "unit"))
                        require(metadata.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString })
                        RecognitionMeasurement(RecognitionMeasurementTarget.valueOf(metadata["target"].asString),
                            RecognitionMeasurementBasis.valueOf(metadata["basis"].asString), RecognitionMeasurementUnit.valueOf(metadata["unit"].asString))
                    })
            }
            require(parsed.map { it.id }.distinct().size == parsed.size) { "识别建议身份重复" }
            RecognitionResult(candidates = parsed)
        } catch (error: Exception) {
            throw IllegalArgumentException("识别结果无法核验，请重新框选或手工填写")
        }

        fun schema(): JsonObject = JsonParser.parseString("""
            {"type":"object","additionalProperties":false,"properties":{
              "version":{"type":"integer","enum":[1]},
              "candidates":{"type":"array","items":{"type":"object","additionalProperties":false,
                "properties":{"id":{"type":"string"},"field":{"type":"string","enum":${Gson().toJson(RecognitionField.entries.map { it.name })}},
                "value":{"type":"string"},"source":{"type":"string","enum":["TEXT","VISUAL","UNKNOWN"]},
                "regionIds":{"type":"array","items":{"type":"string"}},"quote":{"type":"string"},
                "variant":{"type":["string","null"]},"sizeRow":{"type":["string","null"]},
                "measurement":{"anyOf":[{"type":"null"},{"type":"object","additionalProperties":false,"properties":{
                  "target":{"type":"string","enum":${Gson().toJson(RecognitionMeasurementTarget.entries.map { it.name })}},
                  "basis":{"type":"string","enum":${Gson().toJson(RecognitionMeasurementBasis.entries.map { it.name })}},
                  "unit":{"type":"string","enum":${Gson().toJson(RecognitionMeasurementUnit.entries.map { it.name })}}},"required":["target","basis","unit"]}]}},
                "required":["id","field","value","source","regionIds","quote","variant","sizeRow","measurement"]}}},
              "required":["version","candidates"]}
        """).asJsonObject
    }
}

data class RecognitionMerge(val values: Map<RecognitionField, String>, val acceptedIds: Set<String>, val conflicts: Set<String>) {
    companion object {
        fun apply(requestDraftId: String, currentDraftId: String,
            baseline: Map<RecognitionField, String>, current: Map<RecognitionField, String>,
            candidates: List<RecognitionCandidate>, selectedIds: Set<String>,
            selectedVariant: String? = null, selectedSize: String? = null): RecognitionMerge {
            require(requestDraftId == currentDraftId) { "草稿已切换，请重新核对识别资料" }
            require(selectedIds.all { selected -> candidates.any { it.id == selected } }) { "识别建议已变更" }
            val values = linkedMapOf<RecognitionField, String>()
            val accepted = linkedSetOf<String>()
            val conflicts = linkedSetOf<String>()
            candidates.filter { it.id in selectedIds && it.eligible(selectedVariant, selectedSize) }.groupBy { it.field }.forEach { (field, rows) ->
                if (rows.map { it.value }.distinct().size != 1 || current[field].orEmpty() != baseline[field].orEmpty()) {
                    conflicts.addAll(rows.map { it.id })
                } else if (rows.all { it.eligible(selectedVariant, selectedSize) }) {
                    values[field] = rows.first().value
                    accepted.addAll(rows.map { it.id })
                }
            }
            return RecognitionMerge(values, accepted, conflicts)
        }
    }
}
