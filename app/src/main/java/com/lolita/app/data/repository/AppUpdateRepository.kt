package com.lolita.app.data.repository

import com.lolita.app.data.remote.GitHubReleaseApi
import com.lolita.app.domain.model.ReleaseUpdate
import com.lolita.app.domain.usecase.ReleaseVersion

class AppUpdateRepository(private val api: GitHubReleaseApi = GitHubReleaseApi()) {
    suspend fun checkForUpdate(currentVersion: String): ReleaseUpdate {
        val release = api.fetchLatest()
        return ReleaseUpdate(release, ReleaseVersion.compare(release.versionName, currentVersion))
    }
}
