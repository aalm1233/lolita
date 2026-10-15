package com.lolita.app.data.remote

import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** One paid request, with no automatic retry or redirect. Never log keys or response bodies. */
class TryOnImageApi(private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun cancel() { activeConnection.get()?.disconnect() }

    suspend fun generate(apiKey: String, images: List<ByteArray>, prompt: String): ByteArray {
        require(apiKey.isNotBlank() && apiKey.length <= 512 && apiKey.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        require(images.size in 2..9 && images.all { it.isNotEmpty() && it.size <= 10 * 1024 * 1024 } && images.sumOf { it.size.toLong() } <= 40L * 1024 * 1024) { "参考图片数量或大小超出限制" }
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
                        throw IllegalStateException("生成等待超时，服务端可能仍在处理，请稍后检查后再决定是否重试")
                    } catch (error: IOException) {
                        throw IllegalStateException("网络连接中断，服务端可能仍在处理，请检查网络后再决定是否重试")
                    } finally {
                        activeConnection.compareAndSet(conn, null)
                        conn.disconnect()
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }, "lolita-tryon-request").apply { isDaemon = true }
            continuation.invokeOnCancellation { connection.get()?.disconnect(); worker.interrupt() }
            worker.start()
        }
    }

    private fun execute(conn: HttpURLConnection, key: String, images: List<ByteArray>, prompt: String): ByteArray {
        val boundary = "LolitaTryOn${UUID.randomUUID()}"
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 300_000
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setChunkedStreamingMode(64 * 1024)
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.outputStream.use { output ->
            fun text(value: String) { output.write(value.toByteArray(Charsets.UTF_8)) }
            for ((name, value) in listOf("model" to MODEL, "prompt" to prompt, "n" to "1", "quality" to "medium", "size" to "1024x1536", "output_format" to "png")) {
                text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            }
            images.forEachIndexed { index, bytes ->
                check(!Thread.currentThread().isInterrupted) { "已取消等待" }
                text("--$boundary\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"reference-$index.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                output.write(bytes)
                text("\r\n")
            }
            text("--$boundary--\r\n")
        }
        when (val code = conn.responseCode) {
            200 -> Unit
            401, 403 -> error("API Key 无效或没有图像模型权限，请检查服务配置")
            402 -> error("图像服务额度不足，请检查账户余额")
            429 -> error("图像服务繁忙或额度受限，请稍后主动重试")
            in 300..399 -> error("图像服务返回了重定向，已停止请求")
            else -> error("图像服务未完成请求（HTTP $code），请检查账户和图片后重试")
        }
        check(conn.contentLengthLong <= MAX_RESPONSE_BYTES) { "生成结果超出大小限制" }
        val json = conn.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8) }
        return try {
            val data = JsonParser.parseString(json).asJsonObject["data"].asJsonArray
            check(data.size() == 1)
            val encoded = data[0].asJsonObject["b64_json"].asString
            check(encoded.isNotBlank() && encoded.length <= 44 * 1024 * 1024)
            Base64.getDecoder().decode(encoded).also { check(it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES) }
        } catch (error: Exception) {
            throw IllegalStateException("图像服务没有返回有效图片，请稍后检查后重试")
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "已取消等待" }
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size().toLong() + count <= limit) { "生成结果超出大小限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MODEL = "gpt-image-2"
        const val ENDPOINT = "https://api.openai.com/v1/images/edits"
        private const val MAX_RESPONSE_BYTES = 48 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    }
}
