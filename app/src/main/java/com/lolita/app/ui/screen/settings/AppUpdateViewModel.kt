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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val showDialog: Boolean = false,
    val isChecking: Boolean = false,
    val update: ReleaseUpdate? = null,
    val error: String? = null
)

class AppUpdateViewModel(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val checkUpdate: suspend (String) -> ReleaseUpdate = AppModule.appUpdateRepository()::checkForUpdate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    val state = mutableState.asStateFlow()

    fun checkForUpdate() {
        if (mutableState.value.isChecking) {
            mutableState.value = mutableState.value.copy(showDialog = true)
            return
        }
        mutableState.value = AppUpdateUiState(showDialog = true, isChecking = true)
        viewModelScope.launch(dispatcher) {
            try {
                val update = checkUpdate(currentVersion)
                mutableState.value = mutableState.value.copy(update = update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is ReleaseCheckException, is IllegalArgumentException -> e.message ?: "发布信息无法识别，请前往 GitHub 查看"
                    is SocketTimeoutException -> "连接 GitHub 超时，请稍后重试"
                    else -> "检查更新失败，请检查网络或前往 GitHub 查看"
                }
                mutableState.value = mutableState.value.copy(error = message)
            } finally {
                mutableState.value = mutableState.value.copy(isChecking = false)
            }
        }
    }

    fun dismissDialog() {
        mutableState.value = mutableState.value.copy(showDialog = false)
    }
}
