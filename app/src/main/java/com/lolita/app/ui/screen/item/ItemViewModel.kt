package com.lolita.app.ui.screen.item

import androidx.compose.runtime.Immutable
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
import com.lolita.app.data.repository.SourceRepository
import com.lolita.app.ui.screen.common.SortOption
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.file.ImageFileHelper
import com.google.gson.Gson
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

@Immutable
data class ItemCardData(
    val item: Item,
    val brandName: String?,
    val brandLogoUrl: String?,
    val categoryName: String?,
    val itemPrice: Double?,
    val showPrice: Boolean
)
/**
 * UI State for Item List Screen
 */
data class ItemListUiState(
    val items: List<Item> = emptyList(),
    val filteredItems: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val filterStatus: ItemStatus? = null,
    val filterStatuses: Set<ItemStatus>? = null,
    val filterPendingBalanceOnly: Boolean = false,
    val filterGroup: CategoryGroup? = null,
    val filterSeason: String? = null,
    val filterStyle: String? = null,
    val filterColor: String? = null,
    val filterBrandId: Long? = null,
    val searchQuery: String = "",
    val brandNames: Map<Long, String> = emptyMap(),
    val brandLogoUrls: Map<Long, String?> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val categoryGroups: Map<Long, CategoryGroup> = emptyMap(),
    val seasonOptions: List<String> = emptyList(),
    val styleOptions: List<String> = emptyList(),
    val colorOptions: List<String> = emptyList(),
    val totalPrice: Double = 0.0,
    val showTotalPrice: Boolean = false,
    val columnsPerRow: Int = 1,
    val sortOption: SortOption = SortOption.DEFAULT,
    val itemPrices: Map<Long, Double> = emptyMap(),
    val errorMessage: String? = null,
    val todayOutfitItemImages: List<String?> = emptyList(),
    val hasTodayOutfit: Boolean = false,
    val todayOutfitLogId: Long? = null,
    val itemCardDataList: List<ItemCardData> = emptyList()
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
    val colors: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val style: String? = null,
    val size: String? = null,
    val sizeChartImageUrl: String? = null,
    val source: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val brands: List<com.lolita.app.data.local.entity.Brand> = emptyList(),
    val categories: List<com.lolita.app.data.local.entity.Category> = emptyList(),
    val coordinates: List<com.lolita.app.data.local.entity.Coordinate> = emptyList(),
    val styleOptions: List<String> = emptyList(),
    val seasonOptions: List<String> = emptyList(),
    val sourceOptions: List<String> = emptyList(),
    val pricesWithPayments: List<DaoPriceWithPayments> = emptyList(),
    val locationId: Long? = null,
    val locations: List<com.lolita.app.data.local.entity.Location> = emptyList()
)

/**
 * ViewModel for Item List Screen
 */
class ItemListViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences(),
    private val locationRepository: com.lolita.app.data.repository.LocationRepository = com.lolita.app.di.AppModule.locationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    private var totalPriceJob: Job? = null
    private var searchJob: Job? = null

    private val _locations = MutableStateFlow<List<com.lolita.app.data.local.entity.Location>>(emptyList())
    val locations: StateFlow<List<com.lolita.app.data.local.entity.Location>> = _locations.asStateFlow()

    private val _locationItemCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val locationItemCounts: StateFlow<Map<Long, Int>> = _locationItemCounts.asStateFlow()

    private val _unassignedItemCount = MutableStateFlow(0)
    val unassignedItemCount: StateFlow<Int> = _unassignedItemCount.asStateFlow()

    init {
        loadItems()
        loadPreferences()
        loadTodayOutfit()
        loadLocations()
    }

    private fun loadTodayOutfit() {
        viewModelScope.launch {
            val todayLog = com.lolita.app.di.AppModule.outfitLogRepository().getTodayOutfitLog()
            _uiState.update {
                it.copy(
                    hasTodayOutfit = todayLog != null,
                    todayOutfitLogId = todayLog?.outfitLog?.id,
                    todayOutfitItemImages = todayLog?.items?.take(3)?.map { item -> item.imageUrl } ?: emptyList()
                )
            }
        }
    }

    private fun loadLocations() {
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { _locations.value = it }
        }
        viewModelScope.launch {
            locationRepository.getItemCountsByLocation().collect { counts ->
                _locationItemCounts.value = counts.associate { it.locationId to it.count }
            }
        }
        viewModelScope.launch {
            locationRepository.countItemsWithNoLocation().collect { _unassignedItemCount.value = it }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.showTotalPrice.collect { show ->
                _uiState.update { it.copy(showTotalPrice = show, itemCardDataList = buildItemCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, show)) }
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
                val brandLogoMap = brands.associate { it.id to it.logoUrl }
                val categoryMap = categories.associate { it.id to it.name }
                val groupMap = categories.associate { it.id to it.group }
                val priceMap = priceSums.associate { it.itemId to it.totalPrice }
                val seasonOpts = items.flatMap { it.season?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotBlank() } ?: emptyList() }.distinct().sorted()
                val styleOpts = items.mapNotNull { it.style?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
                val colorOpts = items.flatMap { item ->
                    item.colors?.let { json ->
                        try { Gson().fromJson(json, Array<String>::class.java).toList() }
                        catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                }.filter { it.isNotBlank() }.distinct().sorted()
                ItemListData(items, brandMap, brandLogoMap, categoryMap, groupMap, priceMap, seasonOpts, styleOpts, colorOpts)
            }.collect { data ->
                val filtered = applyFilters(
                    data.items, _uiState.value.filterStatus, _uiState.value.searchQuery, _uiState.value.filterGroup,
                    data.groupMap, _uiState.value.filterSeason, _uiState.value.filterStyle, _uiState.value.filterColor, _uiState.value.filterBrandId,
                    _uiState.value.filterStatuses, _uiState.value.filterPendingBalanceOnly
                )
                val sorted = applySorting(filtered, _uiState.value.sortOption, data.priceMap)
                _uiState.update {
                    it.copy(
                        items = data.items,
                        filteredItems = sorted,
                        brandNames = data.brandMap,
                        brandLogoUrls = data.brandLogoMap,
                        categoryNames = data.categoryMap,
                        categoryGroups = data.groupMap,
                        itemPrices = data.priceMap,
                        seasonOptions = data.seasonOpts,
                        styleOptions = data.styleOpts,
                        colorOptions = data.colorOpts,
                        isLoading = false,
                        itemCardDataList = buildItemCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.priceMap, it.showTotalPrice)
                    )
                }
                updateTotalPrice(sorted)
            }
        }
    }

    private data class ItemListData(
        val items: List<Item>,
        val brandMap: Map<Long, String>,
        val brandLogoMap: Map<Long, String?>,
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
    private fun buildItemCardDataList(
        items: List<Item>,
        brandNames: Map<Long, String>,
        brandLogoUrls: Map<Long, String?>,
        categoryNames: Map<Long, String>,
        itemPrices: Map<Long, Double>,
        showPrice: Boolean
    ): List<ItemCardData> {
        return items.map { item ->
            ItemCardData(
                item = item,
                brandName = brandNames[item.brandId],
                brandLogoUrl = brandLogoUrls[item.brandId],
                categoryName = categoryNames[item.categoryId],
                itemPrice = itemPrices[item.id],
                showPrice = showPrice
            )
        }
    }

    fun filterByStatus(status: ItemStatus?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, status, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterStatus = status, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterByStatuses(statuses: Set<ItemStatus>?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            statuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterStatuses = statuses, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun togglePendingBalanceOnly() {
        val newValue = !_uiState.value.filterPendingBalanceOnly
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, newValue
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterPendingBalanceOnly = newValue, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterByGroup(group: CategoryGroup?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, group, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterGroup = group, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterBySeason(season: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            season, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterSeason = season, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterByStyle(style: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, style, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterStyle = style, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterByColor(color: String?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, color, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterColor = color, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun filterByBrand(brandId: Long?) {
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, brandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update { it.copy(filterBrandId = brandId, filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val state = _uiState.value
            val filtered = applyFilters(
                state.items, state.filterStatus, query, state.filterGroup, state.categoryGroups,
                state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
                state.filterStatuses, state.filterPendingBalanceOnly
            )
            val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
            _uiState.update { it.copy(filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
            updateTotalPrice(sorted)
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
        brandId: Long? = null,
        statuses: Set<ItemStatus>? = null,
        pendingBalanceOnly: Boolean = false
    ): List<Item> {
        var result = items

        if (pendingBalanceOnly) {
            result = result.filter { it.status == ItemStatus.PENDING_BALANCE }
        } else if (statuses != null) {
            result = result.filter { it.status in statuses }
        } else if (status != null) {
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
            result = result.filter { item ->
                val itemColors = item.colors?.let { json ->
                    try { Gson().fromJson(json, Array<String>::class.java).toList() }
                    catch (_: Exception) { emptyList() }
                } ?: emptyList()
                itemColors.contains(color)
            }
        }

        if (brandId != null) {
            result = result.filter { it.brandId == brandId }
        }

        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }

        return result
    }

    private fun applySorting(items: List<Item>, sort: SortOption, prices: Map<Long, Double>): List<Item> {
        return when (sort) {
            SortOption.DEFAULT -> items
            SortOption.DATE_DESC -> items.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> items.sortedBy { it.updatedAt }
            SortOption.PRICE_DESC -> items.sortedByDescending { prices[it.id] ?: 0.0 }
            SortOption.PRICE_ASC -> items.sortedBy { prices[it.id] ?: 0.0 }
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingBalanceOnly
        )
        val sorted = applySorting(filtered, option, state.itemPrices)
        _uiState.update { it.copy(filteredItems = sorted, itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)) }
        updateTotalPrice(sorted)
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
    private val seasonRepository: SeasonRepository = com.lolita.app.di.AppModule.seasonRepository(),
    private val sourceRepository: SourceRepository = com.lolita.app.di.AppModule.sourceRepository(),
    private val locationRepository: com.lolita.app.data.repository.LocationRepository = com.lolita.app.di.AppModule.locationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemEditUiState())
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    private val pendingImageDeletions = mutableListOf<String>()
    var hasUnsavedChanges: Boolean = false
        private set

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load all reference data with .first() to avoid race conditions
            val brands = brandRepository.getAllBrands().first()
            val categories = categoryRepository.getAllCategories().first()
            val coordinates = coordinateRepository.getAllCoordinates().first()
            val styles = styleRepository.getAllStyles().first()
            val seasons = seasonRepository.getAllSeasons().first()
            val sources = sourceRepository.getAllSources().first()

            _uiState.update {
                it.copy(
                    brands = brands,
                    categories = categories,
                    coordinates = coordinates,
                    styleOptions = styles.map { s -> s.name },
                    seasonOptions = seasons.map { s -> s.name },
                    sourceOptions = sources.map { s -> s.name }
                )
            }

            // Load locations
            viewModelScope.launch {
                locationRepository.getAllLocations().collect { locations ->
                    _uiState.update { it.copy(locations = locations) }
                }
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
                            colors = item.colors?.let { json ->
                                try { Gson().fromJson(json, Array<String>::class.java).toList() }
                                catch (_: Exception) { listOfNotNull(json.takeIf { it.isNotBlank() }) }
                            } ?: emptyList(),
                            seasons = item.season?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList(),
                            style = item.style,
                            size = item.size,
                            sizeChartImageUrl = item.sizeChartImageUrl,
                            source = item.source,
                            locationId = item.locationId,
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
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateBrand(brandId: Long) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(brandId = brandId)
    }

    fun updateCategory(categoryId: Long) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    fun updateCoordinate(coordinateId: Long?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(coordinateId = coordinateId)
    }

    fun updateStatus(status: ItemStatus) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun updatePriority(priority: ItemPriority) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun updateImageUrl(imageUrl: String?) {
        hasUnsavedChanges = true
        val oldUrl = _uiState.value.imageUrl
        if (oldUrl != null && imageUrl != oldUrl) {
            pendingImageDeletions.add(oldUrl)
        }
        _uiState.value = _uiState.value.copy(imageUrl = imageUrl)
    }

    fun updateColors(colors: List<String>) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(colors = colors)
    }

    fun toggleSeason(season: String) {
        hasUnsavedChanges = true
        val current = _uiState.value.seasons
        val updated = if (season in current) current - season else current + season
        _uiState.value = _uiState.value.copy(seasons = updated)
    }

    fun updateStyle(style: String?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(style = style)
    }

    fun updateSource(source: String?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(source = source)
    }

    fun updateSize(size: String?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(size = size)
    }

    fun updateSizeChartImageUrl(url: String?) {
        hasUnsavedChanges = true
        val oldUrl = _uiState.value.sizeChartImageUrl
        if (oldUrl != null && url != oldUrl) {
            pendingImageDeletions.add(oldUrl)
        }
        _uiState.value = _uiState.value.copy(sizeChartImageUrl = url)
    }

    fun updateLocation(locationId: Long?) {
        _uiState.update { it.copy(locationId = locationId) }
        hasUnsavedChanges = true
    }

    fun isValid(): Boolean {
        val state = _uiState.value
        return state.name.isNotBlank() && state.brandId != 0L && state.categoryId != 0L
    }

    suspend fun saveItem(): Result<Unit> {
        val state = _uiState.value

        if (state.name.isBlank()) {
            return Result.failure(Exception("请输入服饰名称"))
        }

        if (state.brandId == 0L) {
            return Result.failure(Exception("请选择品牌"))
        }

        if (state.categoryId == 0L) {
            return Result.failure(Exception("请选择类型"))
        }

        _uiState.value = _uiState.value.copy(isSaving = true)

        return try {
            val now = System.currentTimeMillis()
            val seasonStr = state.seasons.takeIf { it.isNotEmpty() }?.joinToString(",")
            val colorsJson = if (state.colors.isNotEmpty()) {
                Gson().toJson(state.colors)
            } else null
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
                    colors = colorsJson,
                    season = seasonStr,
                    style = state.style,
                    size = state.size,
                    sizeChartImageUrl = state.sizeChartImageUrl,
                    source = state.source,
                    locationId = state.locationId,
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
                    colors = colorsJson,
                    season = seasonStr,
                    style = state.style,
                    size = state.size,
                    sizeChartImageUrl = state.sizeChartImageUrl,
                    source = state.source,
                    locationId = state.locationId,
                    createdAt = now,
                    updatedAt = now
                )
            }

            if (state.item != null) {
                itemRepository.updateItem(item)
            } else {
                itemRepository.insertItem(item)
            }
            // Delete pending images after successful save
            val deletions = pendingImageDeletions.toList()
            pendingImageDeletions.clear()
            deletions.forEach { path ->
                try { ImageFileHelper.deleteImage(path) } catch (_: Exception) { }
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.failure(e)
        }
    }

    suspend fun deleteItem(): Result<Unit> {
        val item = _uiState.value.item
            ?: return Result.failure(Exception("服饰不存在"))

        return try {
            itemRepository.deleteItem(item)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
