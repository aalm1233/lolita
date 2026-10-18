package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser

/** appliedValues are repository-owned history, never supplied by a review widget. */
data class RecognitionReview(val selectedIds: Set<String> = emptySet(), val edits: Map<String,String> = emptyMap(),
    val variant: String? = null, val sizeRow: String? = null, val resolvedBrandId: Long? = null,
    val resolvedCategoryId: Long? = null, val appliedValues: Map<RecognitionField,String> = emptyMap()) {
    fun encode(): String = Gson().newBuilder().serializeNulls().create().toJson(this)
    fun forSelection(candidates: List<RecognitionCandidate>): RecognitionReview = copy(selectedIds = selectedIds.filter { id ->
        candidates.firstOrNull { it.id == id }?.let { it.copy(value = edits[id] ?: it.value).eligible(variant, sizeRow) } == true
    }.toSet())
    companion object {
        fun decode(json: String, candidateIds: Set<String>): RecognitionReview = try {
            require(json.length <= 512*1024)
            val row = JsonParser.parseString(json).asJsonObject
            val keys = setOf("selectedIds", "edits", "variant", "sizeRow", "resolvedBrandId", "resolvedCategoryId", "appliedValues")
            require(row.keySet() == keys || row.keySet() == keys - "appliedValues")
            fun validText(value: com.google.gson.JsonElement, max: Int) = value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.length <= max && !value.asString.contains('\u0000')
            val selected = row["selectedIds"].asJsonArray
            require(selected.size() <= candidateIds.size && selected.all { validText(it,80) && it.asString in candidateIds })
            require(selected.map { it.asString }.distinct().size == selected.size())
            require(row["edits"].asJsonObject.entrySet().all { (id,value) -> id in candidateIds && validText(value,2000) })
            listOf("variant", "sizeRow").forEach { key -> require(row[key].isJsonNull || validText(row[key],240)) }
            listOf("resolvedBrandId", "resolvedCategoryId").forEach { key ->
                val value = row[key]
                require(value.isJsonNull || value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asBigDecimal.longValueExact() > 0)
            }
            row["appliedValues"]?.asJsonObject?.entrySet()?.forEach { (field, value) ->
                require(RecognitionField.valueOf(field) != RecognitionField.PRICE_NOTE && validText(value,2000))
            }
            if (!row.has("appliedValues")) row.add("appliedValues", com.google.gson.JsonObject())
            Gson().fromJson(row, RecognitionReview::class.java)
        } catch (failure: Exception) { throw IllegalArgumentException("识图核对记录无法读取，请重新核对结果", failure) }
    }
}
