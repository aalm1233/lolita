package com.lolita.app.data.remote

import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.usecase.ReleaseVersion
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class GitHubReleaseApi {
    suspend fun fetchLatest(): AppRelease = withContext(Dispatchers.IO) {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Lolita-Android")
        }
        try {
            when (val status = connection.responseCode) {
                200 -> Unit
                404 -> throw ReleaseCheckException("暂未找到正式发布版本，可前往 GitHub 查看")
                403, 429 -> throw ReleaseCheckException("GitHub 暂时拒绝访问或请求过于频繁，请稍后重试")
                else -> throw ReleaseCheckException("检查更新失败（HTTP $status），请稍后重试")
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_048_576) throw ReleaseCheckException("发布信息过大，请前往 GitHub 查看")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            parseRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASES_URL = "https://github.com/aalm1233/lolita/releases/latest"
        private const val API_URL = "https://api.github.com/repos/aalm1233/lolita/releases/latest"
        private const val RELEASE_PATH = "/aalm1233/lolita/releases"

        internal fun parseRelease(json: String): AppRelease {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.boolean("draft") == false && root.boolean("prerelease") == false) {
                "暂未找到正式发布版本，请前往 GitHub 查看"
            }
            val tag = root.string("tag_name") ?: throw IllegalArgumentException("发布版本信息不完整")
            ReleaseVersion.compare(tag, tag) // Validate the supported stable version format.
            val version = tag.removePrefix("v")
            val pagePath = "$RELEASE_PATH/tag/$tag"
            val releaseUrl = root.string("html_url")?.takeIf { trustedUrl(it, pagePath) }
                ?: "https://github.com$pagePath"
            val assets = root.getAsJsonArray("assets")?.mapNotNull { element ->
                val asset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val name = asset.string("name") ?: return@mapNotNull null
                val url = asset.string("browser_download_url") ?: return@mapNotNull null
                if (asset.string("state") != "uploaded" || !name.endsWith(".apk", ignoreCase = true) ||
                    name.contains('/') || name.contains('\\') ||
                    !trustedUrl(url, "$RELEASE_PATH/download/$tag/$name")) return@mapNotNull null
                name to url
            }.orEmpty()
            val preferred = assets.filter { it.first == "lolita-$version.apk" }
            val downloadUrl = when {
                preferred.size == 1 -> preferred.single().second
                preferred.isEmpty() && assets.size == 1 -> assets.single().second
                else -> null
            }
            return AppRelease(version, releaseUrl, downloadUrl)
        }

        private fun JsonObject.string(key: String): String? = get(key)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.boolean(key: String): Boolean? = get(key)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

        private fun trustedUrl(url: String, expectedPath: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
                uri.port in listOf(-1, 443) && uri.path == expectedPath &&
                uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }
}

internal class ReleaseCheckException(message: String) : IOException(message)
