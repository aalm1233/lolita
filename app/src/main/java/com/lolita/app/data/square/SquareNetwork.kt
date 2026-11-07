package com.lolita.app.data.square

import android.graphics.BitmapFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

/** Network failures are deliberately excluded from user-facing message flows. */
class SquareNetworkException : IOException("Square service unavailable")

// LAN verification phase. Stored user overrides (including an explicit blank) take precedence.
const val DEFAULT_SQUARE_SERVICE_URL = "http://192.168.0.104:18765"

fun normalizeSquareServiceUrl(value: String): String {
    val text = value.trim()
    if (text.isEmpty()) return ""
    require(text.length <= 2000 && text.none { it.isWhitespace() || it.code < 32 }) { "请输入有效的服务地址" }
    val uri = runCatching { URI(text) }.getOrNull()
    require(uri != null && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
        uri.path in listOf("", "/") && (uri.port == -1 || uri.port in 1..65535)) { "请填写服务根地址，不包含账号、路径或参数" }
    val host = uri.host.lowercase()
    val parts = host.split('.')
    val octets = parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
    val local = host == "localhost" || (parts.size == 4 && octets.size == 4 && parts.all { it.matches(Regex("[0-9]{1,3}")) } && (octets[0] == 10 || octets[0] == 127 ||
        octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] in 16..31))
    require(uri.scheme == "https" || uri.scheme == "http" && local) { "内网可使用HTTP，其他服务请使用HTTPS" }
    return uri.toASCIIString().trimEnd('/')
}

data class SquareChange(val seq: Long, val catalog: SquareCatalog)
data class SquareChangePage(val serverId: String, val cursor: Long, val hasMore: Boolean, val changes: List<SquareChange>)

class SquareNetworkClient {
    private fun connection(base: String, path: String): HttpURLConnection {
        require(normalizeSquareServiceUrl(base) == base && base.isNotEmpty())
        return (URI(base + path).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500; readTimeout = 4000; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
    }
    private suspend fun bytes(c: HttpURLConnection, max: Long): ByteArray {
        require(c.responseCode == 200 && c.contentLengthLong <= max)
        return c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536); var count = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer); if (n < 0) break
                count += n; require(count <= max); out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }
    private suspend fun json(base: String, path: String): com.google.gson.JsonObject = withContext(Dispatchers.IO) {
        val c = connection(base, path)
        try { JsonParser.parseString(bytes(c, 32L * 1024 * 1024).toString(Charsets.UTF_8)).asJsonObject }
        finally { c.disconnect() }
    }
    suspend fun serverId(base: String): String {
        val root = json(base, "/v1/health")
        require(root["format"].asString == "lolita.square.api" && root["schemaVersion"].asInt == 1)
        return root["serverId"].asString.also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,100}"))) }
    }
    suspend fun changes(base: String, after: Long): SquareChangePage {
        val root = json(base, "/v1/square/changes?after=$after&limit=10")
        val cursor = root["cursor"].asBigDecimal.longValueExact()
        val rows = root["changes"].asJsonArray.map {
            val row = it.asJsonObject
            SquareChange(row["seq"].asBigDecimal.longValueExact(), SquarePackage.decode(row["catalog"].toString(), network = true))
        }
        val hasMore = root["hasMore"].asBoolean
        require(rows.size <= 10 && rows.zipWithNext().all { (a,b) -> b.seq > a.seq } &&
            rows.all { it.seq > after } && cursor == (rows.lastOrNull()?.seq ?: after) && (!hasMore || rows.isNotEmpty()))
        return SquareChangePage(root["serverId"].asString, cursor, hasMore, rows)
    }
    suspend fun image(base: String, asset: SquareAsset, thumbnail: Boolean, destination: File): File = withContext(Dispatchers.IO) {
        require(asset.path.matches(Regex("images/[a-f0-9]{64}\\.(jpg|jpeg|png|webp)")))
        val c = connection(base, "/v1/" + asset.path + if (thumbnail) "?variant=thumbnail" else "?variant=original")
        val temp = File(destination.parentFile!!.apply { mkdirs() }, ".pending-${UUID.randomUUID()}")
        try {
            val content = bytes(c, if (thumbnail) 4L * 1024 * 1024 else asset.bytes)
            val expected = if (thumbnail) c.getHeaderField("X-Content-SHA256") else asset.sha256
            require(expected != null && expected.matches(Regex("[a-f0-9]{64}")))
            require(thumbnail || content.size.toLong() == asset.bytes)
            require(MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) } == expected)
            temp.writeBytes(content)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, options)
            require(options.outMimeType in setOf("image/jpeg", "image/png", "image/webp"))
            require(if (thumbnail) options.outWidth in 1..600 && options.outHeight in 1..800
                else options.outWidth == asset.width && options.outHeight == asset.height)
            require(temp.renameTo(destination))
            destination
        } finally { c.disconnect(); temp.delete() }
    }
}
