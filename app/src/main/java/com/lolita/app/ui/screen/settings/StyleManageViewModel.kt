package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Style
import com.lolita.app.data.repository.StyleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StyleManageUiState(
    val styles: List<Style> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Style? = null,
    val editingStyle: Style? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class StyleManageViewModel(
    private val styleRepository: StyleRepository = com.lolita.app.di.AppModule.styleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StyleManageUiState())
    val uiState: StateFlow<StyleManageUiState> = _uiState.asStateFlow()

    init {
        loadStyles()
    }

    fun loadStyles() {
        viewModelScope.launch {
            styleRepository.getAllStyles().collect { styles ->
                _uiState.value = _uiState.value.copy(styles = styles, isLoading = false)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addStyle(name: String) {
        viewModelScope.launch {
            try {
                styleRepository.insertStyle(Style(name = name.trim(), isPreset = false))
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "添加失败：风格名称已存在")
            }
        }
    }

    fun showDeleteConfirm(style: Style) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = style)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun deleteStyle(style: Style) {
        viewModelScope.launch {
            try {
                styleRepository.deleteStyle(style)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "删除失败")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showEditDialog(style: Style) {
        _uiState.value = _uiState.value.copy(editingStyle = style)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(editingStyle = null)
    }

    fun updateStyle(style: Style, newName: String) {
        viewModelScope.launch {
            try {
                styleRepository.updateStyle(style.copy(name = newName.trim()), oldName = style.name)
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "重命名失败：风格名称已存在"
                )
            }
        }
    }
}
