package com.lolita.app.domain.model

data class AppRelease(
    val versionName: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?,
    val releaseNotes: String = ""
)

data class ReleaseUpdate(val release: AppRelease, val versionComparison: Int) {
    val hasUpdate: Boolean get() = versionComparison > 0
}
