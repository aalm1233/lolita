package com.lolita.app.ui.screen.coordinate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.local.dao.PriceWithPayments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CoordinateListUiState(
    val coordinates: List<Coordinate> = emptyList(),
    val itemCounts: Map<Long, Int> = emptyMap(),
    val itemImagesByCoordinate: Map<Long, List<String?>> = emptyMap(),
    val isLoading: Boolean = true
)

data class CoordinateDetailUiState(
    val coordinate: Coordinate? = null,
    val items: List<Item> = emptyList(),
    val totalPrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val isLoading: Boolean = true
)

data class CoordinateEditUiState(
    val name: String = "",
    val description: String = "",
    val allItems: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false
)

class CoordinateListViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateListUiState())
    val uiState: StateFlow<CoordinateListUiState> = _uiState.asStateFlow()

    init {
        loadCoordinates()
    }

    private fun loadCoordinates() {
        viewModelScope.launch {
            combine(
                coordinateRepository.getAllCoordinates(),
                coordinateRepository.getItemCountsByCoordinate(),
                itemRepository.getAllItems()
            ) { coordinates, itemCounts, allItems ->
                val countMap = itemCounts.associate { it.coordinate_id to it.itemCount }
                val imageMap = allItems
                    .filter { it.coordinateId != null }
                    .groupBy { it.coordinateId!! }
                    .mapValues { (_, items) -> items.take(4).map { it.imageUrl } }
                Triple(coordinates, countMap, imageMap)
            }.collect { (coordinates, countMap, imageMap) ->
                _uiState.value = _uiState.value.copy(
                    coordinates = coordinates,
                    itemCounts = countMap,
                    itemImagesByCoordinate = imageMap,
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
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateDetailUiState())
    val uiState: StateFlow<CoordinateDetailUiState> = _uiState.asStateFlow()

    fun loadCoordinate(coordinateId: Long) {
        viewModelScope.launch {
            combine(
                coordinateRepository.getCoordinateWithItems(coordinateId),
                priceRepository.getPricesWithPaymentsByCoordinate(coordinateId)
            ) { result, pricesWithPayments ->
                val totalPrice = pricesWithPayments.sumOf { it.price.totalPrice }
                val paidAmount = pricesWithPayments.flatMap { it.payments }
                    .filter { it.isPaid }
                    .sumOf { it.amount }
                val unpaidAmount = pricesWithPayments.flatMap { it.payments }
                    .filter { !it.isPaid }
                    .sumOf { it.amount }

                CoordinateDetailUiState(
                    coordinate = result?.coordinate,
                    items = result?.items ?: emptyList(),
                    totalPrice = totalPrice,
                    paidAmount = paidAmount,
                    unpaidAmount = unpaidAmount,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
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
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateEditUiState())
    val uiState: StateFlow<CoordinateEditUiState> = _uiState.asStateFlow()

    private var originalCreatedAt: Long = 0L
    private var originalSelectedItemIds: Set<Long> = emptySet()

    init {
        loadAllItems()
    }

    private fun loadAllItems() {
        viewModelScope.launch {
            combine(
                itemRepository.getAllItems(),
                coordinateRepository.getAllCoordinates()
            ) { items, coordinates ->
                val nameMap = coordinates.associate { it.id to it.name }
                Pair(items, nameMap)
            }.collect { (items, nameMap) ->
                _uiState.value = _uiState.value.copy(
                    allItems = items,
                    coordinateNames = nameMap
                )
            }
        }
    }

    fun loadCoordinate(coordinateId: Long?) {
        if (coordinateId == null) return

        viewModelScope.launch {
            val coordinate = coordinateRepository.getCoordinateById(coordinateId)
            coordinate?.let {
                originalCreatedAt = it.createdAt
                _uiState.value = _uiState.value.copy(
                    name = it.name,
                    description = it.description
                )
            }

            // Load items already in this coordinate
            val result = coordinateRepository.getCoordinateWithItems(coordinateId).first()
            val itemIds = result?.items?.map { it.id }?.toSet() ?: emptySet()
            originalSelectedItemIds = itemIds
            _uiState.value = _uiState.value.copy(selectedItemIds = itemIds)
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun toggleItemSelection(itemId: Long) {
        val current = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    suspend fun save(): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val coordinate = Coordinate(
                name = _uiState.value.name,
                description = _uiState.value.description
            )
            val id = coordinateRepository.insertCoordinateWithItems(
                coordinate, _uiState.value.selectedItemIds
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
            val coordinate = Coordinate(
                id = coordinateId,
                name = _uiState.value.name,
                description = _uiState.value.description,
                createdAt = originalCreatedAt,
                updatedAt = System.currentTimeMillis()
            )
            val removedIds = originalSelectedItemIds - _uiState.value.selectedItemIds
            val addedIds = _uiState.value.selectedItemIds - originalSelectedItemIds
            coordinateRepository.updateCoordinateWithItems(coordinate, addedIds, removedIds)
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }
}
