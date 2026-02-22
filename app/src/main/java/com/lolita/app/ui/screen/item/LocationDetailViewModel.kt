package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationDetailUiState(
    val location: Location? = null,
    val items: List<Item> = emptyList(),
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val isUnassigned: Boolean = false,
    val isLoading: Boolean = true
)

class LocationDetailViewModel(
    private val locationRepository: LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository()
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
}
