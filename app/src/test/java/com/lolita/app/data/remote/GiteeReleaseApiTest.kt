package com.lolita.app.data.remote

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GiteeReleaseApiTest {
    @Test fun parsesActualGiteeFieldsWithoutGithubOnlyFieldsOrAssetSize() = runBlocking {
        val result = api(page(release("v2.41.0", asset("lolita-2.41.0.apk")))).fetchLatest()
        assertEquals("2.41.0", result.versionName)
        assertEquals("https://gitee.com/aalm1233/lolita/releases/v2.41.0", result.releaseUrl)
        assertEquals("https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/lolita-2.41.0.apk", result.apkDownloadUrl)
    }

    @Test fun choosesHighestNumericStableVersionInsteadOfMostRecentlyUpdatedRelease() = runBlocking {
        val result = api(page(release("v2.9.0"), release("v3.0.0", prerelease = true),
            release("v2.10.0"), release("v2.10.0-rc1"), release("nightly"))).fetchLatest()
        assertEquals("2.10.0", result.versionName)
    }

    @Test fun excludesMissingOrNonBooleanStableFlags() = runBlocking {
        val result = api(page(release("v2.41.0"), release("v9.0.0").replace("\"prerelease\":false", "\"prerelease\":\"false\""),
            release("v8.0.0").replace("\"prerelease\":false,", ""))).fetchLatest()
        assertEquals("2.41.0", result.versionName)
    }

    @Test fun findsHighestStableVersionOnAnotherPage() = runBlocking {
        val first = page(*Array(20) { release("v2.41.0") })
        assertEquals("2.42.0", api(first, page(release("v2.42.0"))).fetchLatest().versionName)
    }

    @Test fun refusesToGuessLatestVersionWhenReleaseHistoryExceedsBound() = runBlocking {
        val fullPage = page(*Array(20) { release("v2.41.0") })
        assertThrows(ReleaseCheckException::class.java) { runBlocking { api(*Array(5) { fullPage }).fetchLatest() } }
        Unit
    }

    @Test fun prefersVersionedApkAndFallsBackOnlyToAnUnambiguousApk() = runBlocking {
        assertEquals("https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/lolita-2.41.0.apk",
            api(page(release("v2.41.0", asset("arm.apk"), asset("lolita-2.41.0.apk")))).fetchLatest().apkDownloadUrl)
        assertEquals("https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/app-release.apk",
            api(page(release("v2.41.0", asset("app-release.apk"), asset("v2.41.0.zip")))).fetchLatest().apkDownloadUrl)
        assertNull(api(page(release("v2.41.0", asset("arm.apk"), asset("x86.apk")))).fetchLatest().apkDownloadUrl)
    }

    @Test fun neverUsesAnOlderApkWhenLatestReleaseHasNoApk() = runBlocking {
        val result = api(page(release("v2.41.0", asset("lolita-2.41.0.apk")), release("v2.42.0"))).fetchLatest()
        assertEquals("2.42.0", result.versionName)
        assertNull(result.apkDownloadUrl)
    }

    @Test fun rejectsDownloadLinksOutsideTheReleaseAndRepository() = runBlocking {
        listOf("http://gitee.com/aalm1233/lolita/releases/download/v2.41.0/app.apk", "https://example.com/app.apk",
            "https://gitee.com/other/lolita/releases/download/v2.41.0/app.apk",
            "https://gitee.com/aalm1233/lolita/releases/download/v2.40.0/app.apk",
            "https://user@gitee.com/aalm1233/lolita/releases/download/v2.41.0/app.apk",
            "https://gitee.com:444/aalm1233/lolita/releases/download/v2.41.0/app.apk",
            "https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/app.apk?download=1").forEach { url ->
            assertNull(url, api(page(release("v2.41.0", asset("app.apk", url)))).fetchLatest().apkDownloadUrl)
        }
    }

    @Test fun rejectsInvalidResponsesAndAbsenceOfStableReleases() = runBlocking {
        listOf("{}", "null", "not json", "[]", page(release("v2.42.0", prerelease = true))).forEach { body ->
            assertThrows(Exception::class.java) { runBlocking { api(body).fetchLatest() } }
        }
    }

    @Test fun rejectsHttpErrorsWithoutFollowingApiRedirects() = runBlocking {
        listOf(302, 403, 404, 429, 500).forEach { status ->
            assertThrows(ReleaseCheckException::class.java) { runBlocking { api("[]", status = status).fetchLatest() } }
        }
    }

    @Test fun rejectsOversizedMetadataBeforeParsingIt() = runBlocking {
        assertThrows(ReleaseCheckException::class.java) { runBlocking { api(" ".repeat(1_048_577)).fetchLatest() } }
        Unit
    }

    private fun api(vararg pages: String, status: Int = 200): GiteeReleaseApi = GiteeReleaseApi { url ->
        val index = Regex("[?&]page=([0-9]+)").find(url.toString())!!.groupValues[1].toInt() - 1
        Response(url, status, pages[index])
    }
    private fun page(vararg releases: String) = "[${releases.joinToString(",")}]"
    private fun release(tag: String, vararg assets: String, prerelease: Boolean = false) =
        """{"id":1200777,"tag_name":"$tag","target_commitish":"dfd1b3","prerelease":$prerelease,"name":"Lolita $tag","body":"release notes","author":{"id":6542644,"login":"aalm1233"},"created_at":"2026-10-12T23:48:27+08:00","assets":[${assets.joinToString(",") }]}"""
    private fun asset(name: String, url: String = "https://gitee.com/aalm1233/lolita/releases/download/v2.41.0/$name") =
        """{"name":"$name","browser_download_url":"$url"}"""

    private class Response(url: URL, private val status: Int, body: String) : HttpURLConnection(url) {
        private val content = body.toByteArray(Charsets.UTF_8)
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(content)
        override fun getContentLengthLong() = content.size.toLong()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }
}
