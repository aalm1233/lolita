package com.lolita.app.ui.screen.coordinate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.local.dao.ItemPriceSum
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.ui.screen.common.SortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoordinateListUiState(
    val allCoordinates: List<Coordinate> = emptyList(),
    val coordinates: List<Coordinate> = emptyList(),
    val searchQuery: String = "",
    val itemCounts: Map<Long, Int> = emptyMap(),
    val itemImagesByCoordinate: Map<Long, List<String?>> = emptyMap(),
    val priceByCoordinate: Map<Long, Double> = emptyMap(),
    val showPrice: Boolean = false,
    val sortOption: SortOption = SortOption.DEFAULT,
    val columnsPerRow: Int = 1,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
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
    val imageUrl: String? = null,
    val allItems: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false
)

class CoordinateListViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateListUiState())
    val uiState: StateFlow<CoordinateListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCoordinates()
    }

    private fun loadCoordinates() {
        viewModelScope.launch {
            combine(
                combine(
                    coordinateRepository.getAllCoordinates(),
                    coordinateRepository.getItemCountsByCoordinate()
                ) { a, b -> Pair(a, b) },
                combine(
                    itemRepository.getAllItems(),
                    priceRepository.getItemPriceSums()
                ) { a, b -> Pair(a, b) },
                appPreferences.showTotalPrice
            ) { (coordinates, itemCounts), (allItems, priceSums), showPrice ->
                val countMap = itemCounts.associate { it.coordinate_id to it.itemCount }
                val imageMap = allItems
                    .filter { it.coordinateId != null }
                    .groupBy { it.coordinateId!! }
                    .mapValues { (_, items) -> items.take(4).map { it.imageUrl } }

                val priceMap = priceSums.associate { it.itemId to it.totalPrice }
                val coordPriceMap = allItems
                    .filter { it.coordinateId != null }
                    .groupBy { it.coordinateId!! }
                    .mapValues { (_, items) ->
                        items.sumOf { priceMap[it.id] ?: 0.0 }
                    }

                CoordinateListUiState(
                    allCoordinates = coordinates,
                    coordinates = coordinates,
                    itemCounts = countMap,
                    itemImagesByCoordinate = imageMap,
                    priceByCoordinate = coordPriceMap,
                    showPrice = showPrice,
                    columnsPerRow = _uiState.value.columnsPerRow,
                    isLoading = false
                )
            }.collect { state ->
                val query = _uiState.value.searchQuery
                val filtered = applySearch(state.allCoordinates, query)
                val sorted = applySorting(filtered, _uiState.value.sortOption, state.priceByCoordinate)
                _uiState.value = state.copy(
                    coordinates = sorted,
                    searchQuery = query,
                    sortOption = _uiState.value.sortOption
                )
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            val filtered = applySearch(state.allCoordinates, query)
            val sorted = applySorting(filtered, state.sortOption, state.priceByCoordinate)
            _uiState.update { it.copy(coordinates = sorted) }
        }
    }

    private fun applySearch(coordinates: List<Coordinate>, query: String): List<Coordinate> {
        if (query.isBlank()) return coordinates
        return coordinates.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    fun deleteCoordinate(coordinate: Coordinate) {
        viewModelScope.launch {
            try {
                coordinateRepository.deleteCoordinate(coordinate)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "删除失败"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setColumns(count: Int) {
        _uiState.value = _uiState.value.copy(columnsPerRow = count)
    }

    fun setSortOption(option: SortOption) {
        val state = _uiState.value
        val filtered = applySearch(state.allCoordinates, state.searchQuery)
        val sorted = applySorting(filtered, option, state.priceByCoordinate)
        _uiState.value = state.copy(sortOption = option, coordinates = sorted)
    }

    private fun applySorting(coordinates: List<Coordinate>, sort: SortOption, prices: Map<Long, Double>): List<Coordinate> {
        return when (sort) {
            SortOption.DEFAULT -> coordinates
            SortOption.DATE_DESC -> coordinates.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> coordinates.sortedBy { it.updatedAt }
            SortOption.PRICE_DESC -> coordinates.sortedByDescending { prices[it.id] ?: 0.0 }
            SortOption.PRICE_ASC -> coordinates.sortedBy { prices[it.id] ?: 0.0 }
        }
    }
}

class CoordinateDetailViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateDetailUiState())
    val uiState: StateFlow<CoordinateDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadCoordinate(coordinateId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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

    fun deleteCoordinate(onSuccess: () -> Unit) {
        val coordinate = _uiState.value.coordinate ?: return
        viewModelScope.launch {
            try {
                val coordinateRepository = com.lolita.app.di.AppModule.coordinateRepository()
                coordinateRepository.deleteCoordinate(coordinate)
                onSuccess()
            } catch (_: Exception) {
                // 删除失败静默处理
            }
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
    var hasUnsavedChanges: Boolean = false
        private set

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
                    description = it.description,
                    imageUrl = it.imageUrl
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
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateImageUrl(url: String?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(imageUrl = url)
    }

    fun toggleItemSelection(itemId: Long) {
        hasUnsavedChanges = true
        val current = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    suspend fun save(): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val now = System.currentTimeMillis()
            val coordinate = Coordinate(
                name = _uiState.value.name,
                description = _uiState.value.description,
                imageUrl = _uiState.value.imageUrl,
                createdAt = now,
                updatedAt = now
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
            if (originalCreatedAt == 0L) {
                throw IllegalStateException("数据未加载完成，请稍后再试")
            }
            val coordinate = Coordinate(
                id = coordinateId,
                name = _uiState.value.name,
                description = _uiState.value.description,
                imageUrl = _uiState.value.imageUrl,
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
