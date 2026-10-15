package com.lolita.app.data.remote

import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseApiTest {
    @Test fun readsMultilineNotesAndAllowsMissingLegacyBody() {
        val notes = "## 更新\n- 后台任务\n- 人台图\n<script>作为文字保留</script>"
        val row = com.google.gson.JsonParser.parseString(payload()).asJsonObject.apply { addProperty("body", notes) }
        assertEquals(notes, GitHubReleaseApi.parseRelease(row.toString()).releaseNotes)
        row.add("body", com.google.gson.JsonNull.INSTANCE)
        assertEquals("", GitHubReleaseApi.parseRelease(row.toString()).releaseNotes)
        row.remove("body")
        assertEquals("", GitHubReleaseApi.parseRelease(row.toString()).releaseNotes)
    }

    @Test fun parsesExistingReleaseWithoutAnUpdateManifest() {
        val release = GitHubReleaseApi.parseRelease(payload(asset("lolita-2.39.5.apk")))
        assertEquals("2.39.5", release.versionName)
        assertEquals("https://github.com/aalm1233/lolita/releases/tag/v2.39.5", release.releaseUrl)
        assertEquals(download("lolita-2.39.5.apk"), release.apkDownloadUrl)
    }

    @Test fun prefersTheVersionedApkAmongSeveralAssets() {
        val release = GitHubReleaseApi.parseRelease(payload(asset("other.apk"), asset("lolita-2.39.5.apk")))
        assertEquals(download("lolita-2.39.5.apk"), release.apkDownloadUrl)
    }

    @Test fun fallsBackToTheOnlyUploadedApkButNotMultipleUnknownApks() {
        assertEquals(download("app-release.apk"), GitHubReleaseApi.parseRelease(payload(asset("app-release.apk"))).apkDownloadUrl)
        assertNull(GitHubReleaseApi.parseRelease(payload(asset("arm.apk"), asset("x86.apk"))).apkDownloadUrl)
        assertNull(GitHubReleaseApi.parseRelease(payload(asset("source.zip"))).apkDownloadUrl)
        assertNull(GitHubReleaseApi.parseRelease(payload(asset("app-release.apk", state = "starter"))).apkDownloadUrl)
    }

    @Test fun unsafeDownloadLinksNeverBecomeActions() {
        listOf(
            "http://github.com/aalm1233/lolita/releases/download/v2.39.5/app.apk",
            "https://evil.example/app.apk",
            "https://github.com/other/repo/releases/download/v2.39.5/app.apk",
            "https://github.com/aalm1233/lolita/releases/download/v2.39.4/app.apk",
            "https://user@github.com/aalm1233/lolita/releases/download/v2.39.5/app.apk",
            "https://github.com:444/aalm1233/lolita/releases/download/v2.39.5/app.apk"
        ).forEach { url ->
            assertNull(url, GitHubReleaseApi.parseRelease(payload(asset("app.apk", url))).apkDownloadUrl)
        }
    }

    @Test fun unsafeReleasePageUsesTheCanonicalRepositoryPage() {
        val json = payload().replace("https://github.com/aalm1233/lolita/releases/tag/v2.39.5", "https://evil.example")
        assertEquals("https://github.com/aalm1233/lolita/releases/tag/v2.39.5", GitHubReleaseApi.parseRelease(json).releaseUrl)
    }

    @Test fun rejectsDraftPrereleaseAndInvalidResponses() {
        listOf(
            payload().replace("\"draft\":false", "\"draft\":true"),
            payload().replace("\"prerelease\":false", "\"prerelease\":true"),
            payload().replace("v2.39.5", "latest"),
            payload().replace("\"draft\":false", "\"draft\":\"false\""),
            "{}", "null", "[]", "not json"
        ).forEach { json ->
            assertThrows(Exception::class.java) { GitHubReleaseApi.parseRelease(json) }
        }
    }

    private fun download(name: String) = "https://github.com/aalm1233/lolita/releases/download/v2.39.5/$name"
    private fun asset(name: String, url: String = download(name), state: String = "uploaded") =
        """{"name":"$name","state":"$state","browser_download_url":"$url"}"""
    private fun payload(vararg assets: String) =
        """{"tag_name":"v2.39.5","html_url":"https://github.com/aalm1233/lolita/releases/tag/v2.39.5","draft":false,"prerelease":false,"assets":[${assets.joinToString(",")}]}"""
}
