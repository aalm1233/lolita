package com.lolita.app.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.local.entity.ItemEvidence
import java.util.UUID

/** Keeps immutable source restrictions after its disposable task has been removed. */
data class RecognitionEvidencePayload(val version: Int = 1, val taskId: String?, val appearanceOnly: Boolean,
    val candidate: RecognitionCandidate) {
    fun encode(): String = Gson().newBuilder().serializeNulls().create().toJson(this)
    companion object {
        fun decode(json: String): RecognitionEvidencePayload {
            require(json.length <= 1024*1024)
            val raw = JsonParser.parseString(json).asJsonObject
            val envelope = raw.has("candidate")
            if (envelope) {
                require(raw.keySet() == setOf("version", "taskId", "appearanceOnly", "candidate"))
                require(raw["version"].isJsonPrimitive && raw["version"].asJsonPrimitive.isNumber && raw["version"].asBigDecimal.intValueExact() == 1 && raw["appearanceOnly"].isJsonPrimitive && raw["appearanceOnly"].asJsonPrimitive.isBoolean)
                require(raw["taskId"].isJsonNull || raw["taskId"].isJsonPrimitive && raw["taskId"].asJsonPrimitive.isString && UUID.fromString(raw["taskId"].asString).toString() == raw["taskId"].asString)
            }
            val candidateJson = if (envelope) raw["candidate"] else raw
            val refs = candidateJson.asJsonObject["regionIds"].asJsonArray.map { it.asString }.toSet()
            val result = RecognitionResult.decode("{\"version\":1,\"candidates\":[${candidateJson}]}", refs)
            val appearance = envelope && raw["appearanceOnly"].asBoolean
            val candidate = result.candidates.single()
            require(!appearance || candidate.source == RecognitionSource.VISUAL && !candidate.field.textOnly)
            return RecognitionEvidencePayload(taskId = if (envelope && !raw["taskId"].isJsonNull) raw["taskId"].asString else null,
                appearanceOnly = appearance, candidate = candidate)
        }
    }
}

fun ItemEvidence.validateRecognitionEvidence(): RecognitionEvidencePayload {
    require(UUID.fromString(id).toString() == id && UUID.fromString(draftId).toString() == draftId)
    require(itemId == null || itemId > 0)
    val payload = RecognitionEvidencePayload.decode(candidateJson)
    require(payload.candidate.field == field && payload.candidate.eligible(payload.candidate.variant,payload.candidate.sizeRow))
    require(regionImagePaths.isNotEmpty() && regionImagePaths.none(String::isBlank))
    require(regionImagePaths.size == payload.candidate.regionIds.distinct().size)
    return payload
}
