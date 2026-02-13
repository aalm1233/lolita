package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for Item List Screen
 */
data class ItemListUiState(
    val items: List<Item> = emptyList(),
    val filteredItems: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val filterStatus: ItemStatus? = null,
    val searchQuery: String = "",
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap()
)

/**
 * UI State for Item Edit Screen
 */
data class ItemEditUiState(
    val item: Item? = null,
    val name: String = "",
    val description: String = "",
    val brandId: Long = 0,
    val categoryId: Long = 0,
    val coordinateId: Long? = null,
    val status: ItemStatus = ItemStatus.OWNED,
    val priority: ItemPriority = ItemPriority.MEDIUM,
    val imageUrl: String? = null,
    val color: String? = null,
    val season: String? = null,
    val style: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val brands: List<com.lolita.app.data.local.entity.Brand> = emptyList(),
    val categories: List<com.lolita.app.data.local.entity.Category> = emptyList(),
    val coordinates: List<com.lolita.app.data.local.entity.Coordinate> = emptyList(),
    val pricesWithPayments: List<DaoPriceWithPayments> = emptyList()
)

/**
 * ViewModel for Item List Screen
 */
class ItemListViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            combine(
                itemRepository.getAllItems(),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories()
            ) { items, brands, categories ->
                val brandMap = brands.associate { it.id to it.name }
                val categoryMap = categories.associate { it.id to it.name }
                Triple(items, brandMap, categoryMap)
            }.collect { (items, brandMap, categoryMap) ->
                _uiState.value = _uiState.value.copy(
                    items = items,
                    filteredItems = applyFilters(items, _uiState.value.filterStatus, _uiState.value.searchQuery),
                    brandNames = brandMap,
                    categoryNames = categoryMap,
                    isLoading = false
                )
            }
        }
    }

    fun filterByStatus(status: ItemStatus?) {
        _uiState.value = _uiState.value.copy(
            filterStatus = status,
            filteredItems = applyFilters(_uiState.value.items, status, _uiState.value.searchQuery)
        )
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredItems = applyFilters(_uiState.value.items, _uiState.value.filterStatus, query)
        )
    }

    private fun applyFilters(
        items: List<Item>,
        status: ItemStatus?,
        query: String
    ): List<Item> {
        var result = items

        if (status != null) {
            result = result.filter { it.status == status }
        }

        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }

        return result
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            itemRepository.deleteItem(item)
        }
    }
}

/**
 * ViewModel for Item Edit/Detail Screen
 */
class ItemEditViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val brandRepository: com.lolita.app.data.repository.BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: com.lolita.app.data.repository.CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val coordinateRepository: com.lolita.app.data.repository.CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemEditUiState())
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load all reference data with .first() to avoid race conditions
            val brands = brandRepository.getAllBrands().first()
            val categories = categoryRepository.getAllCategories().first()
            val coordinates = coordinateRepository.getAllCoordinates().first()

            _uiState.update { it.copy(brands = brands, categories = categories, coordinates = coordinates) }

            if (itemId > 0) {
                val item = itemRepository.getItemById(itemId)
                if (item != null) {
                    val prices = priceRepository.getPricesWithPaymentsByItem(itemId).first()
                    _uiState.update {
                        it.copy(
                            item = item,
                            name = item.name,
                            description = item.description,
                            brandId = item.brandId,
                            categoryId = item.categoryId,
                            coordinateId = item.coordinateId,
                            status = item.status,
                            priority = item.priority,
                            imageUrl = item.imageUrl,
                            color = item.color,
                            season = item.season,
                            style = item.style,
                            pricesWithPayments = prices,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateBrand(brandId: Long) {
        _uiState.value = _uiState.value.copy(brandId = brandId)
    }

    fun updateCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    fun updateCoordinate(coordinateId: Long?) {
        _uiState.value = _uiState.value.copy(coordinateId = coordinateId)
    }

    fun updateStatus(status: ItemStatus) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun updatePriority(priority: ItemPriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun updateImageUrl(imageUrl: String?) {
        _uiState.value = _uiState.value.copy(imageUrl = imageUrl)
    }

    fun updateColor(color: String?) {
        _uiState.value = _uiState.value.copy(color = color)
    }

    fun updateSeason(season: String?) {
        _uiState.value = _uiState.value.copy(season = season)
    }

    fun updateStyle(style: String?) {
        _uiState.value = _uiState.value.copy(style = style)
    }

    fun saveItem(onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value

        if (state.name.isBlank()) {
            onError("请输入服饰名称")
            return
        }

        if (state.brandId == 0L) {
            onError("请选择品牌")
            return
        }

        if (state.categoryId == 0L) {
            onError("请选择类型")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            try {
                val now = System.currentTimeMillis()
                val item = if (state.item != null) {
                    // Update existing item
                    state.item.copy(
                        name = state.name,
                        description = state.description,
                        brandId = state.brandId,
                        categoryId = state.categoryId,
                        coordinateId = state.coordinateId,
                        status = state.status,
                        priority = state.priority,
                        imageUrl = state.imageUrl,
                        color = state.color,
                        season = state.season,
                        style = state.style,
                        updatedAt = now
                    )
                } else {
                    // Create new item
                    Item(
                        id = 0,
                        coordinateId = state.coordinateId,
                        brandId = state.brandId,
                        categoryId = state.categoryId,
                        name = state.name,
                        description = state.description,
                        imageUrl = state.imageUrl,
                        status = state.status,
                        priority = state.priority,
                        color = state.color,
                        season = state.season,
                        style = state.style,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                val itemId = if (state.item != null) {
                    // Update existing item
                    itemRepository.updateItem(item)
                    state.item.id
                } else {
                    // Create new item
                    itemRepository.insertItem(item)
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess(itemId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                onError(e.message ?: "保存失败")
            }
        }
    }

    fun deleteItem(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val item = _uiState.value.item
        if (item == null) {
            onError("服饰不存在")
            return
        }

        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "删除失败")
            }
        }
    }
}
