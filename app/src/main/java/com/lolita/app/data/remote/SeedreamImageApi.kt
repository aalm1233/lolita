package com.lolita.app.data.remote

import com.google.gson.JsonParser
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

/** One paid request, with no automatic retry or redirect. Never log keys or response bodies. */
class SeedreamImageApi(private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun cancel() { activeConnection.get()?.disconnect() }

    suspend fun generate(apiKey: String, images: List<ByteArray>, prompt: String): ByteArray =
        generateChecked(apiKey, images, prompt, 2..9)

    suspend fun generatePersonalModel(apiKey: String, images: List<ByteArray>, prompt: String): ByteArray =
        generateChecked(apiKey, images, prompt, 1..5)

    suspend fun generateMannequin(apiKey: String, images: List<ByteArray>, prompt: String): ByteArray =
        generateChecked(apiKey, images, prompt, 1..2)

    private suspend fun generateChecked(apiKey: String, images: List<ByteArray>, prompt: String, imageCount: IntRange): ByteArray {
        require(apiKey.isNotBlank() && apiKey.length <= 512 && apiKey.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        require(images.size in imageCount && images.all { it.isNotEmpty() && it.size <= 10 * 1024 * 1024 } && images.sumOf { it.size.toLong() } <= 40L * 1024 * 1024) { "参考图片数量或大小超出限制" }
        return suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val worker = Thread({
                val result = runCatching {
                    val conn = connectionFactory(URL(ENDPOINT))
                    connection.set(conn)
                    try {
                        check(activeConnection.compareAndSet(null, conn)) { "已有试衣任务正在结束，请稍后重试" }
                        check(continuation.isActive) { "已取消等待" }
                        execute(conn, apiKey, images, prompt)
                    } catch (error: SocketTimeoutException) {
                        throw ImageGenerationFailure(ImageGenerationFailureKind.TIMEOUT)
                    } catch (error: IOException) {
                        throw ImageGenerationFailure(ImageGenerationFailureKind.NETWORK)
                    } finally {
                        activeConnection.compareAndSet(conn, null)
                        conn.disconnect()
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }, "lolita-seedream-request").apply { isDaemon = true }
            continuation.invokeOnCancellation { connection.get()?.disconnect(); worker.interrupt() }
            worker.start()
        }
    }

    private fun execute(conn: HttpURLConnection, key: String, images: List<ByteArray>, prompt: String): ByteArray {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 300_000
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setChunkedStreamingMode(64 * 1024)
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
            val gson = com.google.gson.Gson()
            // Implicit watermarking is enabled in Ark; this flag controls the visible corner label.
            output.write("{\"model\":${gson.toJson(MODEL)},\"prompt\":${gson.toJson(prompt)},\"size\":\"1024x1536\",\"output_format\":\"png\",\"response_format\":\"b64_json\",\"stream\":false,\"watermark\":false,\"image\":[")
            images.forEachIndexed { index, bytes ->
                check(!Thread.currentThread().isInterrupted) { "已取消等待" }
                if (index > 0) output.write(",")
                output.write("\"data:image/jpeg;base64,")
                output.write(Base64.getEncoder().encodeToString(bytes))
                output.write("\"")
            }
            output.write("]}")
        }
        when (val code = conn.responseCode) {
            200 -> Unit
            401, 403 -> throw ImageGenerationFailure(ImageGenerationFailureKind.AUTH)
            402 -> throw ImageGenerationFailure(ImageGenerationFailureKind.QUOTA)
            429 -> {
                val code = runCatching {
                    val raw = conn.errorStream?.use { readBounded(it, 64 * 1024).toString(Charsets.UTF_8) }
                    raw?.let { JsonParser.parseString(it).asJsonObject["error"].asJsonObject["code"].asString }
                }.getOrNull()
                if (code == "SetLimitExceeded") throw ImageGenerationFailure(ImageGenerationFailureKind.SEEDREAM_LIMIT)
                throw ImageGenerationFailure(ImageGenerationFailureKind.RATE_LIMIT)
            }
            in 300..399 -> throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)
            else -> throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)
        }
        if (conn.contentLengthLong > MAX_RESPONSE_BYTES) throw ImageGenerationFailure(ImageGenerationFailureKind.INVALID_IMAGE)
        val json = conn.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8) }
        return try {
            val response = JsonParser.parseString(json).asJsonObject
            if (response.has("error") && !response["error"].isJsonNull) throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)
            val data = response["data"].asJsonArray
            check(data.size() == 1)
            val row = data[0].asJsonObject
            if (row.has("error") && !row["error"].isJsonNull) throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)
            val encoded = row["b64_json"].asString
            check(encoded.isNotBlank() && encoded.length <= 44 * 1024 * 1024)
            Base64.getDecoder().decode(encoded).also { check(it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES) }
        } catch (error: ImageGenerationFailure) {
            throw error
        } catch (error: Exception) {
            throw ImageGenerationFailure(ImageGenerationFailureKind.INVALID_IMAGE)
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "已取消等待" }
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > limit) throw ImageGenerationFailure(ImageGenerationFailureKind.INVALID_IMAGE)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MODEL = "doubao-seedream-5-0-pro-260628"
        const val ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/images/generations"
        private const val MAX_RESPONSE_BYTES = 48 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    }
}
