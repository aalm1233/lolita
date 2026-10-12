package com.lolita.app.data.remote

import com.lolita.app.domain.model.AppRelease
import com.lolita.app.domain.usecase.ReleaseVersion
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class GiteeReleaseApi(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun fetchLatest(): AppRelease = withTimeout(15_000) {
        withContext(Dispatchers.IO) {
            var latest: AppRelease? = null
            var remainingBytes = 1_048_576
            repeat(5) { page ->
                ensureActive()
                val connection = openConnection(URL("$API_URL?page=${page + 1}&per_page=20")).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Lolita-Android")
                }
                val body = try {
                    when (val status = connection.responseCode) {
                        200 -> Unit
                        404 -> throw ReleaseCheckException("暂未找到正式发布版本，可前往 Gitee 查看")
                        403, 429 -> throw ReleaseCheckException("Gitee 暂时拒绝访问或请求过于频繁，请稍后重试")
                        else -> throw ReleaseCheckException("检查更新失败（HTTP $status），请稍后重试")
                    }
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count > remainingBytes) throw ReleaseCheckException("发布信息过大，请前往 Gitee 查看")
                            remainingBytes -= count
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                } finally {
                    connection.disconnect()
                }
                val releases = JsonParser.parseString(body).asJsonArray
                if (releases.size() > 20) throw ReleaseCheckException("发布信息异常，请前往 Gitee 查看")
                for (element in releases) {
                    val release = element.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseRelease) ?: continue
                    if (latest == null || ReleaseVersion.compare(release.versionName, latest!!.versionName) > 0) {
                        latest = release
                    }
                }
                if (releases.size() < 20) return@withContext latest
                    ?: throw ReleaseCheckException("暂未找到正式发布版本，可前往 Gitee 查看")
            }
            throw ReleaseCheckException("发布记录过多，请前往 Gitee 查看最新版本")
        }
    }

    companion object {
        const val RELEASES_URL = "https://gitee.com/aalm1233/lolita/releases"
        private const val API_URL = "https://gitee.com/api/v5/repos/aalm1233/lolita/releases"
        private const val RELEASE_PATH = "/aalm1233/lolita/releases"

        private fun parseRelease(root: JsonObject): AppRelease? {
            if (root.get("prerelease")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean != false) return null
            val tag = root.string("tag_name") ?: return null
            if (runCatching { ReleaseVersion.compare(tag, tag) }.isFailure) return null
            val version = tag.removePrefix("v")
            val assets = root.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
                val asset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val name = asset.string("name") ?: return@mapNotNull null
                val url = asset.string("browser_download_url") ?: return@mapNotNull null
                if (!name.endsWith(".apk", ignoreCase = true) || name.contains('/') || name.contains('\\') ||
                    !trustedUrl(url, "$RELEASE_PATH/download/$tag/$name")) return@mapNotNull null
                name to url
            }.orEmpty()
            val preferred = assets.filter { it.first == "lolita-$version.apk" }
            val downloadUrl = when {
                preferred.size == 1 -> preferred.single().second
                preferred.isEmpty() && assets.size == 1 -> assets.single().second
                else -> null
            }
            return AppRelease(version, "https://gitee.com$RELEASE_PATH/$tag", downloadUrl)
        }

        private fun JsonObject.string(key: String): String? = get(key)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun trustedUrl(url: String, expectedPath: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "gitee.com" && uri.userInfo == null &&
                uri.port in listOf(-1, 443) && uri.path == expectedPath && uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }
}
