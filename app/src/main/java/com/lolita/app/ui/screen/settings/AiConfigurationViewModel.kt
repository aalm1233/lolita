package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*

data class AiConfigurationUiState(
    val configuration: AiConfigurationSnapshot = AiConfigurationSnapshot(),
    val isLoading: Boolean = true,
    val busy: Boolean = false,
    val editing: AiKeySlot? = null,
    val error: String? = null,
    val message: String? = null
)

class AiConfigurationViewModel(private val store: AiConfigurationStore = AppModule.aiConfigurationRepository()) : ViewModel() {
    private val state = MutableStateFlow(AiConfigurationUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch { store.changes.collect {
            try {
                val snapshot = withContext(Dispatchers.IO) { store.read() }
                state.update { it.copy(configuration = snapshot, isLoading = false) }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { state.update { it.copy(isLoading = false, error = "配置读取失败，请返回后重试") } }
        } }
    }

    fun edit(slot: AiKeySlot) {
        if (!state.value.busy && !state.value.isLoading) state.update { it.copy(editing = slot, error = null, message = null) }
    }
    fun dismissEditor() {
        if (!state.value.busy) state.update { it.copy(editing = null, error = null) }
    }
    fun saveKey(slot: AiKeySlot, input: String) {
        if (state.value.editing != slot || state.value.busy) return
        val key = input.trim()
        if (key.isBlank() || key.length > 512 || key.any { it.code !in 33..126 }) {
            state.update { it.copy(error = "请填写有效的 API Key") }; return
        }
        change("${slot.label}密钥已保存", closeEditor = true) { store.save(slot, key) }
    }
    fun clearKey(slot: AiKeySlot) {
        if (state.value.editing == slot) change("${slot.label}密钥已移除", closeEditor = true) { store.clear(slot) }
    }
    fun select(slot: AiKeySlot) = change("默认${slot.purpose}服务已切换", closeEditor = false) { store.select(slot) }

    private fun change(message: String, closeEditor: Boolean, operation: () -> Unit) {
        if (state.value.busy || state.value.isLoading) return
        state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { operation(); store.read() }
                state.update { it.copy(configuration = snapshot, editing = if (closeEditor) null else it.editing, message = message) }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { state.update { it.copy(error = "配置修改未完成，请重试") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}
