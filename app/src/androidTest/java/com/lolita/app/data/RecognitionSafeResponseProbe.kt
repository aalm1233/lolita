package com.lolita.app.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream
import java.net.HttpURLConnection

/** Test-only transparent transport probe: raw bytes stay in memory and go unchanged to production. */
internal class RecognitionSafeResponseProbe(private val delegate: HttpURLConnection, val diagnostic: JsonObject) : HttpURLConnection(delegate.url) {
    override fun connect() = delegate.connect()
    override fun usingProxy() = delegate.usingProxy()
    override fun disconnect() = delegate.disconnect()
    override fun setRequestMethod(method: String) { delegate.requestMethod = method }
    override fun setConnectTimeout(timeout: Int) { delegate.connectTimeout = timeout }
    override fun setReadTimeout(timeout: Int) { delegate.readTimeout = timeout }
    override fun setInstanceFollowRedirects(followRedirects: Boolean) { delegate.instanceFollowRedirects = followRedirects }
    override fun setDoOutput(dooutput: Boolean) { delegate.doOutput = dooutput }
    override fun setUseCaches(usecaches: Boolean) { delegate.useCaches = usecaches }
    override fun setFixedLengthStreamingMode(contentLength: Int) = delegate.setFixedLengthStreamingMode(contentLength)
    override fun setRequestProperty(key: String, value: String) = delegate.setRequestProperty(key, value)
    override fun getOutputStream() = delegate.outputStream
    override fun getResponseCode(): Int = delegate.responseCode.also { diagnostic.addProperty("httpStatus", it) }
    override fun getContentLengthLong(): Long = delegate.contentLengthLong.also { diagnostic.addProperty("declaredBytes", it) }
    override fun getErrorStream() = delegate.errorStream
    override fun getInputStream(): java.io.InputStream {
        val original = delegate.inputStream
        val captured = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var eof = false
        while (captured.size() <= MAX_BYTES) {
            val count = original.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - captured.size()))
            if (count < 0) { eof = true; break }
            captured.write(buffer, 0, count)
        }
        val bytes = captured.toByteArray()
        diagnostic.addProperty("responseBytes", bytes.size)
        diagnostic.addProperty("diagnosticTruncated", !eof)
        if (eof) {
            original.close()
            diagnostic.add("shape", shape(bytes))
            return ByteArrayInputStream(bytes)
        }
        // Production still observes the full original stream and enforces its own limit.
        return SequenceInputStream(ByteArrayInputStream(bytes), original)
    }

    companion object {
        private const val MAX_BYTES = 1024 * 1024
        // A spelling/character filter is insufficient: even an ASCII key or model can carry a secret.
        // Only local protocol constants may be emitted; arbitrary server strings become OTHER/counts.
        private val allowedFields = setOf("id", "object", "created_at", "completed_at", "model", "output", "caching", "context_management",
            "error", "expire_at", "incomplete_details", "instructions", "max_output_tokens", "max_tool_calls", "metadata", "previous_response_id",
            "reasoning", "service_tier", "status", "store", "temperature", "text", "thinking", "tool_choice", "tools", "top_p", "usage",
            "parallel_tool_calls", "background", "content_filters", "frequency_penalty", "moderation", "presence_penalty", "prompt_cache_key",
            "prompt_cache_retention", "safety_identifier", "truncation", "user", "top_logprobs", "type", "role", "content", "phase", "partial",
            "annotations", "logprobs", "prefix", "effort", "summary", "reason", "version", "candidates", "field", "value", "source", "regionIds",
            "quote", "variant", "sizeRow", "measurement", "target", "basis", "unit")
        private val allowedModels = com.lolita.app.domain.model.RecognitionProvider.entries.map { it.model }.toSet()
        private fun type(value: JsonElement?): String = when {
            value == null -> "absent"
            value.isJsonNull -> "null"
            value.isJsonObject -> "object"
            value.isJsonArray -> "array"
            value.asJsonPrimitive.isBoolean -> "boolean"
            value.asJsonPrimitive.isNumber -> "number"
            else -> "string"
        }
        private fun known(row: JsonObject, key: String, values: Set<String>): String = row[key]?.takeIf {
            it.isJsonPrimitive && it.asJsonPrimitive.isString
        }?.asString?.takeIf { it in values } ?: type(row[key]).let { if (it == "string") "OTHER" else it }
        private fun fields(row: JsonObject) = Gson().toJsonTree(row.keySet().filter { it in allowedFields }.sorted())
        private fun fieldTypes(row: JsonObject) = JsonObject().apply {
            row.keySet().filter { it in allowedFields }.sorted().forEach { addProperty(it, type(row[it])) }
            addProperty("unknownFieldCount", row.keySet().count { it !in allowedFields })
        }
        internal fun shape(bytes: ByteArray): JsonObject = JsonObject().apply {
            val root = runCatching { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject }.getOrNull()
            addProperty("jsonObject", root != null)
            if (root == null) return@apply
            add("rootFields", fields(root)); add("rootTypes", fieldTypes(root))
            addProperty("object", known(root, "object", setOf("response")))
            addProperty("status", known(root, "status", setOf("completed", "incomplete", "failed", "in_progress", "cancelled", "queued")))
            addProperty("model", known(root, "model", allowedModels))
            for (key in listOf("store", "background", "parallel_tool_calls")) root[key]?.takeIf { type(it) == "boolean" }?.let { addProperty(key, it.asBoolean) }
            for (key in listOf("previous_response_id", "error", "incomplete_details")) addProperty("${key}_type", type(root[key]))
            for (key in listOf("thinking", "caching", "reasoning", "incomplete_details")) root[key]?.takeIf { it.isJsonObject }?.asJsonObject?.let { row ->
                add("${key}_fields", fields(row)); add("${key}_types", fieldTypes(row))
                addProperty("${key}_type_value", known(row, "type", setOf("disabled", "enabled")))
                if (key == "reasoning") addProperty("reasoning_effort", known(row, "effort", setOf("none", "low", "medium", "high")))
                if (key == "incomplete_details") addProperty("incomplete_reason", known(row, "reason", setOf("max_output_tokens", "content_filter")))
            }
            root["tools"]?.takeIf { it.isJsonArray }?.let { addProperty("toolCount", it.asJsonArray.size()) }
            val output = root["output"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@apply
            addProperty("outputCount", output.size())
            add("outputs", Gson().toJsonTree(output.take(10).map { element -> JsonObject().apply {
                if (!element.isJsonObject) { addProperty("type", type(element)); return@apply }
                val row = element.asJsonObject
                add("fields", fields(row)); add("types", fieldTypes(row))
                addProperty("type", known(row, "type", setOf("message", "reasoning", "function_call", "web_search_call")))
                addProperty("role", known(row, "role", setOf("assistant", "user", "system", "tool")))
                addProperty("status", known(row, "status", setOf("completed", "incomplete", "in_progress")))
                addProperty("phase", known(row, "phase", setOf("final_answer", "commentary")))
                row["partial"]?.takeIf { type(it) == "boolean" }?.let { addProperty("partial", it.asBoolean) }
                val content = row["content"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@apply
                addProperty("contentCount", content.size())
                add("contents", Gson().toJsonTree(content.take(10).map { contentElement -> JsonObject().apply {
                    if (!contentElement.isJsonObject) { addProperty("type", type(contentElement)); return@apply }
                    val text = contentElement.asJsonObject
                    add("fields", fields(text)); add("types", fieldTypes(text))
                    addProperty("type", known(text, "type", setOf("output_text", "refusal", "summary_text")))
                    for (key in listOf("annotations", "logprobs")) text[key]?.takeIf { it.isJsonArray }?.let { addProperty("${key}Count", it.asJsonArray.size()) }
                    val structured = text["text"]?.takeIf { type(it) == "string" }?.asString?.let {
                        runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
                    }
                    addProperty("structuredObject", structured != null)
                    structured?.let { document ->
                        add("structuredFields", fields(document)); add("structuredTypes", fieldTypes(document))
                        addProperty("versionIsOne", runCatching { document["version"].asBigDecimal.intValueExact() == 1 }.getOrDefault(false))
                        val candidates = document["candidates"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@let
                        addProperty("candidateCount", candidates.size())
                        addProperty("missingMeasurementCount", candidates.count { it.isJsonObject && !it.asJsonObject.has("measurement") })
                        add("candidateShapes", Gson().toJsonTree(candidates.take(120).map { candidate -> JsonObject().apply {
                            if (!candidate.isJsonObject) { addProperty("type", type(candidate)); return@apply }
                            val candidateRow = candidate.asJsonObject
                            add("fields", fields(candidateRow)); add("types", fieldTypes(candidateRow))
                            addProperty("field", known(candidateRow, "field", com.lolita.app.domain.model.RecognitionField.entries.map { it.name }.toSet()))
                            addProperty("source", known(candidateRow, "source", setOf("TEXT", "VISUAL", "UNKNOWN")))
                            candidateRow["regionIds"]?.takeIf { it.isJsonArray }?.let { addProperty("regionCount", it.asJsonArray.size()) }
                            for (key in listOf("id", "value", "quote", "variant", "sizeRow")) candidateRow[key]?.takeIf { type(it) == "string" }?.let {
                                addProperty("${key}Length", it.asString.length); addProperty("${key}Blank", it.asString.isBlank()); addProperty("${key}ContainsNul", it.asString.contains('\u0000'))
                            }
                            candidateRow["measurement"]?.takeIf { it.isJsonObject }?.asJsonObject?.let { add("measurementFields", fields(it)); add("measurementTypes", fieldTypes(it)) }
                        } }))
                    }
                } }))
            } }))
        }
    }
}
