package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.domain.model.*
import com.lolita.app.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryManageUiState(
    val categories: List<Category> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Category? = null,
    val editingCategory: Category? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingSemanticChange: Category? = null,
    val affectedItemCount: Int = 0
)

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManageUiState())
    val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
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

    fun addCategory(name: String, group: CategoryGroup = CategoryGroup.CLOTHING, semantic: CategorySemantic = CategorySemantic.UNCONFIRMED, role: TryOnRole? = null) {
        viewModelScope.launch {
            try {
                require(semantic != CategorySemantic.UNCONFIRMED) { "请选择品类与默认穿戴位置" }
                val category = Category(
                    name = name.trim(),
                    group = group, categorySemantic = semantic, defaultWearRole = role,
                    isPreset = false
                )
                categoryRepository.insertCategory(category)
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "添加失败"
                )
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

    fun showEditDialog(category: Category) {
        _uiState.value = _uiState.value.copy(editingCategory = category)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(editingCategory = null)
    }

    fun updateCategory(category: Category, newName: String, newGroup: CategoryGroup, semantic: CategorySemantic = category.categorySemantic, role: TryOnRole? = category.defaultWearRole) {
        viewModelScope.launch {
            try {
                val updated = category.copy(name = newName.trim(), group = newGroup, categorySemantic = semantic, defaultWearRole = role)
                val count = categoryRepository.affectedItemCount(category.id)
                if (count > 0 && (semantic != category.categorySemantic || role != category.defaultWearRole)) {
                    _uiState.value = _uiState.value.copy(pendingSemanticChange = updated, affectedItemCount = count)
                } else {
                    categoryRepository.updateCategory(updated)
                    hideEditDialog()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "修改失败"
                )
            }
        }
    }
    fun cancelSemanticChange() { _uiState.value = _uiState.value.copy(pendingSemanticChange = null) }
    fun confirmSemanticChange() {
        val category = _uiState.value.pendingSemanticChange ?: return
        viewModelScope.launch {
            try {
                categoryRepository.updateCategory(category, confirmSemanticChange = true)
                _uiState.value = _uiState.value.copy(pendingSemanticChange = null, editingCategory = null)
            } catch (failure: Exception) { _uiState.value = _uiState.value.copy(errorMessage = failure.message) }
        }
    }
}
