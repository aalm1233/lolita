package com.lolita.app.ui.screen.personalmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PersonalModelUiState(
    val models: List<PersonalModel> = emptyList(),
    val defaultId: Long? = null,
    val isLoading: Boolean = true,
    val mutating: Boolean = false,
    val error: String? = null
)

class PersonalModelViewModel @JvmOverloads constructor(
    models: Flow<List<PersonalModel>> = AppModule.personalModelRepository().observe(),
    defaultId: Flow<Long?> = AppModule.appPreferences().defaultPersonalModelId,
    private val setDefault: suspend (Long?) -> Unit = { AppModule.appPreferences().setDefaultPersonalModelId(it) },
    private val renameModel: suspend (Long, String) -> Unit = { id, name -> AppModule.personalModelRepository().rename(id, name) },
    private val deleteModel: suspend (Long) -> Unit = { AppModule.personalModelRepository().delete(it) }
) : ViewModel() {
    private val state = MutableStateFlow(PersonalModelUiState())
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(models, defaultId) { rows, preferred -> rows to preferred?.takeIf { id -> rows.any { it.id == id } } }
                .catch { e -> if (e is CancellationException) throw e; state.update { it.copy(isLoading = false, error = "模特库读取失败，请返回后重试") } }
                .collect { (rows, preferred) -> state.update { it.copy(models = rows, defaultId = preferred, isLoading = false) } }
        }
    }
    fun choose(id: Long?) {
        if (id != null && state.value.models.none { it.id == id }) return
        mutate { setDefault(id) }
    }
    fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > 60) { state.update { it.copy(error = "请填写1至60字的模特名称") }; return }
        mutate { renameModel(id, trimmed) }
    }
    fun delete(id: Long) {
        val wasDefault = state.value.defaultId == id
        mutate { deleteModel(id); if (wasDefault) setDefault(null) }
    }
    private fun mutate(operation: suspend () -> Unit) {
        if (state.value.mutating) return
        state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(error = "操作未完成，请重试") } }
            finally { state.update { it.copy(mutating = false) } }
        }
    }
}
