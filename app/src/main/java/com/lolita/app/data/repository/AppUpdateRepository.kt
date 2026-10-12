package com.lolita.app.data.repository

import com.lolita.app.data.remote.GiteeReleaseApi
import com.lolita.app.data.remote.ReleaseDownloadProbe
import com.lolita.app.domain.model.ReleaseUpdate
import com.lolita.app.domain.usecase.ReleaseVersion

class AppUpdateRepository(private val api: GiteeReleaseApi = GiteeReleaseApi()) {
    private val downloadProbe = ReleaseDownloadProbe()

    suspend fun isDownloadReachable(url: String): Boolean = downloadProbe.isReachable(url)

    suspend fun checkForUpdate(currentVersion: String): ReleaseUpdate {
        val release = api.fetchLatest()
        return ReleaseUpdate(release, ReleaseVersion.compare(release.versionName, currentVersion))
    }
}
