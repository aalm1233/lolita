package com.lolita.app.ui.screen.coordinate

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.data.local.dao.CoordinateWithItems
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.local.dao.ItemPriceSum
import com.lolita.app.data.local.dao.PriceWithPayments
import com.lolita.app.ui.screen.common.SortOption
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
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
    val incompleteOrderCount: Int = 0,
    val isLoading: Boolean = true,
    // Item picker state
    val allItems: List<Item> = emptyList(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val pickerSelectedItemIds: Set<Long> = emptySet(),
    val pickerSearchQuery: String = ""
)

data class CoordinateEditUiState(
    val name: String = "",
    val description: String = "",
    val imageUrls: List<String> = emptyList(),
    val allItems: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val coordinateNames: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null
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
                    .mapValues { (_, items) -> items.take(4).map { it.imageUrls.firstOrNull() } }

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
                    errorMessage = uiErrorText(e, "删除失败")
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
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
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
                val unpaidAmount = pricesWithPayments.filter { order ->
                    order.binding?.purchaseRecordStatus == com.lolita.app.data.local.entity.PurchaseRecordStatus.TRACKED
                }.sumOf { order ->
                    com.lolita.app.domain.usecase.OrderAccounting.remaining(order.price.totalPrice,
                        com.lolita.app.domain.usecase.OrderAccounting.sum(order.payments.filter { it.isPaid }.map { it.amount }))
                }

                Triple(result, totalPrice, Triple(paidAmount, unpaidAmount, pricesWithPayments.count {
                    it.binding?.purchaseRecordStatus != com.lolita.app.data.local.entity.PurchaseRecordStatus.TRACKED
                }))
            }.collect { (result, totalPrice, paidAmounts) ->
                _uiState.update {
                    it.copy(
                        coordinate = result?.coordinate,
                        items = (result?.items ?: emptyList()).sortedBy { item -> item.coordinateOrder },
                        totalPrice = totalPrice,
                        paidAmount = paidAmounts.first,
                        unpaidAmount = paidAmounts.second,
                        incompleteOrderCount = paidAmounts.third,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun removeItemFromCoordinate(item: Item) {
        viewModelScope.launch {
            val itemRepository = com.lolita.app.di.AppModule.itemRepository()
            itemRepository.updateItem(item.copy(coordinateId = null))
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        val currentItems = _uiState.value.items.toMutableList()
        if (fromIndex !in currentItems.indices || toIndex !in currentItems.indices) return
        val item = currentItems.removeAt(fromIndex)
        currentItems.add(toIndex, item)
        _uiState.update { it.copy(items = currentItems) }
        // Persist new order
        viewModelScope.launch {
            currentItems.forEachIndexed { index, orderedItem ->
                itemRepository.updateCoordinateOrder(orderedItem.id, index)
            }
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

    fun loadAllItemsForPicker() {
        viewModelScope.launch {
            val items = itemRepository.getAllItems().first()
            val coordinates = coordinateRepository.getAllCoordinates().first()
            val nameMap = coordinates.associate { it.id to it.name }
            val currentItemIds = _uiState.value.items.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    allItems = items,
                    coordinateNames = nameMap,
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

    fun confirmPickerSelection(onComplete: () -> Unit) {
        val coordinate = _uiState.value.coordinate ?: return
        val originalItemIds = _uiState.value.items.map { it.id }.toSet()
        val selectedIds = _uiState.value.pickerSelectedItemIds
        val addedIds = selectedIds - originalItemIds
        val removedIds = originalItemIds - selectedIds

        if (addedIds.isEmpty() && removedIds.isEmpty()) {
            onComplete()
            return
        }

        viewModelScope.launch {
            coordinateRepository.updateCoordinateWithItems(coordinate, addedIds, removedIds)
            // Set coordinateOrder for newly added items
            val maxOrder = _uiState.value.items.maxOfOrNull { it.coordinateOrder } ?: -1
            addedIds.forEachIndexed { index, itemId ->
                itemRepository.updateCoordinateOrder(itemId, maxOrder + 1 + index)
            }
            onComplete()
        }
    }
}

class CoordinateEditViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordinateEditUiState())
    val uiState: StateFlow<CoordinateEditUiState> = _uiState.asStateFlow()

    private var originalCoordinate: Coordinate? = null
    private var requestedCoordinateId: Long? = null
    private var hasRequestedCoordinate = false
    private var editorLoaded = false
    private var loadJob: Job? = null
    private val newImages = mutableSetOf<String>()
    private var originalSelectedItemIds: Set<Long> = emptySet()
    var hasUnsavedChanges: Boolean = false
        private set

    init {
        loadAllItems()
    }

    private fun loadAllItems() {
        viewModelScope.launch {
            val items = itemRepository.getAllItems().first()
            val coordinates = coordinateRepository.getAllCoordinates().first()
            val nameMap = coordinates.associate { it.id to it.name }
            _uiState.value = _uiState.value.copy(
                allItems = items,
                coordinateNames = nameMap
            )
        }
    }

    fun loadCoordinate(coordinateId: Long?) {
        if (_uiState.value.isSaving) return
        if (hasRequestedCoordinate && requestedCoordinateId == coordinateId &&
            (editorLoaded || loadJob?.isActive == true)) return
        loadJob?.cancel()
        if (hasRequestedCoordinate && requestedCoordinateId != coordinateId) cleanupNewImages()
        requestedCoordinateId = coordinateId
        hasRequestedCoordinate = true
        editorLoaded = false
        originalCoordinate = null
        originalSelectedItemIds = emptySet()
        hasUnsavedChanges = false
        _uiState.update { CoordinateEditUiState(allItems = it.allItems,
            coordinateNames = it.coordinateNames, isLoading = coordinateId != null) }
        if (coordinateId == null) {
            editorLoaded = true
            return
        }
        loadJob = viewModelScope.launch {
            try {
                val result = coordinateRepository.getCoordinateWithItems(coordinateId).first()
                    ?: error("套装不存在，请重试")
                val coordinate = result.coordinate
                val itemIds = result.items.map { it.id }.toSet()
                originalCoordinate = coordinate
                originalSelectedItemIds = itemIds
                editorLoaded = true
                _uiState.update { it.copy(name = coordinate.name, description = coordinate.description,
                    imageUrls = coordinate.imageUrls, selectedItemIds = itemIds, isLoading = false) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(isLoading = false, loadError = uiErrorText(failure, "读取套装失败")) }
            }
        }
    }

    fun cleanupNewImages() {
        ImageFileHelper.cleanupUnreferencedImages(newImages.toList())
        newImages.clear()
    }

    override fun onCleared() {
        cleanupNewImages()
        super.onCleared()
    }

    fun updateName(name: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun addImage(url: String) {
        val current = _uiState.value
        if (current.isSaving || current.isLoading || current.imageUrls.size >= 5) {
            if (url !in current.imageUrls) ImageFileHelper.cleanupUnreferencedImages(listOf(url))
            return
        }
        newImages.add(url)
        hasUnsavedChanges = true
        _uiState.value = current.copy(imageUrls = current.imageUrls + url)
    }

    fun removeImage(index: Int) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        val current = _uiState.value.imageUrls.toMutableList()
        if (index !in current.indices) return
        val removed = current.removeAt(index)
        _uiState.value = _uiState.value.copy(imageUrls = current)
        if (removed !in current && newImages.remove(removed)) {
            ImageFileHelper.cleanupUnreferencedImages(listOf(removed))
        }
    }

    fun reorderImages(fromIndex: Int, toIndex: Int) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        val current = _uiState.value.imageUrls.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _uiState.value = _uiState.value.copy(imageUrls = current)
    }

    fun toggleItemSelection(itemId: Long) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        val current = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    fun isValid(): Boolean {
        return _uiState.value.name.isNotBlank()
    }

    // The VM owns persistence; a recreated screen may cancel its await without cancelling the save.
    private var saveTask: Deferred<Result<Long>>? = null

    private fun startSave(coordinateId: Long?): Deferred<Result<Long>> =
        saveTask?.takeIf { it.isActive } ?: viewModelScope.async {
            if (coordinateId == null) saveDraft() else updateDraft(coordinateId).map { coordinateId }
        }.also { saveTask = it }

    suspend fun save(): Result<Long> = startSave(null).await()

    suspend fun update(coordinateId: Long): Result<Unit> = startSave(coordinateId).await().map { Unit }

    private suspend fun saveDraft(): Result<Long> {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.loadError != null || requestedCoordinateId != null) {
            return Result.failure(IllegalStateException("数据未加载完成，请稍后再试"))
        }
        originalCoordinate?.let { original -> return updateDraft(original.id).map { original.id } }
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val now = System.currentTimeMillis()
            val coordinate = Coordinate(
                name = _uiState.value.name,
                description = _uiState.value.description,
                imageUrls = _uiState.value.imageUrls,
                createdAt = now,
                updatedAt = now
            )
            val id = coordinateRepository.insertCoordinateWithItems(
                coordinate, _uiState.value.selectedItemIds
            )
            originalCoordinate = coordinate.copy(id = id)
            originalSelectedItemIds = _uiState.value.selectedItemIds
            cleanupNewImages()
            hasUnsavedChanges = false
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }

    private suspend fun updateDraft(coordinateId: Long): Result<Unit> {
        if (_uiState.value.isSaving || _uiState.value.isLoading || !editorLoaded || originalCoordinate?.id != coordinateId) {
            return Result.failure(IllegalStateException("数据未加载完成，请稍后再试"))
        }
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val coordinate = requireNotNull(originalCoordinate).copy(
                name = _uiState.value.name,
                description = _uiState.value.description,
                imageUrls = _uiState.value.imageUrls,
                updatedAt = System.currentTimeMillis()
            )
            val removedIds = originalSelectedItemIds - _uiState.value.selectedItemIds
            val addedIds = _uiState.value.selectedItemIds - originalSelectedItemIds
            coordinateRepository.updateCoordinateWithItems(coordinate, addedIds, removedIds)
            cleanupNewImages()
            originalCoordinate = coordinate
            originalSelectedItemIds = _uiState.value.selectedItemIds
            hasUnsavedChanges = false
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }
}
