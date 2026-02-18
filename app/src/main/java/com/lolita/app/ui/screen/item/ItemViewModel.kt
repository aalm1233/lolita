package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.repository.StyleRepository
import com.lolita.app.data.repository.SeasonRepository
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val filterGroup: CategoryGroup? = null,
    val filterSeason: String? = null,
    val filterStyle: String? = null,
    val filterColor: String? = null,
    val filterBrandId: Long? = null,
    val searchQuery: String = "",
    val brandNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val categoryGroups: Map<Long, CategoryGroup> = emptyMap(),
    val seasonOptions: List<String> = emptyList(),
    val styleOptions: List<String> = emptyList(),
    val colorOptions: List<String> = emptyList(),
    val totalPrice: Double = 0.0,
    val showTotalPrice: Boolean = false,
    val columnsPerRow: Int = 1,
    val itemPrices: Map<Long, Double> = emptyMap(),
    val errorMessage: String? = null
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
    val seasons: List<String> = emptyList(),
    val style: String? = null,
    val size: String? = null,
    val sizeChartImageUrl: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val brands: List<com.lolita.app.data.local.entity.Brand> = emptyList(),
    val categories: List<com.lolita.app.data.local.entity.Category> = emptyList(),
    val coordinates: List<com.lolita.app.data.local.entity.Coordinate> = emptyList(),
    val styleOptions: List<String> = emptyList(),
    val seasonOptions: List<String> = emptyList(),
    val pricesWithPayments: List<DaoPriceWithPayments> = emptyList()
)

/**
 * ViewModel for Item List Screen
 */
class ItemListViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    private var totalPriceJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadItems()
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.showTotalPrice.collect { show ->
                _uiState.update { it.copy(showTotalPrice = show) }
            }
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            combine(
                itemRepository.getAllItems(),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories(),
                priceRepository.getItemPriceSums()
            ) { items, brands, categories, priceSums ->
                val brandMap = brands.associate { it.id to it.name }
                val categoryMap = categories.associate { it.id to it.name }
                val groupMap = categories.associate { it.id to it.group }
                val priceMap = priceSums.associate { it.itemId to it.totalPrice }
                val seasonOpts = items.flatMap { it.season?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotBlank() } ?: emptyList() }.distinct().sorted()
                val styleOpts = items.mapNotNull { it.style?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
                val colorOpts = items.mapNotNull { it.color?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
                ItemListData(items, brandMap, categoryMap, groupMap, priceMap, seasonOpts, styleOpts, colorOpts)
            }.collect { data ->
                val filtered = applyFilters(
                    data.items, _uiState.value.filterStatus, _uiState.value.searchQuery, _uiState.value.filterGroup,
                    data.groupMap, _uiState.value.filterSeason, _uiState.value.filterStyle, _uiState.value.filterColor, _uiState.value.filterBrandId
                )
                _uiState.update {
                    it.copy(
                        items = data.items,
                        filteredItems = filtered,
                        brandNames = data.brandMap,
                        categoryNames = data.categoryMap,
                        categoryGroups = data.groupMap,
                        itemPrices = data.priceMap,
                        seasonOptions = data.seasonOpts,
                        styleOptions = data.styleOpts,
                        colorOptions = data.colorOpts,
                        isLoading = false
                    )
                }
                updateTotalPrice(filtered)
            }
        }
    }

    private data class ItemListData(
        val items: List<Item>,
        val brandMap: Map<Long, String>,
        val categoryMap: Map<Long, String>,
        val groupMap: Map<Long, CategoryGroup>,
        val priceMap: Map<Long, Double>,
        val seasonOpts: List<String>,
        val styleOpts: List<String>,
        val colorOpts: List<String>
    )

    private fun updateTotalPrice(filteredItems: List<Item>) {
        totalPriceJob?.cancel()
        totalPriceJob = viewModelScope.launch {
            val itemIds = filteredItems.map { it.id }
            if (itemIds.isEmpty()) {
                _uiState.update { it.copy(totalPrice = 0.0) }
                return@launch
            }
            val total = priceRepository.getTotalPriceByItemIds(itemIds).first()
            _uiState.update { it.copy(totalPrice = total) }
        }
    }

    fun filterByStatus(status: ItemStatus?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, status, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId
        )
        _uiState.update { it.copy(filterStatus = status, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun filterByGroup(group: CategoryGroup?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, group, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId
        )
        _uiState.update { it.copy(filterGroup = group, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun filterBySeason(season: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            season, state.filterStyle, state.filterColor, state.filterBrandId
        )
        _uiState.update { it.copy(filterSeason = season, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun filterByStyle(style: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, style, state.filterColor, state.filterBrandId
        )
        _uiState.update { it.copy(filterStyle = style, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun filterByColor(color: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, color, state.filterBrandId
        )
        _uiState.update { it.copy(filterColor = color, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun filterByBrand(brandId: Long?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, brandId
        )
        _uiState.update { it.copy(filterBrandId = brandId, filteredItems = filtered) }
        updateTotalPrice(filtered)
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val state = _uiState.value
            val filtered = applyFilters(
                state.items, state.filterStatus, query, state.filterGroup, state.categoryGroups,
                state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId
            )
            _uiState.update { it.copy(filteredItems = filtered) }
            updateTotalPrice(filtered)
        }
    }

    private fun applyFilters(
        items: List<Item>,
        status: ItemStatus?,
        query: String,
        group: CategoryGroup? = null,
        categoryGroups: Map<Long, CategoryGroup> = emptyMap(),
        season: String? = null,
        style: String? = null,
        color: String? = null,
        brandId: Long? = null
    ): List<Item> {
        var result = items

        if (status != null) {
            result = result.filter { it.status == status }
        }

        if (group != null) {
            result = result.filter { categoryGroups[it.categoryId] == group }
        }

        if (season != null) {
            result = result.filter { item ->
                item.season?.split(",")?.any { it.trim() == season } == true
            }
        }

        if (style != null) {
            result = result.filter { it.style == style }
        }

        if (color != null) {
            result = result.filter { it.color == color }
        }

        if (brandId != null) {
            result = result.filter { it.brandId == brandId }
        }

        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }

        return result
    }

    fun setColumns(count: Int) {
        _uiState.update { it.copy(columnsPerRow = count) }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val styleRepository: StyleRepository = com.lolita.app.di.AppModule.styleRepository(),
    private val seasonRepository: SeasonRepository = com.lolita.app.di.AppModule.seasonRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemEditUiState())
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    private val pendingImageDeletions = mutableListOf<String>()

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load all reference data with .first() to avoid race conditions
            val brands = brandRepository.getAllBrands().first()
            val categories = categoryRepository.getAllCategories().first()
            val coordinates = coordinateRepository.getAllCoordinates().first()
            val styles = styleRepository.getAllStyles().first()
            val seasons = seasonRepository.getAllSeasons().first()

            _uiState.update {
                it.copy(
                    brands = brands,
                    categories = categories,
                    coordinates = coordinates,
                    styleOptions = styles.map { s -> s.name },
                    seasonOptions = seasons.map { s -> s.name }
                )
            }

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
                            seasons = item.season?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList(),
                            style = item.style,
                            size = item.size,
                            sizeChartImageUrl = item.sizeChartImageUrl,
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
        val oldUrl = _uiState.value.imageUrl
        if (oldUrl != null && imageUrl != oldUrl) {
            pendingImageDeletions.add(oldUrl)
        }
        _uiState.value = _uiState.value.copy(imageUrl = imageUrl)
    }

    fun updateColor(color: String?) {
        _uiState.value = _uiState.value.copy(color = color)
    }

    fun toggleSeason(season: String) {
        val current = _uiState.value.seasons
        val updated = if (season in current) current - season else current + season
        _uiState.value = _uiState.value.copy(seasons = updated)
    }

    fun updateStyle(style: String?) {
        _uiState.value = _uiState.value.copy(style = style)
    }

    fun updateSize(size: String?) {
        _uiState.value = _uiState.value.copy(size = size)
    }

    fun updateSizeChartImageUrl(url: String?) {
        val oldUrl = _uiState.value.sizeChartImageUrl
        if (oldUrl != null && url != oldUrl) {
            pendingImageDeletions.add(oldUrl)
        }
        _uiState.value = _uiState.value.copy(sizeChartImageUrl = url)
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
                val seasonStr = state.seasons.takeIf { it.isNotEmpty() }?.joinToString(",")
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
                        season = seasonStr,
                        style = state.style,
                        size = state.size,
                        sizeChartImageUrl = state.sizeChartImageUrl,
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
                        season = seasonStr,
                        style = state.style,
                        size = state.size,
                        sizeChartImageUrl = state.sizeChartImageUrl,
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
                // Delete pending images after successful save
                val deletions = pendingImageDeletions.toList()
                pendingImageDeletions.clear()
                deletions.forEach { path ->
                    try { ImageFileHelper.deleteImage(path) } catch (_: Exception) { }
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
