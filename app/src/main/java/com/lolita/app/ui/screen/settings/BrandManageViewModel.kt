package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.repository.BrandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrandManageUiState(
    val brands: List<Brand> = emptyList(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Brand? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BrandManageViewModel(
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrandManageUiState())
    val uiState: StateFlow<BrandManageUiState> = _uiState.asStateFlow()

    init {
        loadBrands()
    }

    fun loadBrands() {
        viewModelScope.launch {
            brandRepository.getAllBrands().collect { brands ->
                _uiState.value = _uiState.value.copy(
                    brands = brands,
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

    fun addBrand(name: String) {
        viewModelScope.launch {
            try {
                val brand = Brand(
                    name = name,
                    isPreset = false
                )
                brandRepository.insertBrand(brand)
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "添加失败：品牌名称已存在"
                )
                hideAddDialog()
            }
        }
    }

    fun showDeleteConfirm(brand: Brand) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = brand)
    }

    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun deleteBrand(brand: Brand) {
        viewModelScope.launch {
            try {
                brandRepository.deleteBrand(brand)
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
