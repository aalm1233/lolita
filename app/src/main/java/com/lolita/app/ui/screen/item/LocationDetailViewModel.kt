package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.domain.model.StorageTemplate
import com.lolita.app.domain.model.StorageTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationDetailUiState(
    val location: Location? = null,
    val locations: List<Location> = emptyList(),
    val nodes: List<StorageNode> = emptyList(),
    val currentNodeId: Long? = null,
    val childNodes: List<StorageNode> = emptyList(),
    val nodeItemCounts: Map<Long, Int> = emptyMap(),
    val currentPath: String = "",
    val recordCount: Int = 0,
    val items: List<Item> = emptyList(),
    val allItems: List<Item> = emptyList(),
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val selectedItemIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val templates: List<StorageTemplate> = emptyList(),
    val showTemplates: Boolean = false,
    val isUnassigned: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class LocationDetailViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val storageRepository: StorageRepository = com.lolita.app.di.AppModule.storageRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocationDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var roomId = -1L
    private var loadJob: Job? = null

    fun loadLocation(locationId: Long) {
        if (loadJob != null && roomId == locationId) return
        loadJob?.cancel()
        roomId = locationId
        _uiState.value = LocationDetailUiState(isUnassigned = locationId == -1L)
        loadJob = viewModelScope.launch {
            combine(locationRepository.getAllLocations(), storageRepository.observeNodes(), itemRepository.getAllItems(),
                brandRepository.getAllBrands(), categoryRepository.getAllCategories()) { rooms, nodes, items, brands, categories ->
                _uiState.value.copy(location = rooms.find { it.id == roomId }, locations = rooms, nodes = nodes,
                    allItems = items, brandNames = brands.associate { it.id to it.name },
                    categoryNames = categories.associate { it.id to it.name }, isLoading = false)
            }.collect { incoming -> _uiState.value = derive(incoming) }
        }
    }

    private fun derive(state: LocationDetailUiState): LocationDetailUiState {
        val nodeId = state.currentNodeId?.takeIf { id -> state.nodes.any { it.id == id && it.locationId == roomId } }
        val ids = nodeId?.let { StorageTree.descendantIds(it, state.nodes) }
        val ownedItems = state.allItems.filter { it.status.supportsStorage }
        val roomItems = ownedItems.filter { if (state.isUnassigned) it.locationId == null else it.locationId == roomId }
        val contained = if (ids == null) roomItems else roomItems.filter { it.storageNodeId in ids }
        val query = state.searchQuery.trim()
        val displayed = contained.filter { item ->
            val belongsHere = state.isUnassigned || nodeId != null || item.storageNodeId == null || query.isNotBlank()
            val matches = query.isBlank() || listOf(item.name, state.brandNames[item.brandId].orEmpty(),
                state.categoryNames[item.categoryId].orEmpty(), item.colors.joinToString(" "),
                StorageTree.pathFor(item.locationId, item.storageNodeId, state.locations, state.nodes))
                .any { it.contains(query, ignoreCase = true) }
            belongsHere && matches
        }
        val counts = state.nodes.associate { node ->
            val descendants = StorageTree.descendantIds(node.id, state.nodes)
            node.id to ownedItems.count { it.storageNodeId in descendants }
        }
        return state.copy(currentNodeId = nodeId, items = displayed,
            childNodes = state.nodes.filter { it.locationId == roomId && it.parentNodeId == nodeId &&
                (query.isBlank() || StorageTree.pathFor(roomId, it.id, state.locations, state.nodes).contains(query, true)) },
            currentPath = StorageTree.pathFor(if (state.isUnassigned) null else roomId, nodeId, state.locations, state.nodes),
            recordCount = contained.size, nodeItemCounts = counts,
            selectedItemIds = state.selectedItemIds.intersect(ownedItems.map { it.id }.toSet()))
    }

    fun openNode(id: Long?) { _uiState.update { derive(it.copy(currentNodeId = id, searchQuery = "", selectedItemIds = emptySet())) } }
    fun openParent(): Boolean {
        val id = _uiState.value.currentNodeId ?: return false
        openNode(_uiState.value.nodes.find { it.id == id }?.parentNodeId)
        return true
    }
    fun updateSearch(query: String) { _uiState.update { derive(it.copy(searchQuery = query)) } }
    fun toggleSelection(id: Long) { _uiState.update { it.copy(selectedItemIds = if (id in it.selectedItemIds) it.selectedItemIds - id else it.selectedItemIds + id) } }
    fun clearSelection() { _uiState.update { it.copy(selectedItemIds = emptySet()) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    private fun save(onComplete: () -> Unit = {}, action: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try { action(); onComplete() }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(errorMessage = failure.message ?: "保存失败，请重试") }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun createNode(name: String, description: String, image: String?, onComplete: () -> Unit) = save(onComplete) {
        storageRepository.createNode(roomId, _uiState.value.currentNodeId, name, description, image)
    }
    fun updateNode(node: StorageNode, name: String, description: String, image: String?, onComplete: () -> Unit) = save(onComplete) {
        storageRepository.updateNode(node.copy(name = name, description = description, imageUrl = image))
    }
    fun deleteNode(node: StorageNode, clearContents: Boolean, onComplete: () -> Unit) = save(onComplete) {
        storageRepository.deleteNode(node.id, clearContents)
    }
    fun moveNode(node: StorageNode, locationId: Long, parentNodeId: Long?, onComplete: () -> Unit) = save(onComplete) {
        storageRepository.moveNode(node.id, locationId, parentNodeId)
    }
    fun moveSelected(locationId: Long?, nodeId: Long?, onComplete: () -> Unit) {
        val selected = _uiState.value.selectedItemIds.toSet()
        save({ clearSelection(); onComplete() }) { storageRepository.moveItems(selected, locationId, nodeId) }
    }
    fun addItems(itemIds: Set<Long>, onComplete: () -> Unit) = save(onComplete) {
        storageRepository.moveItems(itemIds, roomId, _uiState.value.currentNodeId)
    }
    fun showTemplates() = save {
        val templates = storageRepository.getTemplates()
        _uiState.update { it.copy(templates = templates, showTemplates = true) }
    }
    fun hideTemplates() { if (!_uiState.value.isSaving) _uiState.update { it.copy(showTemplates = false) } }
    fun adoptTemplates(locationId: Long, ids: Set<String>) = save({ _uiState.update { it.copy(showTemplates = false) } }) {
        storageRepository.adoptTemplates(locationId, ids)
    }
}
