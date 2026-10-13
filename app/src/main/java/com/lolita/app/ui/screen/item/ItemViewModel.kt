package com.lolita.app.ui.screen.item

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.ItemPurchaseReview
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
import com.lolita.app.ui.screen.common.ViewMode
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.TimeZone

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
    val viewMode: ViewMode = ViewMode.LIST,
    val galleryCardDataList: List<ItemCardData> = emptyList(),
    val itemPrices: Map<Long, Double> = emptyMap(),
    val errorMessage: String? = null,
    val todayOutfitItemImages: List<String?> = emptyList(),
    val hasTodayOutfit: Boolean = false,
    val todayOutfitLogId: Long? = null,
    val itemCardDataList: List<ItemCardData> = emptyList(),
    val purchaseReviews: List<ItemPurchaseReview> = emptyList()
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
    val parentItemId: Long? = null,
    val includedInParentPrice: Boolean = false,
    val allItems: List<Item> = emptyList(),
    val children: List<Item> = emptyList(),
    val legacyPriceCount: Int = 0,
    val status: ItemStatus = ItemStatus.OWNED,
    val priority: ItemPriority = ItemPriority.MEDIUM,
    val imageUrls: List<String> = emptyList(),
    val imageUrlsToDelete: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val style: String? = null,
    val size: String? = null,
    val sizeChartImageUrl: String? = null,
    val sizeChartImageUrls: List<String> = emptyList(),
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
    val locations: List<com.lolita.app.data.local.entity.Location> = emptyList(),
    val purchaseReviews: List<ItemPurchaseReview> = emptyList(),
    val purchaseReviewsLoaded: Boolean = false
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
    private val locationRepository: com.lolita.app.data.repository.LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    private val outfitLogRepository: com.lolita.app.data.repository.OutfitLogRepository = com.lolita.app.di.AppModule.outfitLogRepository(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val currentTimeZone: () -> TimeZone = TimeZone::getDefault
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    private var totalPriceJob: Job? = null
    private var searchJob: Job? = null
    private val todayOutfitDay = MutableStateFlow(
        outfitLogRepository.dayRangeAt(currentTimeMillis(), currentTimeZone())
    )

    private val _locations = MutableStateFlow<List<com.lolita.app.data.local.entity.Location>>(emptyList())
    val locations: StateFlow<List<com.lolita.app.data.local.entity.Location>> = _locations.asStateFlow()

    private val _locationItemCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val locationItemCounts: StateFlow<Map<Long, Int>> = _locationItemCounts.asStateFlow()

    private val _unassignedItemCount = MutableStateFlow(0)
    val unassignedItemCount: StateFlow<Int> = _unassignedItemCount.asStateFlow()

    private val _locationItemImages = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val locationItemImages: StateFlow<Map<Long, List<String>>> = _locationItemImages.asStateFlow()

    init {
        loadItems()
        loadPreferences()
        loadTodayOutfit()
        loadLocations()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadTodayOutfit() {
        viewModelScope.launch {
            todayOutfitDay.flatMapLatest { range -> outfitLogRepository.observeOutfitLogByDay(range) }
                .retryWhen { error, _ ->
                    if (error is CancellationException) throw error
                    android.util.Log.w("ItemListVM", "Failed to observe today's outfit log", error)
                    delay(1_000)
                    true
                }.collect { todayLog ->
                    _uiState.update {
                        it.copy(
                            hasTodayOutfit = todayLog != null,
                            todayOutfitLogId = todayLog?.outfitLog?.id,
                            todayOutfitItemImages = todayLog?.items?.take(3)
                                ?.map { item -> item.imageUrls.firstOrNull() } ?: emptyList()
                        )
                    }
                }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshTodayOutfitDay()
                // Check at midnight, with a bounded wait in case the clock changes
                // while the screen's broadcast listener is not active.
                delay((todayOutfitDay.value.endExclusive - currentTimeMillis()).coerceIn(1L, 60_000L))
            }
        }
    }

    fun refreshTodayOutfitDay() {
        todayOutfitDay.value = outfitLogRepository.dayRangeAt(currentTimeMillis(), currentTimeZone())
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
        viewModelScope.launch {
            locationRepository.getLocationItemImages().collect { images ->
                _locationItemImages.value = images
                    .groupBy { it.locationId }
                    .mapValues { (_, items) -> items.take(4).map { it.imageUrls.firstOrNull() ?: "" } }
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.showTotalPrice.collect { show ->
                _uiState.update { it.copy(showTotalPrice = show, itemCardDataList = buildItemCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, show)) }
            }
        }
        viewModelScope.launch {
            appPreferences.viewMode.collect { mode ->
                _uiState.update {
                    it.copy(
                        viewMode = mode,
                        galleryCardDataList = if (mode == ViewMode.GALLERY) {
                            buildGalleryCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                        } else emptyList()
                    )
                }
            }
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            combine(
                itemRepository.getAllItems(),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories(),
                priceRepository.getItemPriceSums(),
                itemRepository.getPurchaseReviews()
            ) { items, brands, categories, priceSums, reviews ->
                val brandMap = brands.associate { it.id to it.name }
                val brandLogoMap = brands.associate { it.id to it.logoUrl }
                val categoryMap = categories.associate { it.id to it.name }
                val groupMap = categories.associate { it.id to it.group }
                val priceMap = priceSums.associate { it.itemId to it.totalPrice }
                val seasonOpts = items.flatMap { it.season?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotBlank() } ?: emptyList() }.distinct().sorted()
                val styleOpts = items.mapNotNull { it.style?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
                val colorOpts = items.flatMap { item ->
                    item.colors
                }.filter { it.isNotBlank() }.distinct().sorted()
                ItemListData(items, brandMap, brandLogoMap, categoryMap, groupMap, priceMap, seasonOpts, styleOpts, colorOpts, reviews)
            }.collect { data ->
                val currentState = _uiState.value
                val filtered = applyFilters(
                    data.items, currentState.filterStatus, currentState.searchQuery, currentState.filterGroup,
                    data.groupMap, currentState.filterSeason, currentState.filterStyle, currentState.filterColor, currentState.filterBrandId,
                    currentState.filterStatuses, currentState.filterPendingBalanceOnly
                )
                val sorted = applySorting(filtered, currentState.sortOption, data.priceMap)
                val newItemCardDataList = buildItemCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.priceMap, currentState.showTotalPrice)
                _uiState.update {
                    it.copy(
                        items = data.items,
                        purchaseReviews = data.purchaseReviews,
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
                        itemCardDataList = newItemCardDataList,
                        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                            buildGalleryCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.priceMap, currentState.showTotalPrice)
                        } else emptyList()
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
        val colorOpts: List<String>,
        val purchaseReviews: List<ItemPurchaseReview>
    )

    private fun updateTotalPrice(filteredItems: List<Item>) {
        totalPriceJob?.cancel()
        totalPriceJob = viewModelScope.launch {
            val visibleRootIds = filteredItems.filter { it.parentItemId == null }.map { it.id }.toSet()
            val itemIds = (filteredItems.map { it.id } + _uiState.value.items
                .filter { it.parentItemId in visibleRootIds && !it.includedInParentPrice }
                .map { it.id }).distinct()
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

    private fun buildGalleryCardDataList(
        filteredItems: List<Item>,
        brandNames: Map<Long, String>,
        brandLogoUrls: Map<Long, String?>,
        categoryNames: Map<Long, String>,
        itemPrices: Map<Long, Double>,
        showPrice: Boolean
    ): List<ItemCardData> {
        return filteredItems
            .filter { it.imageUrls.isNotEmpty() }
            .map { item ->
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
        _uiState.update {
            it.copy(
                filterStatuses = statuses,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterPendingBalanceOnly = newValue,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterGroup = group,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterSeason = season,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterStyle = style,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterColor = color,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
        _uiState.update {
            it.copy(
                filterBrandId = brandId,
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
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
            _uiState.update {
                it.copy(
                    filteredItems = sorted,
                    itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                    galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                        buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                    } else emptyList()
                )
            }
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
        // Parts remain searchable but do not crowd the default wardrobe overview.
        var result = if (query.isBlank() && !pendingBalanceOnly) items.filter { it.parentItemId == null } else items

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
                item.colors.contains(color)
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
        _uiState.update {
            it.copy(
                filteredItems = sorted,
                itemCardDataList = buildItemCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
                galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
        updateTotalPrice(sorted)
    }

    fun setColumns(count: Int) {
        _uiState.update { it.copy(columnsPerRow = count) }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update {
            it.copy(
                viewMode = mode,
                galleryCardDataList = if (mode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
        viewModelScope.launch { appPreferences.setViewMode(mode) }
    }

    fun shuffleGalleryItems() {
        _uiState.update {
            it.copy(
                galleryCardDataList = buildGalleryCardDataList(
                    it.filteredItems,
                    it.brandNames,
                    it.brandLogoUrls,
                    it.categoryNames,
                    it.itemPrices,
                    it.showTotalPrice
                ).shuffled()
            )
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item)
            } catch (e: SQLiteConstraintException) {
                _uiState.update { it.copy(errorMessage = "此服饰已被套装引用，无法删除。请先从套装中移除后再试。") }
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

    private val _pendingImageDeletions = MutableStateFlow(emptySet<String>())
    private var locationJob: Job? = null
    private var hierarchyJob: Job? = null
    private var priceJob: Job? = null
    private var supportingDataLoaded = AtomicBoolean(false)
    var hasUnsavedChanges: Boolean = false
        private set

    private val importedImages = mutableListOf<String>()
    private var appliedImport: String? = null

    fun loadItem(itemId: Long, defaultStatus: String = "", defaultParentItemId: Long? = null, importedDraftJson: String? = null) {
        if (importedDraftJson != null && appliedImport == importedDraftJson) return
        val importDraft = importedDraftJson?.let { json ->
            runCatching { com.google.gson.Gson().fromJson(json, com.lolita.app.data.file.TaobaoProductDraft::class.java) }.getOrNull()
        }
        if (importDraft != null) {
            appliedImport = importedDraftJson
            importedImages.clear()
            importedImages.addAll(importDraft.imagePaths)
            importedImages.addAll(importDraft.sizeChartImagePaths.orEmpty())
            importDraft.sizeChartImagePath?.let { importedImages.add(it) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadReferenceData()
            ensureLocationCollection()
            priceJob?.cancel()
            hierarchyJob?.cancel()
            _pendingImageDeletions.update { emptySet() }
            hasUnsavedChanges = false

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
                            parentItemId = item.parentItemId,
                            includedInParentPrice = item.includedInParentPrice,
                            status = item.status,
                            priority = item.priority,
                            imageUrls = item.imageUrls,
                            imageUrlsToDelete = emptyList(),
                            colors = item.colors,
                            seasons = item.season?.split(",")?.filter { season -> season.isNotBlank() } ?: emptyList(),
                            style = item.style,
                            size = item.size,
                            sizeChartImageUrl = item.sizeChartImageUrl,
                            sizeChartImageUrls = item.sizeChartImageUrls,
                            source = item.source,
                            locationId = item.locationId,
                            pricesWithPayments = prices,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                observeHierarchy(itemId)
                priceJob = viewModelScope.launch {
                    priceRepository.getPricesWithPaymentsByItem(itemId).collect { prices ->
                        val historicalCount = priceRepository.getPricesByItemList(itemId).size - prices.size
                        _uiState.update { it.copy(pricesWithPayments = prices, legacyPriceCount = historicalCount.coerceAtLeast(0)) }
                    }
                }
                return@launch
            }

            val defaultItemStatus = defaultStatus
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { ItemStatus.valueOf(it) }.getOrNull() }
                ?: ItemStatus.OWNED

            val defaultParent = defaultParentItemId?.let { itemRepository.getItemById(it) }
            _uiState.update { current ->
                current.copy(
                    item = null,
                    name = "",
                    description = "",
                    brandId = defaultParent?.brandId ?: 0L,
                    categoryId = 0L,
                    coordinateId = defaultParent?.coordinateId,
                    parentItemId = defaultParentItemId,
                    includedInParentPrice = defaultParentItemId != null,
                    children = emptyList(),
                    legacyPriceCount = 0,
                    status = defaultItemStatus,
                    priority = ItemPriority.MEDIUM,
                    imageUrls = emptyList(),
                    imageUrlsToDelete = emptyList(),
                    colors = emptyList(),
                    seasons = emptyList(),
                    style = null,
                    size = null,
                    sizeChartImageUrl = null,
                    sizeChartImageUrls = emptyList(),
                    source = null,
                    pricesWithPayments = emptyList(),
                    locationId = null,
                    isLoading = false,
                    isSaving = false
                )
            }
            importDraft?.let { draft ->
                    _uiState.update { it.copy(name = draft.name, description = draft.description,
                        brandId = com.lolita.app.data.file.TaobaoProductParser.matchBrand(it.brands.map { b -> b.name }, draft.brandAliases.orEmpty().ifEmpty { listOf(draft.brand) })?.let { name -> it.brands.first { b -> b.name == name }.id } ?: 0L,
                        categoryId = it.categories.firstOrNull { c -> c.name.equals(draft.category.orEmpty(), ignoreCase = true) }?.id ?: 0L,
                        sizeChartImageUrl = draft.sizeChartImagePaths.orEmpty().firstOrNull() ?: draft.sizeChartImagePath,
                        sizeChartImageUrls = draft.sizeChartImagePaths.orEmpty().drop(1),
                        size = draft.size.ifBlank { null }, source = "淘宝", imageUrls = draft.imagePaths,
                        status = ItemStatus.WISHED) }
                    hasUnsavedChanges = true
            }
            observeHierarchy(0L)
        }
    }

    private fun observeHierarchy(itemId: Long) {
        hierarchyJob = viewModelScope.launch {
            combine(itemRepository.getAllItems(), itemRepository.getPurchaseReviews()) { items, reviews ->
                items to reviews
            }.collect { (items, reviews) ->
                _uiState.update { current ->
                    val persisted = items.find { it.id == itemId }
                    val parent = items.find { it.id == current.parentItemId }
                    val ledgerStatus = if (current.includedInParentPrice) parent?.status
                        else if (current.pricesWithPayments.isNotEmpty()) persisted?.status else null
                    current.copy(
                        allItems = items,
                        purchaseReviews = reviews,
                        purchaseReviewsLoaded = true,
                        children = items.filter { it.parentItemId == itemId && itemId > 0 },
                        item = if (!hasUnsavedChanges && persisted != null) persisted else current.item,
                        status = ledgerStatus ?: current.status
                    )
                }
            }
        }
    }

    suspend fun attachExistingChild(childId: Long, includedInPrice: Boolean): Result<Unit> = runCatching {
        val parentId = _uiState.value.item?.id ?: error("请先保存主件")
        itemRepository.attachChild(childId, parentId, includedInPrice)
    }

    suspend fun detachChild(childId: Long): Result<Unit> = runCatching {
        itemRepository.detachChild(childId)
    }

    private suspend fun loadReferenceData() {
        if (supportingDataLoaded.get()) return

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
                styleOptions = styles.map { style -> style.name },
                seasonOptions = seasons.map { season -> season.name },
                sourceOptions = sources.map { source -> source.name }
            )
        }
        supportingDataLoaded.set(true)
    }

    private fun ensureLocationCollection() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            locationRepository.getAllLocations().collect { locations ->
                _uiState.update { it.copy(locations = locations) }
            }
        }
    }

    fun updateName(name: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(name = name) }
    }

    fun updateDescription(description: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(description = description) }
    }

    fun updateBrand(brandId: Long) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(brandId = brandId) }
    }

    fun updateCategory(categoryId: Long) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun updateCoordinate(coordinateId: Long?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(coordinateId = coordinateId) }
    }

    fun updateParent(parentItemId: Long?) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            val parent = current.allItems.find { it.id == parentItemId }
            current.copy(
                parentItemId = parentItemId,
                includedInParentPrice = parentItemId != null && current.includedInParentPrice,
                status = if (parentItemId != null && current.includedInParentPrice) parent?.status ?: current.status else current.status
            )
        }
    }

    fun updateIncludedInParentPrice(included: Boolean) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            val parent = current.allItems.find { it.id == current.parentItemId }
            current.copy(includedInParentPrice = included && current.parentItemId != null,
                status = if (included) parent?.status ?: current.status else current.status)
        }
    }

    fun updateStatus(status: ItemStatus) {
        if (_uiState.value.includedInParentPrice || _uiState.value.pricesWithPayments.isNotEmpty()) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(status = status) }
    }

    fun updatePriority(priority: ItemPriority) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(priority = priority) }
    }

    fun addImage(url: String) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            if (current.imageUrls.size >= 5) current
            else current.copy(imageUrls = current.imageUrls + url)
        }
    }

    fun removeImage(index: Int) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            val mutableUrls = current.imageUrls.toMutableList()
            if (index !in mutableUrls.indices) current
            else {
                val removed = mutableUrls.removeAt(index)
                current.copy(
                    imageUrls = mutableUrls,
                    imageUrlsToDelete = current.imageUrlsToDelete + removed
                )
            }
        }
    }

    fun reorderImages(fromIndex: Int, toIndex: Int) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            val mutableUrls = current.imageUrls.toMutableList()
            if (fromIndex !in mutableUrls.indices || toIndex !in mutableUrls.indices) current
            else {
                val item = mutableUrls.removeAt(fromIndex)
                mutableUrls.add(toIndex, item)
                current.copy(imageUrls = mutableUrls)
            }
        }
    }

    fun updateColors(colors: List<String>) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(colors = colors) }
    }

    fun toggleSeason(season: String) {
        hasUnsavedChanges = true
        _uiState.update { current ->
            val updated = if (season in current.seasons) current.seasons - season else current.seasons + season
            current.copy(seasons = updated)
        }
    }

    fun updateStyle(style: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(style = style) }
    }

    fun updateSource(source: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(source = source) }
    }

    fun updateSize(size: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(size = size) }
    }

    fun addSizeChartImage(url: String) {
        importedImages.add(url)
        hasUnsavedChanges = true
        _uiState.update { current ->
            if (current.sizeChartImageUrl == null) current.copy(sizeChartImageUrl = url)
            else current.copy(sizeChartImageUrls = current.sizeChartImageUrls + url)
        }
    }

    fun removeSizeChartImage(index: Int) {
        val current = _uiState.value
        val images = (listOfNotNull(current.sizeChartImageUrl) + current.sizeChartImageUrls).toMutableList()
        if (index !in images.indices) return
        val removed = images.removeAt(index)
        hasUnsavedChanges = true
        _pendingImageDeletions.update { it + removed }
        _uiState.update { it.copy(sizeChartImageUrl = images.firstOrNull(), sizeChartImageUrls = images.drop(1)) }
    }

    fun updateSizeChartImageUrl(url: String?) {
        hasUnsavedChanges = true
        val oldUrl = _uiState.value.sizeChartImageUrl
        if (oldUrl != null && url != oldUrl) {
            _pendingImageDeletions.update { it + oldUrl }
        }
        _uiState.update { it.copy(sizeChartImageUrl = url) }
    }

    fun updateLocation(locationId: Long?) {
        _uiState.update { it.copy(locationId = locationId) }
        hasUnsavedChanges = true
    }

    fun isValid(): Boolean {
        val state = _uiState.value
        return state.name.isNotBlank() && state.brandId != 0L && state.categoryId != 0L
    }

    suspend fun saveItem(): Result<Long> {
        val state = _uiState.value
        if (state.isSaving) return Result.failure(Exception("正在保存中"))

        if (state.name.isBlank()) {
            return Result.failure(Exception("请输入服饰名称"))
        }

        if (state.brandId == 0L) {
            return Result.failure(Exception("请选择品牌"))
        }

        if (state.categoryId == 0L) {
            return Result.failure(Exception("请选择类型"))
        }

        _uiState.update { it.copy(isSaving = true) }

        return try {
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
                    parentItemId = state.parentItemId,
                    includedInParentPrice = state.includedInParentPrice,
                    status = state.status,
                    priority = state.priority,
                    imageUrls = state.imageUrls,
                    colors = state.colors,
                    season = seasonStr,
                    style = state.style,
                    size = state.size,
                    sizeChartImageUrl = state.sizeChartImageUrl,
                    sizeChartImageUrls = state.sizeChartImageUrls,
                    source = state.source,
                    locationId = state.locationId,
                    updatedAt = now
                )
            } else {
                // Create new item
                Item(
                    id = 0,
                    coordinateId = state.coordinateId,
                    parentItemId = state.parentItemId,
                    includedInParentPrice = state.includedInParentPrice,
                    brandId = state.brandId,
                    categoryId = state.categoryId,
                    name = state.name,
                    description = state.description,
                    imageUrls = state.imageUrls,
                    status = state.status,
                    priority = state.priority,
                    colors = state.colors,
                    season = seasonStr,
                    style = state.style,
                    size = state.size,
                    sizeChartImageUrl = state.sizeChartImageUrl,
                    sizeChartImageUrls = state.sizeChartImageUrls,
                    source = state.source,
                    locationId = state.locationId,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val savedItemId = if (state.item != null) {
                itemRepository.updateItem(item)
                item.id
            } else {
                itemRepository.insertItem(item)
            }
            importedImages.clear()
            // Delete pending images after successful save
            state.imageUrlsToDelete.forEach { path ->
                try { ImageFileHelper.deleteImage(path) } catch (_: Exception) { }
            }
            _pendingImageDeletions.value.forEach { path ->
                try { ImageFileHelper.deleteImage(path) } catch (_: Exception) { }
            }
            _pendingImageDeletions.update { emptySet() }
            hasUnsavedChanges = false
            val savedItem = itemRepository.getItemById(savedItemId) ?: item.copy(id = savedItemId)
            _uiState.update {
                it.copy(
                    item = savedItem,
                    status = savedItem.status,
                    imageUrls = state.imageUrls,
                    imageUrlsToDelete = emptyList(),
                    isSaving = false
                )
            }
            Result.success(savedItemId)
        } catch (e: Exception) {
            _uiState.update { it.copy(isSaving = false) }
            Result.failure(e)
        }
    }

    override fun onCleared() {
        importedImages.forEach { java.io.File(it).delete() }
        super.onCleared()
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
