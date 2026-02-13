package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryManageUiState(
    val categories: List<Category> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Category? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManageUiState())
    val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    isLoading = false
                )
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            try {
                val category = Category(
                    name = name,
                    isPreset = false
                )
                categoryRepository.insertCategory(category)
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "添加失败：类型名称已存在"
                )
                hideAddDialog()
            }
        }
    }

    fun showDeleteConfirm(category: Category) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = category)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(category)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "删除失败"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
