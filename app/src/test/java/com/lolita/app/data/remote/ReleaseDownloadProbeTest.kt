package com.lolita.app.data.remote

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReleaseDownloadProbeTest {
    @Test fun acceptsTheRealGiteeApkRedirectAndZipContentType() = runBlocking {
        val stable = "https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/lolita-2.41.0.apk"
        val cdn = "https://foruda.gitee.com/attach_file/1/lolita-2.41.0.apk?token=test"
        val connections = mapOf(stable to Response(stable, 302, location = cdn),
            cdn to Response(cdn, 200, type = "application/zip"))
        assertTrue(ReleaseDownloadProbe { connections.getValue(it.toString()) }.isReachable(stable))
    }

    @Test fun followsAGitHubRedirectToAReachableBinaryWithoutDownloadingIt() = runBlocking {
        val cdn = "https://release-assets.githubusercontent.com/github-production-release-asset/123/asset?signature=test"
        val connections = mapOf(
            apk to Response(apk, 302, location = cdn),
            cdn to Response(cdn, 200)
        )
        val probe = ReleaseDownloadProbe { connections.getValue(it.toString()) }
        assertTrue(probe.isReachable(apk))
        assertTrue(connections.values.all { it.requestMethod == "HEAD" && it.disconnected })
    }

    @Test fun rejectsMissingFilesHtmlPagesEmptyFilesAndAccessErrors() = runBlocking {
        val responses = listOf(
            Response(apk, 404), Response(apk, 403), Response(apk, 500),
            Response(apk, 200, type = "text/html; charset=utf-8"),
            Response(apk, 200, type = "application/zip"),
            Response(apk, 200, type = "application/xml"),
            Response(apk, 200, length = 0)
        )
        for (response in responses) {
            assertFalse(ReleaseDownloadProbe { response }.isReachable(apk))
            assertTrue(response.disconnected)
        }
    }

    @Test fun rejectsRedirectsToHttpUntrustedHostsAndCredentialUrls() = runBlocking {
        val targets = listOf("http://release-assets.githubusercontent.com/file", "https://example.com/file",
            "https://github.com.evil.example/file", "https://foruda.gitee.com.evil.example/file",
            "https://user@github.com/file", "https://github.com:444/file")
        for (target in targets) {
            assertFalse(ReleaseDownloadProbe { Response(it.toString(), 302, location = target) }.isReachable(apk))
        }
    }

    @Test fun stopsRedirectLoopsAndRejectsRedirectsWithoutALocation() = runBlocking {
        var requests = 0
        assertFalse(ReleaseDownloadProbe {
            requests++
            Response(it.toString(), 302, location = apk)
        }.isReachable(apk))
        assertTrue(requests <= 4)
        assertFalse(ReleaseDownloadProbe { Response(apk, 302) }.isReachable(apk))
    }

    @Test fun networkFailuresAreReportedAsAnUnreachableLink() = runBlocking {
        assertFalse(ReleaseDownloadProbe { throw IOException("offline") }.isReachable(apk))
    }

    @Test fun rejectsAnUntrustedInitialLinkBeforeConnecting() = runBlocking {
        assertFalse(ReleaseDownloadProbe { error("Must not connect to an untrusted source") }
            .isReachable("https://example.com/app.apk"))
    }

    private val apk = "https://github.com/aalm1233/lolita/releases/download/v2.42.0/lolita-2.42.0.apk"

    private class Response(
        url: String,
        private val status: Int,
        private val location: String? = null,
        private val type: String = "application/octet-stream",
        private val length: Long = 1234
    ) : HttpURLConnection(URL(url)) {
        var disconnected = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? = when (name.lowercase()) {
            "location" -> location
            "content-type" -> type
            "content-length" -> length.toString()
            else -> null
        }
        override fun getContentType() = type
        override fun getContentLengthLong() = length
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
    }
}
