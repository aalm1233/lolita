package com.lolita.app.ui.screen.settings

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.data.repository.LocationRepository
import com.lolita.app.data.repository.StorageRepository
import com.lolita.app.domain.model.StorageTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationManageUiState(
    val locations: List<Location> = emptyList(),
    val nodes: List<StorageNode> = emptyList(),
    val locationItemCounts: Map<Long, Int> = emptyMap(),
    val showAddDialog: Boolean = false,
    val showDeleteConfirm: Location? = null,
    val deleteItemCount: Int = 0,
    val editingLocation: Location? = null,
    val templates: List<StorageTemplate> = emptyList(),
    val showTemplates: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class LocationManageViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val storageRepository: StorageRepository = com.lolita.app.di.AppModule.storageRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocationManageUiState())
    val uiState = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            combine(locationRepository.getAllLocations(), locationRepository.getItemCountsByLocation(), storageRepository.observeNodes()) {
                locations, counts, nodes -> Triple(locations, counts.associate { it.locationId to it.count }, nodes)
            }.collect { (locations, counts, nodes) -> _uiState.update { it.copy(locations = locations,
                locationItemCounts = counts, nodes = nodes, isLoading = false,
                deleteItemCount = it.showDeleteConfirm?.let { room -> counts[room.id] ?: 0 } ?: 0) } }
        }
    }
    fun showAddDialog() { _uiState.update { it.copy(showAddDialog = true) } }
    fun hideAddDialog() { if (!_uiState.value.isSaving) _uiState.update { it.copy(showAddDialog = false) } }
    fun showEditDialog(location: Location) { _uiState.update { it.copy(editingLocation = location) } }
    fun hideEditDialog() { if (!_uiState.value.isSaving) _uiState.update { it.copy(editingLocation = null) } }
    fun showDeleteConfirm(location: Location) { _uiState.update { it.copy(showDeleteConfirm = location, deleteItemCount = it.locationItemCounts[location.id] ?: 0) } }
    fun hideDeleteConfirm() { if (!_uiState.value.isSaving) _uiState.update { it.copy(showDeleteConfirm = null) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    private fun save(action: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try { action() }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(errorMessage = uiErrorText(failure, "保存失败，请核对位置名称")) }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }
    fun addLocation(name: String, description: String, imageUrl: String?) = save {
        require(name.isNotBlank()) { "请填写房间名称" }
        locationRepository.insertLocation(Location(name = name.trim(), description = description.trim(), imageUrl = imageUrl))
        _uiState.update { it.copy(showAddDialog = false) }
    }
    fun updateLocation(location: Location, name: String, description: String, imageUrl: String?) = save {
        locationRepository.updateLocation(location.copy(name = name.trim(), description = description.trim(), imageUrl = imageUrl))
        _uiState.update { it.copy(editingLocation = null) }
    }
    fun deleteLocation(location: Location, clearContents: Boolean = false) = save {
        storageRepository.deleteRoom(location, clearContents)
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }
    fun showTemplates() = save {
        val templates = storageRepository.getTemplates()
        _uiState.update { it.copy(templates = templates, showTemplates = true) }
    }
    fun hideTemplates() { if (!_uiState.value.isSaving) _uiState.update { it.copy(showTemplates = false) } }
    fun adoptTemplates(roomId: Long, ids: Set<String>) = save {
        storageRepository.adoptTemplates(roomId, ids)
        _uiState.update { it.copy(showTemplates = false) }
    }
}
