package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class LocationDetailUiState(
    val location: Location? = null,
    val items: List<Item> = emptyList(),
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val isUnassigned: Boolean = false,
    val isLoading: Boolean = true,
    // Item picker state
    val allItems: List<Item> = emptyList(),
    val locationNames: Map<Long, String> = emptyMap(),
    val pickerSelectedItemIds: Set<Long> = emptySet(),
    val pickerSearchQuery: String = ""
)

class LocationDetailViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationDetailUiState())
    val uiState: StateFlow<LocationDetailUiState> = _uiState.asStateFlow()

    fun loadLocation(locationId: Long) {
        if (locationId == -1L) {
            _uiState.update { it.copy(isUnassigned = true, isLoading = false) }
            viewModelScope.launch {
                locationRepository.getItemsWithNoLocation().collect { items ->
                    _uiState.update { it.copy(items = items) }
                }
            }
        } else {
            viewModelScope.launch {
                locationRepository.getLocationById(locationId).collect { loc ->
                    _uiState.update { it.copy(location = loc, isLoading = false) }
                }
            }
            viewModelScope.launch {
                locationRepository.getItemsByLocationId(locationId).collect { items ->
                    _uiState.update { it.copy(items = items) }
                }
            }
        }
        viewModelScope.launch {
            brandRepository.getAllBrands().collect { brands ->
                _uiState.update { it.copy(brandNames = brands.associate { b -> b.id to b.name }) }
            }
        }
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categoryNames = cats.associate { c -> c.id to c.name }) }
            }
        }
    }

    fun loadAllItemsForPicker() {
        viewModelScope.launch {
            val items = itemRepository.getAllItems().first()
            val locations = locationRepository.getAllLocations().first()
            val brands = brandRepository.getAllBrands().first()
            val categories = categoryRepository.getAllCategories().first()
            val nameMap = locations.associate { it.id to it.name }
            val brandMap = brands.associate { it.id to it.name }
            val categoryMap = categories.associate { it.id to it.name }
            val currentItemIds = _uiState.value.items.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    allItems = items,
                    locationNames = nameMap,
                    brandNames = brandMap,
                    categoryNames = categoryMap,
                    pickerSelectedItemIds = currentItemIds,
                    pickerSearchQuery = ""
                )
            }
        }
    }

    fun togglePickerItemSelection(itemId: Long) {
        val current = _uiState.value.pickerSelectedItemIds
        _uiState.update {
            it.copy(
                pickerSelectedItemIds = if (itemId in current) current - itemId else current + itemId
            )
        }
    }

    fun updatePickerSearchQuery(query: String) {
        _uiState.update { it.copy(pickerSearchQuery = query) }
    }

    fun confirmPickerSelection(locationId: Long, onComplete: () -> Unit) {
        val originalItemIds = _uiState.value.items.map { it.id }.toSet()
        val selectedIds = _uiState.value.pickerSelectedItemIds
        val addedIds = selectedIds - originalItemIds
        val removedIds = originalItemIds - selectedIds

        if (addedIds.isEmpty() && removedIds.isEmpty()) {
            onComplete()
            return
        }

        viewModelScope.launch {
            addedIds.forEach { itemId ->
                val item = itemRepository.getItemById(itemId)
                item?.let { itemRepository.updateItem(it.copy(locationId = locationId)) }
            }
            removedIds.forEach { itemId ->
                val item = itemRepository.getItemById(itemId)
                item?.let { itemRepository.updateItem(it.copy(locationId = null)) }
            }
            onComplete()
        }
    }
}
