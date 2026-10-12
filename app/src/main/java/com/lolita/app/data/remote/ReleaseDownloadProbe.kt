package com.lolita.app.data.remote

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ReleaseDownloadProbe(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun isReachable(url: String): Boolean = withTimeoutOrNull(8_000) {
        withContext(Dispatchers.IO) {
            try {
                var target = URI(url)
                val isGiteeApk = target.host == "gitee.com" && target.path.startsWith("/aalm1233/lolita/releases/download/") &&
                    target.path.endsWith(".apk", ignoreCase = true)
                val visited = mutableSetOf<URI>()
                repeat(4) {
                    ensureActive()
                    if (!trusted(target) || !visited.add(target)) return@withContext false
                    val connection = openConnection(target.toURL()).apply {
                        requestMethod = "HEAD"
                        connectTimeout = 3_000
                        readTimeout = 3_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Lolita-Android")
                    }
                    try {
                        when (connection.responseCode) {
                            200 -> {
                                ensureActive()
                                val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                                val isBinary = type in setOf("application/vnd.android.package-archive", "application/octet-stream") ||
                                    (isGiteeApk && type == "application/zip")
                                return@withContext isBinary &&
                                    connection.contentLengthLong > 0
                            }
                            301, 302, 303, 307, 308 -> {
                                val location = connection.getHeaderField("Location") ?: return@withContext false
                                target = target.resolve(location)
                            }
                            else -> return@withContext false
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    } ?: false

    private fun trusted(uri: URI): Boolean = uri.scheme == "https" && uri.userInfo == null &&
        uri.port in listOf(-1, 443) && uri.fragment == null &&
        uri.host in setOf("gitee.com", "foruda.gitee.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}
