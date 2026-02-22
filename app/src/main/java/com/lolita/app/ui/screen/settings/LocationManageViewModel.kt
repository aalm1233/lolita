package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationManageUiState(
    val locations: List<Location> = emptyList(),
    val locationItemCounts: Map<Long, Int> = emptyMap(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Location? = null,
    val deleteItemCount: Int = 0,
    val editingLocation: Location? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class LocationManageViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationManageUiState())
    val uiState: StateFlow<LocationManageUiState> = _uiState.asStateFlow()

    init {
        loadLocations()
    }

    private fun loadLocations() {
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { locations ->
                val counts = mutableMapOf<Long, Int>()
                locations.forEach { loc ->
                    counts[loc.id] = locationRepository.countItemsByLocation(loc.id)
                }
                _uiState.value = _uiState.value.copy(
                    locations = locations,
                    locationItemCounts = counts,
                    isLoading = false
                )
            }
        }
    }

    fun showAddDialog() { _uiState.update { it.copy(showAddDialog = true) } }
    fun hideAddDialog() { _uiState.update { it.copy(showAddDialog = false) } }
    fun showEditDialog(location: Location) { _uiState.update { it.copy(editingLocation = location) } }
    fun hideEditDialog() { _uiState.update { it.copy(editingLocation = null) } }

    fun showDeleteConfirm(location: Location) {
        viewModelScope.launch {
            val count = locationRepository.countItemsByLocation(location.id)
            _uiState.update { it.copy(showDeleteConfirm = location, deleteItemCount = count) }
        }
    }

    fun hideDeleteConfirm() { _uiState.update { it.copy(showDeleteConfirm = null) } }

    fun addLocation(name: String, description: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                locationRepository.insertLocation(
                    Location(name = name.trim(), description = description.trim(), imageUrl = imageUrl)
                )
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "添加失败：位置名称已存在") }
            }
        }
    }

    fun updateLocation(location: Location, name: String, description: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                val oldImageUrl = location.imageUrl
                if (oldImageUrl != null && oldImageUrl != imageUrl) {
                    ImageFileHelper.deleteImage(oldImageUrl)
                }
                locationRepository.updateLocation(
                    location.copy(name = name.trim(), description = description.trim(), imageUrl = imageUrl)
                )
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "更新失败") }
            }
        }
    }

    fun deleteLocation(location: Location) {
        viewModelScope.launch {
            try {
                locationRepository.deleteLocation(location)
                hideDeleteConfirm()
            } catch (e: Exception) {
                hideDeleteConfirm()
                _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}
