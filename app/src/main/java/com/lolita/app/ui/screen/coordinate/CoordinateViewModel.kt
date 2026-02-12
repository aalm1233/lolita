package com.lolita.app.ui.screen.coordinate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoordinateListUiState(
    val coordinates: List<Coordinate> = emptyList(),
    val isLoading: Boolean = true
)

data class CoordinateDetailUiState(
    val coordinate: Coordinate? = null,
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true
)

data class CoordinateEditUiState(
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false
)

class CoordinateListViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateListUiState())
    val uiState: StateFlow<CoordinateListUiState> = _uiState.asStateFlow()

    init {
        loadCoordinates()
    }

    private fun loadCoordinates() {
        viewModelScope.launch {
            coordinateRepository.getAllCoordinates().collect { coordinates ->
                _uiState.value = _uiState.value.copy(
                    coordinates = coordinates,
                    isLoading = false
                )
            }
        }
    }

    fun deleteCoordinate(coordinate: Coordinate) {
        viewModelScope.launch {
            coordinateRepository.deleteCoordinate(coordinate)
        }
    }
}

class CoordinateDetailViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateDetailUiState())
    val uiState: StateFlow<CoordinateDetailUiState> = _uiState.asStateFlow()

    fun loadCoordinate(coordinateId: Long) {
        viewModelScope.launch {
            coordinateRepository.getCoordinateWithItems(coordinateId).collect { result ->
                _uiState.value = _uiState.value.copy(
                    coordinate = result?.coordinate,
                    items = result?.items ?: emptyList(),
                    isLoading = false
                )
            }
        }
    }

    fun removeItemFromCoordinate(item: Item) {
        viewModelScope.launch {
            val itemRepository = com.lolita.app.di.AppModule.itemRepository()
            itemRepository.updateItem(item.copy(coordinateId = null))
        }
    }
}

class CoordinateEditViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateEditUiState())
    val uiState: StateFlow<CoordinateEditUiState> = _uiState.asStateFlow()

    private var originalCreatedAt: Long = 0L

    fun loadCoordinate(coordinateId: Long?) {
        if (coordinateId == null) return

        viewModelScope.launch {
            val coordinate = coordinateRepository.getCoordinateById(coordinateId)
            coordinate?.let {
                originalCreatedAt = it.createdAt
                _uiState.value = CoordinateEditUiState(
                    name = it.name,
                    description = it.description
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    suspend fun save(): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val id = coordinateRepository.insertCoordinate(
                Coordinate(
                    name = _uiState.value.name,
                    description = _uiState.value.description
                )
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(id)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }

    suspend fun update(coordinateId: Long): Result<Unit> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            coordinateRepository.updateCoordinate(
                Coordinate(
                    id = coordinateId,
                    name = _uiState.value.name,
                    description = _uiState.value.description,
                    createdAt = originalCreatedAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }
}
