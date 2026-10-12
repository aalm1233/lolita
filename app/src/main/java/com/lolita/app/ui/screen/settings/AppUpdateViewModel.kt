package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.BuildConfig
import com.lolita.app.data.remote.ReleaseCheckException
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.ReleaseUpdate
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AppUpdateUiState(
    val showDialog: Boolean = false,
    val isChecking: Boolean = false,
    val update: ReleaseUpdate? = null,
    val error: String? = null
)

class AppUpdateViewModel(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val checkUpdate: suspend (String) -> ReleaseUpdate = AppModule.appUpdateRepository()::checkForUpdate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val canDownload: suspend (String) -> Boolean = { AppModule.appUpdateRepository().isDownloadReachable(it) }
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    val state = mutableState.asStateFlow()
    private var automaticCheckStarted = false
    private var interactiveCheck = false
    private var fetchedUpdate: ReleaseUpdate? = null
    private var checkJob: Job? = null
    private var checkId = 0

    fun checkAutomaticallyOnce() {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        if (!mutableState.value.isChecking) startCheck(interactive = false)
    }

    fun checkForUpdate() {
        if (mutableState.value.isChecking) {
            interactiveCheck = true
            val update = fetchedUpdate
            if (update != null) {
                // The release is ready; an automatic link probe must not delay a manual check.
                checkId++
                checkJob?.cancel()
                mutableState.value = mutableState.value.copy(
                    showDialog = true, isChecking = false, update = update, error = null
                )
            } else {
                mutableState.value = mutableState.value.copy(showDialog = true)
            }
            return
        }
        startCheck(interactive = true)
    }

    private fun startCheck(interactive: Boolean) {
        interactiveCheck = interactive
        fetchedUpdate = null
        val id = ++checkId
        mutableState.value = AppUpdateUiState(showDialog = interactive, isChecking = true)
        checkJob = viewModelScope.launch(dispatcher) {
            try {
                val completed = withTimeoutOrNull(15_000) {
                    val update = checkUpdate(currentVersion)
                    if (id == checkId) {
                        fetchedUpdate = update
                        val url = update.release.apkDownloadUrl
                        if (interactiveCheck) {
                            mutableState.value = mutableState.value.copy(update = update)
                        } else if (update.hasUpdate && url != null && canDownload(url) && id == checkId) {
                            mutableState.value = mutableState.value.copy(showDialog = true, update = update)
                        }
                    }
                    true
                }
                if (completed == null && id == checkId && interactiveCheck) {
                    mutableState.value = mutableState.value.copy(error = "连接 Gitee 超时，请稍后重试")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == checkId && interactiveCheck) {
                    val message = when (e) {
                        is ReleaseCheckException, is IllegalArgumentException -> e.message ?: "发布信息无法识别，请前往 Gitee 查看"
                        is SocketTimeoutException -> "连接 Gitee 超时，请稍后重试"
                        else -> "检查更新失败，请检查网络或前往 Gitee 查看"
                    }
                    mutableState.value = mutableState.value.copy(error = message)
                }
            } finally {
                if (id == checkId) mutableState.value = mutableState.value.copy(isChecking = false)
            }
        }
    }

    fun dismissDialog() {
        mutableState.value = mutableState.value.copy(showDialog = false)
    }
}
