package com.lolita.app.data.remote

import com.google.gson.JsonParser
import com.google.gson.Gson
import com.lolita.app.domain.model.ImageGenerationFailure
import com.lolita.app.domain.model.ImageGenerationFailureKind
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

data class GeneratedImageLayer(val bytes: ByteArray, val zIndex: Int, val name: String,
    val description: String, val normalizedBounds: List<Int>)
data class GeneratedImageLayers(val layers: List<GeneratedImageLayer>, val generatedImages: Int)

/** Explicit, separately billed decomposition. Never redirects, retries, or downloads result URLs. */
class SeedreamLayersApi(private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    suspend fun decompose(apiKey: String, image: ByteArray, prompt: String): GeneratedImageLayers {
        require(apiKey.isNotBlank() && apiKey.length <= 512 && apiKey.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        require(image.size in 1..10 * 1024 * 1024 && prompt.length in 1..30000) { "分层输入无效或过大" }
        return suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val worker = Thread({
                val result = runCatching {
                    val conn = connectionFactory(URL(SeedreamImageApi.ENDPOINT))
                    connection.set(conn)
                    try {
                        check(continuation.isActive) { "已取消等待" }
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 20_000
                        conn.readTimeout = 300_000
                        conn.instanceFollowRedirects = false
                        conn.doOutput = true
                        conn.setChunkedStreamingMode(64 * 1024)
                        conn.setRequestProperty("Authorization", "Bearer $apiKey")
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        val gson = Gson()
                        val request = mapOf("model" to SeedreamImageApi.MODEL, "prompt" to prompt,
                            "image" to "data:${ReferenceImageEncoding.of(image).mimeType};base64,${Base64.getEncoder().encodeToString(image)}",
                            "layer_decomposition" to true, "size" to "2K", "output_format" to "png",
                            "response_format" to "b64_json", "stream" to false, "watermark" to false)
                        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { gson.toJson(request, it) }
                        when (conn.responseCode) {
                            200 -> Unit
                            401, 403 -> throw ImageGenerationFailure(ImageGenerationFailureKind.AUTH)
                            402 -> throw ImageGenerationFailure(ImageGenerationFailureKind.QUOTA)
                            429 -> throw ImageGenerationFailure(ImageGenerationFailureKind.RATE_LIMIT)
                            else -> throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)
                        }
                        if (conn.contentLengthLong > MAX_RESPONSE_BYTES) invalid()
                        parse(conn.inputStream.use { bounded(it) }.toString(Charsets.UTF_8))
                    } catch (_: SocketTimeoutException) {
                        throw ImageGenerationFailure(ImageGenerationFailureKind.TIMEOUT)
                    } catch (_: IOException) {
                        throw ImageGenerationFailure(ImageGenerationFailureKind.NETWORK)
                    } finally { conn.disconnect() }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }, "lolita-seedream-layers").apply { isDaemon = true }
            continuation.invokeOnCancellation { connection.get()?.disconnect(); worker.interrupt() }
            worker.start()
        }
    }

    internal fun parse(json: String): GeneratedImageLayers = try {
        val root = JsonParser.parseString(json).asJsonObject
        require(!root.has("error") || root["error"].isJsonNull)
        val data = root["data"].asJsonArray
        require(data.size() in 2..17)
        var total = 0L
        val indices = mutableSetOf<Int>()
        val layers = data.mapNotNull { entry ->
            val row = entry.asJsonObject
            require(!row.has("error") || row["error"].isJsonNull)
            val z = row["z_index"].asBigDecimal.intValueExact()
            require(z in 0..16 && indices.add(z))
            if (z == 0) return@mapNotNull null // The background is not a selectable single-item layer.
            require(row["output_format"].asString == "png")
            val encoded = row["b64_json"].asString
            require(encoded.length in 1..44 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            total += bytes.size
            require(bytes.size in 1..32 * 1024 * 1024 && total <= 40L * 1024 * 1024)
            require(ReferenceImageEncoding.of(bytes).mimeType == "image/png")
            val bounds = row["bounding_box"].asJsonObject["normalized"].asJsonArray.map { it.asBigDecimal.intValueExact() }
            require(bounds.size == 4 && bounds.all { it in 0..1000 } && bounds[0] < bounds[2] && bounds[1] < bounds[3])
            GeneratedImageLayer(bytes, z, row["name"]?.asString.orEmpty().take(160),
                row["description"]?.asString.orEmpty().take(600), bounds)
        }.sortedBy { it.zIndex }
        require(0 in indices && layers.isNotEmpty())
        val generated = root["usage"]?.asJsonObject?.get("generated_images")?.asBigDecimal?.intValueExact() ?: data.size()
        require(generated in data.size()..17)
        GeneratedImageLayers(layers, generated)
    } catch (failure: ImageGenerationFailure) { throw failure }
      catch (_: Exception) { invalid() }

    private fun bounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "已取消等待" }
            val count = input.read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size().toLong() + count > MAX_RESPONSE_BYTES) invalid()
            out.write(buffer, 0, count)
        }
    }

    private fun invalid(): Nothing = throw ImageGenerationFailure(ImageGenerationFailureKind.INVALID_IMAGE)
    companion object { private const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024 }
}
