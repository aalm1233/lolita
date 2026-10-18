package com.lolita.app.ui.screen.item

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.ItemDraft
import com.lolita.app.data.local.entity.ItemDraftData
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.domain.model.*
import com.lolita.app.data.repository.ItemDraftTargetDeletedException
import com.lolita.app.data.repository.ItemDraftConflictException
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.data.repository.StorageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
    val filterPendingPaymentOnly: Boolean = false,
    val filterGroup: CategoryGroup? = null,
    val filterFamily: WardrobeFamily? = null,
    val filterCategoryId: Long? = null,
    val filterSize: String = "",
    val filterLocationId: Long? = null,
    val filterStorageNodeId: Long? = null,
    val filterSpecification: String = "",
    val categorySemantics: Map<Long, CategorySemantic> = emptyMap(),
    val categoryOptions: List<com.lolita.app.data.local.entity.Category> = emptyList(),
    val storageNodes: List<StorageNode> = emptyList(),
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
    val draftId: String? = null,
    val specifications: WardrobeSpecifications = WardrobeSpecifications(),
    val composition: ItemComposition = ItemComposition.UNCONFIRMED,
    val storageNodeId: Long? = null,
    val storageNodes: List<StorageNode> = emptyList(),
    val item: Item? = null,
    val name: String = "",
    val description: String = "",
    val brandId: Long = 0,
    val categoryId: Long = 0,
    val preferredFamily: WardrobeFamily? = null,
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
    val recoveredImport: Boolean = false,
    val draftError: String? = null,
    val loadError: String? = null,
    val hasDraftConflict: Boolean = false,
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
    val purchaseReviewsLoaded: Boolean = false,
    val isSettingCover: Boolean = false,
    val coverError: String? = null
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
    private val currentTimeZone: () -> TimeZone = TimeZone::getDefault,
    storageRepository: StorageRepository? = null
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
        (storageRepository ?: runCatching {
            if (itemRepository === com.lolita.app.di.AppModule.itemRepository()) com.lolita.app.di.AppModule.storageRepository() else null
        }.getOrNull())?.let { repository -> viewModelScope.launch {
            repository.observeNodes().collect { nodes -> _uiState.update { it.copy(storageNodes = nodes) }; refreshWardrobeFilters() }
        } }
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
            locationRepository.getAllLocations().collect { _locations.value = it; refreshWardrobeFilters() }
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
                val semanticMap = categories.associate { it.id to it.categorySemantic }
                val priceMap = priceSums.associate { it.itemId to it.totalPrice }
                val seasonOpts = items.flatMap { it.season?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotBlank() } ?: emptyList() }.distinct().sorted()
                val styleOpts = items.mapNotNull { it.style?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
                val colorOpts = items.flatMap { item ->
                    item.colors
                }.filter { it.isNotBlank() }.distinct().sorted()
                ItemListData(items, brandMap, brandLogoMap, categoryMap, groupMap, priceMap, seasonOpts, styleOpts, colorOpts, reviews, semanticMap, categories)
            }.collect { data ->
                _uiState.update { it.copy(categorySemantics = data.semanticMap, categoryOptions = data.categories,
                    brandNames = data.brandMap, categoryNames = data.categoryMap) }
                val currentState = _uiState.value
                val filtered = applyFilters(
                    data.items, currentState.filterStatus, currentState.searchQuery, currentState.filterGroup,
                    data.groupMap, currentState.filterSeason, currentState.filterStyle, currentState.filterColor, currentState.filterBrandId,
                    currentState.filterStatuses, currentState.filterPendingPaymentOnly
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
        val purchaseReviews: List<ItemPurchaseReview>,
        val semanticMap: Map<Long, CategorySemantic>,
        val categories: List<com.lolita.app.data.local.entity.Category>
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
                categoryName = categoryNames[item.categoryId]?.let { if (item.parentItemId != null) "$it · 配套小物" else it },
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
                    categoryName = categoryNames[item.categoryId]?.let { if (item.parentItemId != null) "$it · 配套小物" else it },
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
            statuses, state.filterPendingPaymentOnly
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

    fun togglePendingPaymentOnly() {
        val newValue = !_uiState.value.filterPendingPaymentOnly
        val state = _uiState.value
        val filtered = applyFilters(
            state.items, state.filterStatus, state.searchQuery, state.filterGroup, state.categoryGroups,
            state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, newValue
        )
        val sorted = applySorting(filtered, _uiState.value.sortOption, _uiState.value.itemPrices)
        _uiState.update {
            it.copy(
                filterPendingPaymentOnly = newValue,
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
                state.filterStatuses, state.filterPendingPaymentOnly
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

    fun filterByFamily(family: WardrobeFamily?) {
        _uiState.update { it.copy(filterFamily = family, filterGroup = null, filterCategoryId = null, filterSpecification = "") }
        refreshWardrobeFilters()
    }
    fun filterByCategory(categoryId: Long?) { _uiState.update { it.copy(filterCategoryId = categoryId) }; refreshWardrobeFilters() }
    fun filterBySize(raw: String) { _uiState.update { it.copy(filterSize = raw) }; refreshWardrobeFilters() }
    fun filterByStorage(roomId: Long?, nodeId: Long?) {
        _uiState.update { it.copy(filterLocationId = roomId, filterStorageNodeId = nodeId) }; refreshWardrobeFilters()
    }
    fun filterBySpecification(raw: String) { _uiState.update { it.copy(filterSpecification = raw) }; refreshWardrobeFilters() }
    private fun refreshWardrobeFilters() {
        val state = _uiState.value
        val result = applySorting(applyFilters(state.items, state.filterStatus, state.searchQuery, state.filterGroup,
            state.categoryGroups, state.filterSeason, state.filterStyle, state.filterColor, state.filterBrandId,
            state.filterStatuses, state.filterPendingPaymentOnly), state.sortOption, state.itemPrices)
        _uiState.update { it.copy(filteredItems = result,
            itemCardDataList = buildItemCardDataList(result, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice),
            galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) buildGalleryCardDataList(result, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice) else emptyList()) }
        updateTotalPrice(result)
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
        pendingPaymentOnly: Boolean = false
    ): List<Item> {
        val state = _uiState.value
        val hasExplicitFilter = group != null || state.filterFamily != null || state.filterCategoryId != null ||
            state.filterSize.isNotBlank() || state.filterLocationId != null || state.filterStorageNodeId != null ||
            season != null || style != null || color != null || brandId != null
        var result = if (query.isBlank() && !pendingPaymentOnly && !hasExplicitFilter) items.filter { it.parentItemId == null } else items
        state.filterFamily?.let { family -> result = result.filter { state.categorySemantics[it.categoryId]?.family == family } }
        state.filterCategoryId?.let { category -> result = result.filter { it.categoryId == category } }
        if (state.filterSize.isNotBlank()) result = result.filter { it.size.orEmpty().contains(state.filterSize, true) }
        state.filterLocationId?.let { room -> result = result.filter { it.locationId == room } }
        state.filterStorageNodeId?.let { node -> result = result.filter { it.storageNodeId == node } }
        if (state.filterSpecification.isNotBlank()) result = result.filter { item ->
            val specs = WardrobeSpecifications.decode(item.specificationsJson)
            val values = when (state.filterFamily) {
                WardrobeFamily.WIG -> listOf(specs.wig.lengthCm, specs.wig.curl)
                WardrobeFamily.FOOTWEAR -> listOf(specs.footwear.sizeText, specs.footwear.sizeSystem, specs.footwear.heelHeightCm)
                else -> emptyList()
            }
            values.any { it.contains(state.filterSpecification, true) }
        }

        if (pendingPaymentOnly) {
            result = result.filter { it.status == ItemStatus.PENDING_BALANCE || it.status == ItemStatus.PENDING_PAYMENT }
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
            result = result.filter { item ->
                val path = item.locationId?.let { room -> com.lolita.app.domain.model.StorageTree.pathFor(room, item.storageNodeId, _locations.value, state.storageNodes) }.orEmpty()
                listOf(item.name, state.brandNames[item.brandId].orEmpty(), state.categoryNames[item.categoryId].orEmpty(), path)
                    .plus(item.colors).any { it.contains(query, ignoreCase = true) }
            }
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
            state.filterStatuses, state.filterPendingPaymentOnly
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
    private val locationRepository: com.lolita.app.data.repository.LocationRepository = com.lolita.app.di.AppModule.locationRepository(),
    draftRepository: ItemDraftRepository? = null,
    storageRepository: StorageRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
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

    private val importedImages = mutableSetOf<String>()
    private enum class ItemLoadMode { EDITOR, DETAIL }
    private data class EditorIdentity(val itemId: Long, val defaultStatus: String,
        val defaultParentItemId: Long?, val importedDraftJson: String?, val defaultLocationId: Long?,
        val defaultStorageNodeId: Long?, val defaultFamily: WardrobeFamily?, val mode: ItemLoadMode) {
        val hasEntryDefaults: Boolean get() = defaultStatus.isNotBlank() || defaultParentItemId != null ||
            importedDraftJson != null || defaultLocationId != null || defaultStorageNodeId != null || defaultFamily != null
    }
    private var requestedIdentity: EditorIdentity? = null
    private var loadedIdentity: EditorIdentity? = null
    private var loadJob: Job? = null
    private var currentImportToken: String? = null
    // The baseline belongs to the editable snapshot, never to a live observed Item row.
    private var draftBaselineUpdatedAt: Long? = null

    private val drafts = draftRepository ?: runCatching {
        if (itemRepository === com.lolita.app.di.AppModule.itemRepository()) com.lolita.app.di.AppModule.itemDraftRepository() else null
    }.getOrNull()
    private val storage = storageRepository ?: runCatching {
        if (itemRepository === com.lolita.app.di.AppModule.itemRepository()) com.lolita.app.di.AppModule.storageRepository() else null
    }.getOrNull()
    private val draftMutex = Mutex()
    private var discardInProgress = false

    private fun closeDeletedEditor() {
        discardInProgress=true
        hasUnsavedChanges=false
        loadedIdentity=null
        savedStateHandle.remove<String>("itemDraftId")
        _uiState.update { it.copy(item=null,draftId=null,isLoading=false,isSaving=false,draftError=null,
            loadError="物品已删除，编辑草稿已关闭",hasDraftConflict=false) }
    }

    init {
        viewModelScope.launch {
            _uiState.collect { state ->
                if (drafts != null && state.draftId != null && !state.isLoading && !state.isSaving &&
                    state.loadError == null && hasUnsavedChanges && !discardInProgress) {
                    runCatching { draftMutex.withLock {
                        val latest = _uiState.value
                        if (!latest.isLoading && !latest.isSaving && latest.loadError==null && !discardInProgress && hasUnsavedChanges && latest.draftId == state.draftId) persistDraft(latest)
                    } }
                        .onFailure { failure ->
                            if(failure is CancellationException) throw failure
                            if(failure is ItemDraftTargetDeletedException) closeDeletedEditor()
                            else _uiState.update { it.copy(draftError = "草稿保存失败：${failure.message}") }
                        }
                }
            }
        }
        storage?.let { repository -> viewModelScope.launch {
            repository.observeNodes().collect { nodes -> _uiState.update { it.copy(storageNodes = nodes) } }
        } }
    }

    private fun snapshotDraft(state: ItemEditUiState): ItemDraft = ItemDraft(
        id = requireNotNull(state.draftId), targetItemId = state.item?.id,
        imagePaths = state.imageUrls, sizeChartImagePaths = listOfNotNull(state.sizeChartImageUrl) + state.sizeChartImageUrls,
        dataJson = ItemDraftData(name = state.name, description = state.description, brandId = state.brandId,
            categoryId = state.categoryId, coordinateId = state.coordinateId, parentItemId = state.parentItemId,
            includedInParentPrice = state.includedInParentPrice, status = state.status, priority = state.priority,
            colors = state.colors, seasons = state.seasons, style = state.style, size = state.size, source = state.source,
            locationId = state.locationId, storageNodeId = state.storageNodeId, specificationsJson = state.specifications.encode(),
            composition = state.composition, importToken = currentImportToken, baselineUpdatedAt = draftBaselineUpdatedAt).encode())

    private suspend fun persistDraft(state: ItemEditUiState = _uiState.value) {
        drafts?.let { repository ->
            val snapshot = snapshotDraft(state)
            val previous = repository.get(snapshot.id)
            repository.save(snapshot.copy(evidenceImagePaths = previous?.evidenceImagePaths.orEmpty(),
                dataJson = snapshot.data().copy(recognitionSessionIds = previous?.data()?.recognitionSessionIds.orEmpty()).encode()))
        }
    }

    private suspend fun restoreDraft(identity: EditorIdentity): Boolean {
        if (drafts == null) return false
        val restored = if (identity.itemId > 0) drafts.getForItem(identity.itemId)
            else savedStateHandle.get<String>("itemDraftId")?.let { drafts.get(it) ?: error("所选物品草稿已不存在") }
                ?: if (!identity.hasEntryDefaults) drafts.latestNew() else null
        val id = restored?.id ?: java.util.UUID.randomUUID().toString()
        savedStateHandle["itemDraftId"] = id
        if (restored == null) { _uiState.update { it.copy(draftId = id) }; return false }
        val data = restored.data()
        require(restored.targetItemId == null || restored.targetItemId == _uiState.value.item?.id) { "草稿对应的物品已删除" }
        draftBaselineUpdatedAt = data.baselineUpdatedAt
        _uiState.update { it.copy(draftId=id) }
        hasUnsavedChanges = true
        if(restored.targetItemId!=null && (data.baselineUpdatedAt==null || data.baselineUpdatedAt!=_uiState.value.item?.updatedAt)) {
            throw ItemDraftConflictException()
        }
        currentImportToken = data.importToken
        _uiState.update { it.copy(draftId = id, name = data.name, description = data.description,
            brandId = data.brandId, categoryId = data.categoryId, coordinateId = data.coordinateId,
            parentItemId = data.parentItemId, includedInParentPrice = data.includedInParentPrice,
            status = data.status, priority = data.priority, colors = data.colors, seasons = data.seasons,
            style = data.style, size = data.size, source = data.source, locationId = data.locationId,
            storageNodeId = data.storageNodeId, specifications = WardrobeSpecifications.decode(data.specificationsJson),
            composition = data.composition, imageUrls = restored.imagePaths,
            sizeChartImageUrl = restored.sizeChartImagePaths.firstOrNull(), sizeChartImageUrls = restored.sizeChartImagePaths.drop(1)) }
        hasUnsavedChanges = true
        return true
    }

    fun selectDraft(id: String?) {
        if (id != null && requestedIdentity == null) savedStateHandle["itemDraftId"] = id
    }

    suspend fun copyToNewDraft(): String {
        val itemId = _uiState.value.item?.id ?: error("物品尚未读取完成")
        return checkNotNull(drafts) { "无法创建物品草稿" }.copyFromItem(itemId)
    }

    suspend fun keepDraft() {
        val state = _uiState.value
        if (drafts != null && state.draftId != null && !state.isLoading && !state.isSaving && state.loadError == null && !discardInProgress) {
            try {
                draftMutex.withLock {
                    val latest=_uiState.value
                    if(!latest.isLoading && !latest.isSaving && latest.loadError==null && !discardInProgress && latest.draftId==state.draftId) {
                        persistDraft(latest)
                        latest.item?.id?.let { drafts.reconnectRecognition(it,requireNotNull(latest.draftId)) }
                    }
                }
            } catch(failure: ItemDraftTargetDeletedException) { closeDeletedEditor() }
        }
        if (drafts == null) cleanupNewImages()
    }

    suspend fun discardDraft() {
        discardInProgress = true
        draftMutex.withLock { _uiState.value.draftId?.let { drafts?.discard(it) } }
        savedStateHandle.remove<String>("itemDraftId")
        hasUnsavedChanges = false
        cleanupNewImages()
    }

    suspend fun discardConflictingDraftAndReload() {
        val identity=requestedIdentity ?: return
        require(_uiState.value.hasDraftConflict && !_uiState.value.isSaving) { "草稿状态已变化，请重新核对" }
        discardDraft()
        requestedIdentity=null
        loadedIdentity=null
        loadItem(identity.itemId,identity.defaultStatus,identity.defaultParentItemId,identity.importedDraftJson,
            identity.defaultLocationId,identity.defaultStorageNodeId,identity.defaultFamily)
    }

    private fun recognitionPaths(semantic: CategorySemantic): Map<RecognitionField, Pair<String, String>> = mapOf(
        RecognitionField.CLOTHING_LENGTH to ("clothing" to "lengthCm"), RecognitionField.CLOTHING_BUST to ("clothing" to "bustCm"),
        RecognitionField.CLOTHING_WAIST to ("clothing" to "waistCm"), RecognitionField.CLOTHING_HIP to ("clothing" to "hipCm"),
        RecognitionField.CLOTHING_SHOULDER to ("clothing" to "shoulderWidthCm"), RecognitionField.CLOTHING_SLEEVE to ("clothing" to "sleeveLengthCm"),
        RecognitionField.WIG_COLOR to ("wig" to "mainColor"), RecognitionField.WIG_LENGTH to ("wig" to "lengthCm"),
        RecognitionField.WIG_CURL to ("wig" to "curl"), RecognitionField.WIG_BANGS to ("wig" to "bangs"),
        RecognitionField.WIG_PARTING to ("wig" to "parting"), RecognitionField.WIG_MATERIAL to ("wig" to "material"),
        RecognitionField.WIG_HEAT to ("wig" to "heatResistance"), RecognitionField.WIG_HEAD_RANGE to ("wig" to "headCircumference"),
        RecognitionField.WIG_COMPONENTS to ("wig" to "extraHairPieces"),
        RecognitionField.HOSIERY_LENGTH to ("hosiery" to "lengthType"), RecognitionField.HOSIERY_PATTERN to ("hosiery" to "pattern"),
        RecognitionField.HOSIERY_THICKNESS to ("hosiery" to "thickness"), RecognitionField.HOSIERY_DEN to ("hosiery" to "denier"),
        RecognitionField.HOSIERY_FOOT_RANGE to ("hosiery" to "footLength"), RecognitionField.HOSIERY_MATERIAL to ("hosiery" to "material"),
        RecognitionField.HOSIERY_TRANSPARENCY to ("hosiery" to "transparency"),
        RecognitionField.SHOE_SIZE_SYSTEM to ("footwear" to "sizeSystem"), RecognitionField.SHOE_HEEL to ("footwear" to "heelHeightCm"),
        RecognitionField.SHOE_INNER_LENGTH to ("footwear" to "innerLengthCm"), RecognitionField.SHOE_SHAFT to ("footwear" to "shaftHeightCm"),
        RecognitionField.SHOE_TOE to ("footwear" to "toeShape"), RecognitionField.SHOE_FASTENING to ("footwear" to "fastening"),
        RecognitionField.PETTICOAT_LENGTH to ("petticoat" to "lengthCm"), RecognitionField.PETTICOAT_SHAPE to ("petticoat" to "silhouette"),
        RecognitionField.PETTICOAT_VOLUME to ("petticoat" to "volume"), RecognitionField.PETTICOAT_WAIST to ("petticoat" to "waistCm"),
        RecognitionField.PETTICOAT_LAYERS to ("petticoat" to "layers"), RecognitionField.PETTICOAT_MATERIAL to ("petticoat" to "material"),
        RecognitionField.ACCESSORY_POSITION to ("accessory" to "wearPosition"), RecognitionField.ACCESSORY_DIMENSIONS to ("accessory" to "dimensions"),
        RecognitionField.ACCESSORY_FASTENING to ("accessory" to "fastening"), RecognitionField.ACCESSORY_MATERIAL to ("accessory" to "material"),
        RecognitionField.CARRY_METHOD to (if (semantic.family == WardrobeFamily.BAG) "bag" to "carryMode" else "handheld" to "usage"),
        RecognitionField.CARRY_DIMENSIONS to (if (semantic.family == WardrobeFamily.BAG) "bag" to "dimensions" else "handheld" to "dimensions"),
        RecognitionField.CARRY_CAPACITY to ("bag" to "capacity"), RecognitionField.CARRY_FOLDABLE to ("handheld" to "foldable")
    )

    fun currentRecognitionValues(): Map<RecognitionField, String> = recognitionValues(_uiState.value)

    private fun recognitionValues(state: ItemEditUiState): Map<RecognitionField, String> {
        val semantic = state.categories.firstOrNull { it.id == state.categoryId }?.categorySemantic ?: CategorySemantic.UNCONFIRMED
        val profileJson = com.google.gson.Gson().toJsonTree(state.specifications).asJsonObject
        val brand = state.brands.firstOrNull { it.id == state.brandId }
        val base = mutableMapOf(RecognitionField.NAME to state.name,
            RecognitionField.CATEGORY to state.categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty(),
            RecognitionField.BRAND to brand?.takeIf { !it.isUnsetBrand }?.name.orEmpty(),
            RecognitionField.COLOR to state.colors.joinToString("、"), RecognitionField.SIZE to state.size.orEmpty(),
            RecognitionField.PATTERN to state.specifications.pattern,
            RecognitionField.STYLE to state.style.orEmpty(), RecognitionField.SEASON to state.seasons.joinToString("、"))
        recognitionPaths(semantic).forEach { (field, path) -> base[field] = profileJson[path.first].asJsonObject[path.second].asString }
        return RecognitionField.entries.associateWith { base[it].orEmpty() }
    }

    private fun checkedRecognitionState(expectedDraftId: String, operationOwnsSaving: Boolean = false): ItemEditUiState = _uiState.value.also { state ->
        require(state.draftId == expectedDraftId && loadedIdentity == requestedIdentity && loadedIdentity != null &&
            !state.isLoading && (!state.isSaving || operationOwnsSaving) && !discardInProgress && state.loadError == null) {
            "草稿已切换或尚未读取完成，请重新核对"
        }
    }

    private fun recognitionPlan(current: ItemEditUiState, expectedDraftId: String, baseline: Map<RecognitionField,String>,
        candidates: List<RecognitionCandidate>, selectedIds: Set<String>, variant: String?, size: String?,
        resolvedBrandId: Long?, resolvedCategoryId: Long?): Pair<ItemEditUiState,RecognitionMerge> {
        val merge = RecognitionMerge.apply(expectedDraftId, requireNotNull(current.draftId), baseline,
            recognitionValues(current), candidates, selectedIds, variant, size)
        val rejected = mutableSetOf<RecognitionField>()
        if (RecognitionField.BRAND in merge.values && current.brands.none { it.id == resolvedBrandId && !it.isUnsetBrand }) rejected += RecognitionField.BRAND
        if (RecognitionField.CATEGORY in merge.values && current.categories.none { it.id == resolvedCategoryId }) rejected += RecognitionField.CATEGORY
        val selectedCategory = if (RecognitionField.CATEGORY in merge.values && RecognitionField.CATEGORY !in rejected) resolvedCategoryId else current.categoryId
        val semantic = current.categories.firstOrNull { it.id == selectedCategory }?.categorySemantic ?: CategorySemantic.UNCONFIRMED
        val paths = recognitionPaths(semantic)
        val validProfile = specificationProfile(semantic)
        paths.forEach { (field, path) -> if (field in merge.values && path.first != validProfile) rejected += field }
        val values = merge.values.filterKeys { it !in rejected }
        val rejectedIds = candidates.filter { it.id in merge.acceptedIds && it.field in rejected }.map { it.id }.toSet()
        val accepted = merge.acceptedIds - rejectedIds
        val profileJson = com.google.gson.Gson().toJsonTree(current.specifications).asJsonObject
        values.forEach { (field, value) -> paths[field]?.let { (profile, key) -> profileJson[profile].asJsonObject.addProperty(key, value) } }
        values[RecognitionField.PATTERN]?.let { profileJson.addProperty("pattern", it) }
        var newState = current.copy(specifications = WardrobeSpecifications.decode(profileJson.toString()),
            name = values[RecognitionField.NAME] ?: current.name,
            categoryId = if (RecognitionField.CATEGORY in values) requireNotNull(resolvedCategoryId) else current.categoryId,
            brandId = if (RecognitionField.BRAND in values) requireNotNull(resolvedBrandId) else current.brandId,
            colors = values[RecognitionField.COLOR]?.let(::listOf) ?: current.colors,
            size = values[RecognitionField.SIZE] ?: current.size,
            style = values[RecognitionField.STYLE] ?: current.style,
            seasons = values[RecognitionField.SEASON]?.let(::listOf) ?: current.seasons)
        if (RecognitionField.SIZE in values) {
            newState = when (semantic.family) {
                WardrobeFamily.FOOTWEAR -> newState.copy(specifications = newState.specifications.copy(footwear = newState.specifications.footwear.copy(sizeText = values.getValue(RecognitionField.SIZE))))
                WardrobeFamily.HOSIERY -> newState.copy(specifications = newState.specifications.copy(hosiery = newState.specifications.hosiery.copy(sizeText = values.getValue(RecognitionField.SIZE))))
                else -> newState
            }
        }
        val actualValues = recognitionValues(newState)
        return newState to RecognitionMerge(values.mapValues { (field, _) -> actualValues[field].orEmpty() }, accepted, merge.conflicts + rejectedIds)
    }

    private fun publishRecognitionState(next: ItemEditUiState) {
        _uiState.update { it.copy(specifications=next.specifications,name=next.name,categoryId=next.categoryId,
            brandId=next.brandId,colors=next.colors,size=next.size,style=next.style,seasons=next.seasons,isSaving=false) }
    }

    /** Standalone local merge. Recognition task/evidence applies must use applyRecognitionTask. */
    suspend fun applyRecognitionSelection(expectedDraftId: String, baseline: Map<RecognitionField, String>,
        candidates: List<RecognitionCandidate>, selectedIds: Set<String>, variant: String? = null, size: String? = null,
        resolvedBrandId: Long? = null, resolvedCategoryId: Long? = null): RecognitionMerge = draftMutex.withLock {
        val before = checkedRecognitionState(expectedDraftId)
        before.item?.let { require(itemRepository.getItemById(it.id) != null) { "物品已删除，无法应用识图资料" } }
        val (next, merge) = recognitionPlan(checkedRecognitionState(expectedDraftId), expectedDraftId, baseline,
            candidates, selectedIds, variant, size, resolvedBrandId, resolvedCategoryId)
        if (merge.acceptedIds.isNotEmpty()) {
            if (drafts != null) persistDraft(next)
            hasUnsavedChanges = true
            publishRecognitionState(next)
        }
        merge
    }

    /** The only task apply entry: editor mutex -> Room transaction -> publish after commit. */
    suspend fun applyRecognitionTask(repository: com.lolita.app.data.repository.RecognitionRepository,
        taskId: String, review: com.lolita.app.domain.model.RecognitionReview): RecognitionMerge {
        val draftId = requireNotNull(_uiState.value.draftId) { "草稿尚未读取完成" }
        checkedRecognitionState(draftId)
        _uiState.update { it.copy(isSaving = true) }
        try {
            return withContext(NonCancellable) { draftMutex.withLock {
                checkedRecognitionState(draftId, true)
                var committedState: ItemEditUiState? = null
                val merge = repository.apply(taskId,review) { request,candidates,selection ->
                    require(request.draftId == draftId) { "识图结果属于另一份草稿" }
                    val (next, merged) = recognitionPlan(checkedRecognitionState(draftId,true),draftId,request.baseline,
                        candidates,selection.selectedIds,selection.variant,selection.sizeRow,selection.resolvedBrandId,selection.resolvedCategoryId)
                    if (merged.acceptedIds.isNotEmpty()) { persistDraft(next); committedState=next }
                    merged
                }
                committedState?.let { next -> hasUnsavedChanges=true; publishRecognitionState(next) }
                merge
            } }
        } finally { _uiState.update { if(it.draftId==draftId) it.copy(isSaving=false) else it } }
    }

    fun updateSpecifications(value: WardrobeSpecifications) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(specifications = value) }
    }

    fun updateComposition(value: ItemComposition) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(composition = value) }
    }

    fun updateStorage(locationId: Long?, nodeId: Long?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(locationId = locationId, storageNodeId = nodeId) }
    }

    fun loadItem(itemId: Long, defaultStatus: String = "", defaultParentItemId: Long? = null, importedDraftJson: String? = null,
        defaultLocationId: Long? = savedStateHandle.get<Long>("defaultLocationId")?.takeIf { it > 0 },
        defaultStorageNodeId: Long? = savedStateHandle.get<Long>("defaultStorageNodeId")?.takeIf { it > 0 },
        defaultFamily: WardrobeFamily? = savedStateHandle.get<String>("defaultFamily")?.let { name -> WardrobeFamily.entries.find { it.name == name } }) {
        loadItemSnapshot(itemId, defaultStatus, defaultParentItemId, importedDraftJson,
            defaultLocationId, defaultStorageNodeId, defaultFamily, ItemLoadMode.EDITOR)
    }

    /** A detail observes saved business data; an unfinished editor draft belongs to the editor. */
    fun loadDetail(itemId: Long) {
        loadItemSnapshot(itemId, "", null, null, null, null, null, ItemLoadMode.DETAIL)
    }

    private fun loadItemSnapshot(itemId: Long, defaultStatus: String, defaultParentItemId: Long?, importedDraftJson: String?,
        defaultLocationId: Long?, defaultStorageNodeId: Long?, defaultFamily: WardrobeFamily?, mode: ItemLoadMode) {
        if (_uiState.value.isSaving) return
        val identity = EditorIdentity(itemId, if (itemId > 0) "" else defaultStatus,
            if (itemId > 0) null else defaultParentItemId, importedDraftJson,
            if (itemId > 0) null else defaultLocationId, if (itemId > 0) null else defaultStorageNodeId,
            if (itemId > 0) null else defaultFamily, mode)
        if (requestedIdentity == identity && (loadedIdentity == identity || loadJob?.isActive == true)) return
        loadJob?.cancel()
        priceJob?.cancel()
        hierarchyJob?.cancel()
        if (requestedIdentity != identity) {
            cleanupNewImages()
            if (requestedIdentity != null) savedStateHandle.remove<String>("itemDraftId")
        }
        discardInProgress = false
        locationJob?.cancel()
        locationJob = null
        supportingDataLoaded.set(false)
        requestedIdentity = identity
        loadedIdentity = null
        draftBaselineUpdatedAt = null
        _uiState.value = ItemEditUiState(isLoading = true, preferredFamily = identity.defaultFamily)
        val importDraft = importedDraftJson?.let { json ->
            runCatching { com.google.gson.Gson().fromJson(json, com.lolita.app.data.file.TaobaoProductDraft::class.java) }.getOrNull()
        }
        currentImportToken = importDraft?.importToken
        if (importDraft != null) {
            importedImages.addAll(importDraft.imagePaths)
            importedImages.addAll(importDraft.sizeChartImagePaths.orEmpty())
            importDraft.sizeChartImagePath?.let { importedImages.add(it) }
        }
        loadJob = viewModelScope.launch {
            try {
                require(mode != ItemLoadMode.DETAIL || itemId > 0) { "服饰不存在，请重试" }
                loadReferenceData()
                ensureLocationCollection()
                priceJob?.cancel()
                hierarchyJob?.cancel()
                _pendingImageDeletions.update { emptySet() }
                hasUnsavedChanges = false

                val committedImport = if (itemId == 0L) currentImportToken?.let {
                    itemRepository.getItemByImportToken(it)
                } else null
                val effectiveItemId = committedImport?.id ?: itemId
                if (effectiveItemId > 0) {
                    val item = committedImport ?: itemRepository.getItemById(effectiveItemId)
                    if (item != null) {
                        if (mode == ItemLoadMode.EDITOR) draftBaselineUpdatedAt = item.updatedAt
                        val prices = priceRepository.getPricesWithPaymentsByItem(effectiveItemId).first()
                        _uiState.update {
                            it.copy(
                                item = item,
                                name = item.name,
                                description = item.description,
                                brandId = item.brandId,
                                specifications = WardrobeSpecifications.decode(item.specificationsJson),
                                composition = item.composition, storageNodeId = item.storageNodeId,
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
                                recoveredImport = committedImport != null,
                                isLoading = true
                            )
                        }
                    } else {
                        error("服饰不存在，请重试")
                    }
                    if (mode == ItemLoadMode.EDITOR) restoreDraft(identity)
                    if(mode == ItemLoadMode.EDITOR && drafts?.hasRecognitionForItem(effectiveItemId)==true) draftMutex.withLock {
                        persistDraft()
                        drafts.reconnectRecognition(effectiveItemId,requireNotNull(_uiState.value.draftId))
                    }
                    if (committedImport != null) {
                        ImageFileHelper.cleanupUnreferencedImages(importedImages.toList())
                        importedImages.clear()
                    }
                    observeHierarchy(effectiveItemId)
                    priceJob = viewModelScope.launch {
                        priceRepository.getPricesWithPaymentsByItem(effectiveItemId).collect { prices ->
                            val historicalCount = priceRepository.getPricesByItemList(effectiveItemId).size - prices.size
                            _uiState.update { it.copy(pricesWithPayments = prices, legacyPriceCount = historicalCount.coerceAtLeast(0)) }
                        }
                    }
                    // Opening the form is safe only after draft/conflict and recognition recovery finish.
                    loadedIdentity = identity
                    _uiState.update { it.copy(isLoading = false) }
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
                        brandId = defaultParent?.brandId ?: current.brands.firstOrNull { it.isUnsetBrand }?.id ?: 0L,
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
                        isLoading = true,
                        isSaving = false,
                        recoveredImport = false
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
                val restored = restoreDraft(identity)
                if (!restored) {
                    try {
                        identity.defaultLocationId?.let { require(locationRepository.getLocationByIdSync(it) != null) { "预填的房间已删除，请重新选择位置" } }
                        identity.defaultStorageNodeId?.let { nodeId ->
                            val node = storage?.getNodes()?.find { it.id == nodeId }
                            require(node != null && node.locationId == identity.defaultLocationId) { "预填的收纳位置已删除或不属于该房间" }
                        }
                        _uiState.update { it.copy(locationId = identity.defaultLocationId, storageNodeId = identity.defaultStorageNodeId) }
                    } catch (failure: Exception) {
                        savedStateHandle.remove<String>("itemDraftId")
                        _uiState.update { it.copy(draftId = null) }
                        throw failure
                    }
                }
                observeHierarchy(0L)
                loadedIdentity = identity
                _uiState.update { it.copy(isLoading = false) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _uiState.update { it.copy(isLoading = false, loadError = failure.message ?: "读取服饰失败",
                    hasDraftConflict = failure is ItemDraftConflictException) }
            }
        }
    }

    private fun observeHierarchy(itemId: Long) {
        hierarchyJob = viewModelScope.launch {
            combine(itemRepository.getAllItems(), itemRepository.getPurchaseReviews()) { items, reviews ->
                items to reviews
            }.collect { (items, reviews) ->
                if(itemId>0 && _uiState.value.item?.id==itemId && items.none { it.id==itemId }) closeDeletedEditor()
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

    fun setImageCover(path: String) {
        val current = _uiState.value
        val item = current.item ?: return
        if (current.isLoading || current.isSaving || current.isSettingCover || hasUnsavedChanges) return
        val identity = requestedIdentity
        _uiState.update { it.copy(isSettingCover = true, coverError = null) }
        viewModelScope.launch {
            try {
                itemRepository.setCoverImage(item.id, path)
                val latest = itemRepository.getItemById(item.id)
                if (requestedIdentity == identity && latest != null && !hasUnsavedChanges) {
                    if(draftBaselineUpdatedAt==item.updatedAt && latest.copy(imageUrls=item.imageUrls,updatedAt=item.updatedAt)==item) draftBaselineUpdatedAt=latest.updatedAt
                    _uiState.update { it.copy(item = latest, imageUrls = latest.imageUrls) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (requestedIdentity == identity) _uiState.update {
                    it.copy(coverError = failure.message ?: "设置封面失败，请重试")
                }
            } finally {
                if (requestedIdentity == identity) _uiState.update { it.copy(isSettingCover = false) }
            }
        }
    }

    fun clearCoverError() { _uiState.update { it.copy(coverError = null) } }

    suspend fun detachChild(childId: Long): Result<Unit> = runCatching {
        itemRepository.detachChild(childId)
    }

    private suspend fun loadReferenceData() {
        if (supportingDataLoaded.get()) return

        brandRepository.ensureUnsetBrand()
        categoryRepository.ensurePresetCategories()
        val brands = brandRepository.getAllBrands().first()
        val categories = categoryRepository.getAllCategories().first()
        val coordinates = coordinateRepository.getAllCoordinates().first()
        val styles = styleRepository.getAllStyles().first()
        val seasons = seasonRepository.getAllSeasons().first()
        val sources = sourceRepository.getAllSources().first()
        val storageNodes = storage?.getNodes().orEmpty()

        _uiState.update {
            it.copy(
                brands = brands,
                categories = categories,
                coordinates = coordinates,
                styleOptions = styles.map { style -> style.name },
                seasonOptions = seasons.map { season -> season.name },
                sourceOptions = sources.map { source -> source.name },
                storageNodes = storageNodes
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
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(name = name) }
    }

    fun updateDescription(description: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(description = description) }
    }

    fun updateBrand(brandId: Long) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(brandId = brandId) }
    }

    fun updateCategory(categoryId: Long) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun updateCoordinate(coordinateId: Long?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(coordinateId = coordinateId) }
    }

    fun updateParent(parentItemId: Long?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
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
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { current ->
            val parent = current.allItems.find { it.id == current.parentItemId }
            current.copy(includedInParentPrice = included && current.parentItemId != null,
                status = if (included) parent?.status ?: current.status else current.status)
        }
    }

    fun updateStatus(status: ItemStatus) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        if (_uiState.value.includedInParentPrice || _uiState.value.pricesWithPayments.isNotEmpty()) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(status = status) }
    }

    fun updatePriority(priority: ItemPriority) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(priority = priority) }
    }

    fun addImage(url: String) {
        val current = _uiState.value
        if (current.isSaving || current.isLoading || current.imageUrls.size >= 5) {
            if (url !in draftImages()) ImageFileHelper.cleanupUnreferencedImages(listOf(url))
            return
        }
        importedImages.add(url)
        hasUnsavedChanges = true
        _uiState.update { it.copy(imageUrls = it.imageUrls + url) }
    }

    fun removeImage(index: Int) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
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
        cleanupRemovedNewImages()
    }

    fun reorderImages(fromIndex: Int, toIndex: Int) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
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
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(colors = colors) }
    }

    fun toggleSeason(season: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { current ->
            val updated = if (season in current.seasons) current.seasons - season else current.seasons + season
            current.copy(seasons = updated)
        }
    }

    fun updateStyle(style: String?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(style = style) }
    }

    fun updateSource(source: String?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(source = source) }
    }

    fun updateSize(size: String?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        hasUnsavedChanges = true
        _uiState.update { it.copy(size = size) }
    }

    fun addSizeChartImage(url: String) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) {
            if (url !in draftImages()) ImageFileHelper.cleanupUnreferencedImages(listOf(url))
            return
        }
        importedImages.add(url)
        hasUnsavedChanges = true
        _uiState.update { current ->
            if (current.sizeChartImageUrl == null) current.copy(sizeChartImageUrl = url)
            else current.copy(sizeChartImageUrls = current.sizeChartImageUrls + url)
        }
    }

    fun removeSizeChartImage(index: Int) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        val current = _uiState.value
        val images = (listOfNotNull(current.sizeChartImageUrl) + current.sizeChartImageUrls).toMutableList()
        if (index !in images.indices) return
        val removed = images.removeAt(index)
        hasUnsavedChanges = true
        _pendingImageDeletions.update { it + removed }
        _uiState.update { it.copy(sizeChartImageUrl = images.firstOrNull(), sizeChartImageUrls = images.drop(1)) }
        cleanupRemovedNewImages()
    }

    fun updateSizeChartImageUrl(url: String?) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) {
            if (url != null && url !in draftImages()) ImageFileHelper.cleanupUnreferencedImages(listOf(url))
            return
        }
        url?.let { importedImages.add(it) }
        hasUnsavedChanges = true
        val oldUrl = _uiState.value.sizeChartImageUrl
        if (oldUrl != null && url != oldUrl) {
            _pendingImageDeletions.update { it + oldUrl }
        }
        _uiState.update { it.copy(sizeChartImageUrl = url) }
        cleanupRemovedNewImages()
    }

    fun updateLocation(locationId: Long?) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        _uiState.update { it.copy(locationId = locationId) }
        hasUnsavedChanges = true
    }

    fun isValid(): Boolean {
        val state = _uiState.value
        return state.name.isNotBlank() && state.categoryId != 0L
    }

    // The VM owns persistence; a recreated screen may cancel its await without cancelling the save.
    private var saveTask: Deferred<Result<Long>>? = null

    suspend fun saveItem(): Result<Long> {
        val task = saveTask?.takeIf { it.isActive } ?: viewModelScope.async { saveItemDraft() }
            .also { saveTask = it }
        return task.await()
    }

    private suspend fun saveItemDraft(): Result<Long> {
        val state = _uiState.value
        if (state.isLoading) return Result.failure(Exception("正在读取服饰"))
        if (state.loadError != null || (requestedIdentity?.itemId?.let { it > 0 } == true && state.item == null)) {
            return Result.failure(Exception("原记录尚未读取完成，暂时无法保存"))
        }
        if (state.isSaving) return Result.failure(Exception("正在保存中"))
        if (state.isSettingCover) return Result.failure(Exception("正在设置封面"))
        if (state.recoveredImport && !hasUnsavedChanges && state.item != null) {
            return Result.success(state.item.id)
        }

        if (state.name.isBlank()) {
            return Result.failure(Exception("请输入服饰名称"))
        }

        if (state.categoryId == 0L) {
            return Result.failure(Exception("请选择类型"))
        }

        _uiState.update { it.copy(isSaving = true) }

        return try {
            val now = System.currentTimeMillis()
            val resolvedBrandId = state.brandId.takeIf { it > 0 } ?: brandRepository.ensureUnsetBrand()
            val seasonStr = state.seasons.takeIf { it.isNotEmpty() }?.joinToString(",")
            val item = if (state.item != null) {
                // Update existing item
                state.item.copy(
                    name = state.name,
                    description = state.description,
                    brandId = resolvedBrandId,
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
                    storageNodeId = state.storageNodeId, specificationsJson = state.specifications.encode(), composition = state.composition,
                    updatedAt = now
                )
            } else {
                // Create new item
                Item(
                    id = 0,
                    coordinateId = state.coordinateId,
                    parentItemId = state.parentItemId,
                    includedInParentPrice = state.includedInParentPrice,
                    brandId = resolvedBrandId,
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
                    storageNodeId = state.storageNodeId, specificationsJson = state.specifications.encode(), composition = state.composition,
                    createdAt = now,
                    updatedAt = now,
                    importToken = currentImportToken
                )
            }

            val savedItemId = draftMutex.withLock {
                if (drafts != null && state.draftId != null) {
                    persistDraft(state)
                    drafts.commit(state.draftId, item)
                } else if (state.item != null) {
                    itemRepository.updateItem(item); item.id
                } else itemRepository.insertItem(item)
            }
            savedStateHandle.remove<String>("itemDraftId")
            // Store the committed identity before any further suspension.
            _uiState.update { it.copy(item = item.copy(id = savedItemId)) }
            ImageFileHelper.cleanupUnreferencedImages(
                importedImages + state.imageUrlsToDelete + _pendingImageDeletions.value
            )
            importedImages.clear()
            _pendingImageDeletions.update { emptySet() }
            hasUnsavedChanges = false
            val savedItem = itemRepository.getItemById(savedItemId) ?: item.copy(id = savedItemId)
            draftBaselineUpdatedAt=savedItem.updatedAt
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
            if (e is CancellationException) throw e
            if(e is ItemDraftTargetDeletedException) closeDeletedEditor()
            else _uiState.update { it.copy(isSaving = false,
                loadError=if(e is ItemDraftConflictException) e.message else it.loadError,
                hasDraftConflict=it.hasDraftConflict || e is ItemDraftConflictException) }
            Result.failure(e)
        }
    }

    private fun draftImages(): Set<String> = with(_uiState.value) {
        (imageUrls + listOfNotNull(sizeChartImageUrl) + sizeChartImageUrls).toSet()
    }

    private fun cleanupRemovedNewImages() {
        val removed = importedImages - draftImages()
        importedImages.removeAll(removed)
        ImageFileHelper.cleanupUnreferencedImages(removed)
    }

    fun cleanupNewImages() {
        ImageFileHelper.cleanupUnreferencedImages(importedImages.toList())
        importedImages.clear()
    }

    override fun onCleared() {
        // Durable draft owns its images until explicit discard or commit.
        if (drafts == null || _uiState.value.draftId == null) cleanupNewImages()
        super.onCleared()
    }

    suspend fun deleteItem(): Result<Unit> {
        val state=_uiState.value
        val item = state.item ?: return Result.failure(Exception("服饰不存在"))
        if(state.isLoading || state.isSaving || discardInProgress) return Result.failure(Exception("正在处理物品，请稍后再删除"))
        discardInProgress=true
        _uiState.update { it.copy(isSaving=true) }
        return try {
            withContext(NonCancellable) {
                draftMutex.withLock {
                    itemRepository.deleteItem(item)
                    closeDeletedEditor()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if(_uiState.value.item?.id==item.id) {
                discardInProgress=false
                _uiState.update { it.copy(isSaving=false) }
            }
            if(e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
