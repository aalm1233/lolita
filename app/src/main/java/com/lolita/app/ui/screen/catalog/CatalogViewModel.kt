package com.lolita.app.ui.screen.catalog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.CatalogEntry
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.RemoteBrand
import com.lolita.app.data.local.entity.RemoteCatalogEntry
import com.lolita.app.data.local.entity.RemoteCategory
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CatalogRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.SeasonRepository
import com.lolita.app.data.repository.SharedLibrarySyncRepository
import com.lolita.app.data.repository.SourceRepository
import com.lolita.app.data.repository.StyleRepository
import com.lolita.app.ui.screen.common.SortOption
import com.lolita.app.ui.screen.common.ViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CatalogCardData(
    val entry: CatalogEntry,
    val brandName: String?,
    val brandLogoUrl: String?,
    val categoryName: String?,
    val linkedItemStatus: ItemStatus?,
    val isRemote: Boolean
)

data class CatalogListUiState(
    val entries: List<CatalogEntry> = emptyList(),
    val filteredEntries: List<CatalogEntry> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filterBrandId: Long? = null,
    val filterCategoryId: Long? = null,
    val filterStyle: String? = null,
    val filterSeason: String? = null,
    val filterColor: String? = null,
    val sortOption: SortOption = SortOption.DEFAULT,
    val viewMode: ViewMode = ViewMode.LIST,
    val columnsPerRow: Int = 1,
    val brandNames: Map<Long, String> = emptyMap(),
    val brandLogoUrls: Map<Long, String?> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val styleOptions: List<String> = emptyList(),
    val seasonOptions: List<String> = emptyList(),
    val colorOptions: List<String> = emptyList(),
    val cardDataList: List<CatalogCardData> = emptyList(),
    val galleryCardDataList: List<CatalogCardData> = emptyList()
)

data class CatalogDetailUiState(
    val entry: CatalogEntry? = null,
    val isLoading: Boolean = true,
    val brandName: String? = null,
    val brandLogoUrl: String? = null,
    val categoryName: String? = null,
    val linkedItemStatus: ItemStatus? = null,
    val isRemote: Boolean = false
)

data class CatalogEditUiState(
    val entry: CatalogEntry? = null,
    val name: String = "",
    val description: String = "",
    val brandId: Long? = null,
    val categoryId: Long? = null,
    val seriesName: String = "",
    val referenceUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val imageUrlsToDelete: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val style: String? = null,
    val season: String? = null,
    val size: String? = null,
    val source: String? = null,
    val linkedItemId: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val styleOptions: List<String> = emptyList(),
    val seasonOptions: List<String> = emptyList(),
    val sourceOptions: List<String> = emptyList()
)

class CatalogListViewModel(
    private val catalogRepository: CatalogRepository = com.lolita.app.di.AppModule.catalogRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val sharedLibrarySyncRepository: SharedLibrarySyncRepository = com.lolita.app.di.AppModule.sharedLibrarySyncRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogListUiState())
    val uiState: StateFlow<CatalogListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeCatalogEntries()
    }

    private fun observeCatalogEntries() {
        viewModelScope.launch {
            val localDataFlow = combine(
                catalogRepository.getAllCatalogEntries(),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories(),
                itemRepository.getAllItems()
            ) { entries, brands, categories, items ->
                LocalCatalogData(
                    entries = entries,
                    brandMap = brands.associate { it.id to it.name },
                    brandLogoMap = brands.associate { it.id to it.logoUrl },
                    categoryMap = categories.associate { it.id to it.name },
                    itemStatusMap = items.associate { it.id to it.status }
                )
            }
            val remoteCoreFlow = combine(
                sharedLibrarySyncRepository.observeRemoteCatalogEntries(),
                sharedLibrarySyncRepository.observeRemoteBrands(),
                sharedLibrarySyncRepository.observeRemoteCategories()
            ) { remoteEntries, remoteBrands, remoteCategories ->
                RemoteCatalogCore(
                    entries = remoteEntries,
                    brandMap = remoteBrands.toSyntheticNameMap(),
                    brandLogoMap = remoteBrands.toSyntheticLogoMap(),
                    categoryMap = remoteCategories.toSyntheticCategoryNameMap()
                )
            }
            val remoteRefFlow = combine(
                sharedLibrarySyncRepository.observeRemoteStyles(),
                sharedLibrarySyncRepository.observeRemoteSeasons(),
                sharedLibrarySyncRepository.observeRemoteSources()
            ) { remoteStyles, remoteSeasons, remoteSources ->
                CatalogRemoteRefs(
                    styleNames = remoteStyles.associate { it.publicId to it.name },
                    seasonNames = remoteSeasons.associate { it.publicId to it.name },
                    sourceNames = remoteSources.associate { it.publicId to it.name }
                )
            }

            combine(localDataFlow, remoteCoreFlow, remoteRefFlow) { localData, remoteCore, remoteRefs ->
                val mergedEntries = localData.entries + remoteCore.entries.map { remoteEntry ->
                    remoteEntry.toCatalogEntry(
                        styleNames = remoteRefs.styleNames,
                        seasonNames = remoteRefs.seasonNames,
                        sourceNames = remoteRefs.sourceNames
                    )
                }
                CatalogListData(
                    entries = mergedEntries,
                    brandMap = localData.brandMap + remoteCore.brandMap,
                    brandLogoMap = localData.brandLogoMap + remoteCore.brandLogoMap,
                    categoryMap = localData.categoryMap + remoteCore.categoryMap,
                    itemStatusMap = localData.itemStatusMap,
                    styleOptions = mergedEntries.mapNotNull { it.style?.takeIf(String::isNotBlank) }.distinct().sorted(),
                    seasonOptions = mergedEntries.mapNotNull { it.season?.takeIf(String::isNotBlank) }.distinct().sorted(),
                    colorOptions = mergedEntries.flatMap { it.colors }.filter(String::isNotBlank).distinct().sorted()
                )
            }.collect { data ->
                val currentState = _uiState.value
                val filtered = applyFilters(
                    entries = data.entries,
                    query = currentState.searchQuery,
                    brandId = currentState.filterBrandId,
                    categoryId = currentState.filterCategoryId,
                    style = currentState.filterStyle,
                    season = currentState.filterSeason,
                    color = currentState.filterColor
                )
                val sorted = applySorting(filtered, currentState.sortOption)
                _uiState.update {
                    it.copy(
                        entries = data.entries,
                        filteredEntries = sorted,
                        isLoading = false,
                        brandNames = data.brandMap,
                        brandLogoUrls = data.brandLogoMap,
                        categoryNames = data.categoryMap,
                        styleOptions = data.styleOptions,
                        seasonOptions = data.seasonOptions,
                        colorOptions = data.colorOptions,
                        cardDataList = buildCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.itemStatusMap),
                        galleryCardDataList = buildGalleryCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.itemStatusMap)
                    )
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refreshFilteredEntries()
        }
    }

    fun filterByBrand(brandId: Long?) {
        _uiState.update { it.copy(filterBrandId = brandId) }
        refreshFilteredEntries()
    }

    fun filterByCategory(categoryId: Long?) {
        _uiState.update { it.copy(filterCategoryId = categoryId) }
        refreshFilteredEntries()
    }

    fun filterByStyle(style: String?) {
        _uiState.update { it.copy(filterStyle = style) }
        refreshFilteredEntries()
    }

    fun filterBySeason(season: String?) {
        _uiState.update { it.copy(filterSeason = season) }
        refreshFilteredEntries()
    }

    fun filterByColor(color: String?) {
        _uiState.update { it.copy(filterColor = color) }
        refreshFilteredEntries()
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        refreshFilteredEntries()
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setColumns(count: Int) {
        _uiState.update { it.copy(columnsPerRow = count) }
    }

    private fun refreshFilteredEntries() {
        val state = _uiState.value
        val filtered = applyFilters(
            entries = state.entries,
            query = state.searchQuery,
            brandId = state.filterBrandId,
            categoryId = state.filterCategoryId,
            style = state.filterStyle,
            season = state.filterSeason,
            color = state.filterColor
        )
        val sorted = applySorting(filtered, state.sortOption)
        val itemStatusMap = buildLinkedItemStatusMap(state.cardDataList)
        _uiState.update {
            it.copy(
                filteredEntries = sorted,
                cardDataList = buildCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, itemStatusMap),
                galleryCardDataList = buildGalleryCardDataList(sorted, it.brandNames, it.brandLogoUrls, it.categoryNames, itemStatusMap)
            )
        }
    }

    private fun buildLinkedItemStatusMap(cardDataList: List<CatalogCardData>): Map<Long, ItemStatus> {
        return cardDataList.mapNotNull { card ->
            val linkedItemId = card.entry.linkedItemId ?: return@mapNotNull null
            val status = card.linkedItemStatus ?: return@mapNotNull null
            linkedItemId to status
        }.toMap()
    }

    private fun applyFilters(
        entries: List<CatalogEntry>,
        query: String,
        brandId: Long?,
        categoryId: Long?,
        style: String?,
        season: String?,
        color: String?
    ): List<CatalogEntry> {
        return entries.filter { entry ->
            val matchesQuery = query.isBlank() ||
                entry.name.contains(query, ignoreCase = true) ||
                entry.seriesName?.contains(query, ignoreCase = true) == true ||
                entry.description.contains(query, ignoreCase = true)
            val matchesBrand = brandId == null || entry.brandId == brandId
            val matchesCategory = categoryId == null || entry.categoryId == categoryId
            val matchesStyle = style == null || entry.style == style
            val matchesSeason = season == null || entry.season == season
            val matchesColor = color == null || color in entry.colors
            matchesQuery && matchesBrand && matchesCategory && matchesStyle && matchesSeason && matchesColor
        }
    }

    private fun applySorting(entries: List<CatalogEntry>, sortOption: SortOption): List<CatalogEntry> {
        return when (sortOption) {
            SortOption.DEFAULT, SortOption.DATE_DESC -> entries.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> entries.sortedBy { it.updatedAt }
            SortOption.PRICE_ASC, SortOption.PRICE_DESC -> entries.sortedByDescending { it.updatedAt }
        }
    }

    private fun buildCardDataList(
        entries: List<CatalogEntry>,
        brandMap: Map<Long, String>,
        brandLogoMap: Map<Long, String?>,
        categoryMap: Map<Long, String>,
        itemStatusMap: Map<Long, ItemStatus>
    ): List<CatalogCardData> {
        return entries.map { entry ->
            CatalogCardData(
                entry = entry,
                brandName = entry.brandId?.let(brandMap::get),
                brandLogoUrl = entry.brandId?.let(brandLogoMap::get),
                categoryName = entry.categoryId?.let(categoryMap::get),
                linkedItemStatus = entry.linkedItemId?.let(itemStatusMap::get),
                isRemote = entry.id < 0L
            )
        }
    }

    private fun buildGalleryCardDataList(
        entries: List<CatalogEntry>,
        brandMap: Map<Long, String>,
        brandLogoMap: Map<Long, String?>,
        categoryMap: Map<Long, String>,
        itemStatusMap: Map<Long, ItemStatus>
    ): List<CatalogCardData> {
        return buildCardDataList(
            entries = entries.filter { it.imageUrls.isNotEmpty() },
            brandMap = brandMap,
            brandLogoMap = brandLogoMap,
            categoryMap = categoryMap,
            itemStatusMap = itemStatusMap
        )
    }

    private data class CatalogListData(
        val entries: List<CatalogEntry>,
        val brandMap: Map<Long, String>,
        val brandLogoMap: Map<Long, String?>,
        val categoryMap: Map<Long, String>,
        val itemStatusMap: Map<Long, ItemStatus>,
        val styleOptions: List<String>,
        val seasonOptions: List<String>,
        val colorOptions: List<String>
    )

    private data class LocalCatalogData(
        val entries: List<CatalogEntry>,
        val brandMap: Map<Long, String>,
        val brandLogoMap: Map<Long, String?>,
        val categoryMap: Map<Long, String>,
        val itemStatusMap: Map<Long, ItemStatus>
    )

    private data class RemoteCatalogCore(
        val entries: List<RemoteCatalogEntry>,
        val brandMap: Map<Long, String>,
        val brandLogoMap: Map<Long, String?>,
        val categoryMap: Map<Long, String>
    )

}

class CatalogDetailViewModel(
    private val catalogRepository: CatalogRepository = com.lolita.app.di.AppModule.catalogRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val sharedLibrarySyncRepository: SharedLibrarySyncRepository = com.lolita.app.di.AppModule.sharedLibrarySyncRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogDetailUiState())
    val uiState: StateFlow<CatalogDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun loadCatalogEntry(catalogEntryId: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val localDataFlow = combine(
                catalogRepository.getAllCatalogEntries(),
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories(),
                itemRepository.getAllItems()
            ) { entries, brands, categories, items ->
                CatalogDetailLocalData(
                    entries = entries,
                    brands = brands.associateBy { it.id },
                    categories = categories.associateBy { it.id },
                    itemStatusMap = items.associate { it.id to it.status }
                )
            }
            val remoteCoreFlow = combine(
                sharedLibrarySyncRepository.observeRemoteCatalogEntries(),
                sharedLibrarySyncRepository.observeRemoteBrands(),
                sharedLibrarySyncRepository.observeRemoteCategories()
            ) { remoteEntries, remoteBrands, remoteCategories ->
                CatalogDetailRemoteCore(
                    entries = remoteEntries,
                    brands = remoteBrands.associateBy { it.publicId },
                    categories = remoteCategories.associateBy { it.publicId }
                )
            }
            val remoteRefFlow = combine(
                sharedLibrarySyncRepository.observeRemoteStyles(),
                sharedLibrarySyncRepository.observeRemoteSeasons(),
                sharedLibrarySyncRepository.observeRemoteSources()
            ) { remoteStyles, remoteSeasons, remoteSources ->
                CatalogRemoteRefs(
                    styleNames = remoteStyles.associate { it.publicId to it.name },
                    seasonNames = remoteSeasons.associate { it.publicId to it.name },
                    sourceNames = remoteSources.associate { it.publicId to it.name }
                )
            }

            combine(localDataFlow, remoteCoreFlow, remoteRefFlow) { localData, remoteCore, remoteRefs ->
                val localEntry = localData.entries.find { it.id == catalogEntryId }
                if (localEntry != null) {
                    val brand = localEntry.brandId?.let(localData.brands::get)
                    val category = localEntry.categoryId?.let(localData.categories::get)
                    return@combine CatalogDetailUiState(
                        entry = localEntry,
                        isLoading = false,
                        brandName = brand?.name,
                        brandLogoUrl = brand?.logoUrl,
                        categoryName = category?.name,
                        linkedItemStatus = localEntry.linkedItemId?.let(localData.itemStatusMap::get),
                        isRemote = false
                    )
                }

                val remoteEntry = remoteCore.entries.firstOrNull {
                    SharedLibrarySyncRepository.syntheticCatalogEntryId(it.publicId) == catalogEntryId
                }
                val remoteBrand = remoteEntry?.brandPublicId?.let(remoteCore.brands::get)
                val remoteCategory = remoteEntry?.categoryPublicId?.let(remoteCore.categories::get)
                val mappedRemoteEntry = remoteEntry?.toCatalogEntry(
                    styleNames = remoteRefs.styleNames,
                    seasonNames = remoteRefs.seasonNames,
                    sourceNames = remoteRefs.sourceNames
                )
                CatalogDetailUiState(
                    entry = mappedRemoteEntry,
                    isLoading = false,
                    brandName = remoteBrand?.name,
                    brandLogoUrl = remoteBrand?.logoUrl,
                    categoryName = remoteCategory?.name,
                    linkedItemStatus = null,
                    isRemote = mappedRemoteEntry != null
                )
            }.collect { _uiState.value = it }
        }
    }

    suspend fun deleteCatalogEntry(): Result<Unit> {
        if (_uiState.value.isRemote) {
            return Result.failure(Exception("Shared catalog entries are read only"))
        }
        val entry = _uiState.value.entry ?: return Result.failure(Exception("图鉴记录不存在"))
        return try {
            catalogRepository.deleteCatalogEntry(entry)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private data class CatalogRemoteRefs(
    val styleNames: Map<String, String>,
    val seasonNames: Map<String, String>,
    val sourceNames: Map<String, String>
)

private data class CatalogDetailLocalData(
    val entries: List<CatalogEntry>,
    val brands: Map<Long, Brand>,
    val categories: Map<Long, Category>,
    val itemStatusMap: Map<Long, ItemStatus>
)

private data class CatalogDetailRemoteCore(
    val entries: List<RemoteCatalogEntry>,
    val brands: Map<String, RemoteBrand>,
    val categories: Map<String, RemoteCategory>
)

private fun RemoteCatalogEntry.toCatalogEntry(
    styleNames: Map<String, String>,
    seasonNames: Map<String, String>,
    sourceNames: Map<String, String>
): CatalogEntry {
    return CatalogEntry(
        id = SharedLibrarySyncRepository.syntheticCatalogEntryId(publicId),
        name = name,
        brandId = brandPublicId?.let(SharedLibrarySyncRepository::syntheticRemoteId),
        categoryId = categoryPublicId?.let(SharedLibrarySyncRepository::syntheticRemoteId),
        seriesName = seriesName,
        referenceUrl = referenceUrl,
        imageUrls = imageUrls,
        colors = colors,
        style = stylePublicId?.let(styleNames::get),
        season = seasonPublicId?.let(seasonNames::get),
        size = size,
        source = sourcePublicId?.let(sourceNames::get),
        description = description,
        linkedItemId = null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )
}

private fun List<RemoteBrand>.toSyntheticNameMap(): Map<Long, String> {
    return associate { SharedLibrarySyncRepository.syntheticRemoteId(it.publicId) to it.name }
}

private fun List<RemoteBrand>.toSyntheticLogoMap(): Map<Long, String?> {
    return associate { SharedLibrarySyncRepository.syntheticRemoteId(it.publicId) to it.logoUrl }
}

private fun List<RemoteCategory>.toSyntheticCategoryNameMap(): Map<Long, String> {
    return associate { SharedLibrarySyncRepository.syntheticRemoteId(it.publicId) to it.name }
}

class CatalogEditViewModel(
    private val catalogRepository: CatalogRepository = com.lolita.app.di.AppModule.catalogRepository(),
    private val brandRepository: BrandRepository = com.lolita.app.di.AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = com.lolita.app.di.AppModule.categoryRepository(),
    private val styleRepository: StyleRepository = com.lolita.app.di.AppModule.styleRepository(),
    private val seasonRepository: SeasonRepository = com.lolita.app.di.AppModule.seasonRepository(),
    private val sourceRepository: SourceRepository = com.lolita.app.di.AppModule.sourceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogEditUiState())
    val uiState: StateFlow<CatalogEditUiState> = _uiState.asStateFlow()

    private val pendingImageDeletions = mutableListOf<String>()
    private var supportingDataLoaded = false

    var hasUnsavedChanges: Boolean = false
        private set

    fun loadCatalogEntry(catalogEntryId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadReferenceData()
            pendingImageDeletions.clear()
            hasUnsavedChanges = false

            val entry = catalogEntryId?.takeIf { it > 0 }?.let { catalogRepository.getCatalogEntryByIdOnce(it) }
            _uiState.update { current ->
                current.copy(
                    entry = entry,
                    name = entry?.name ?: "",
                    description = entry?.description ?: "",
                    brandId = entry?.brandId,
                    categoryId = entry?.categoryId,
                    seriesName = entry?.seriesName.orEmpty(),
                    referenceUrl = entry?.referenceUrl.orEmpty(),
                    imageUrls = entry?.imageUrls ?: emptyList(),
                    imageUrlsToDelete = emptyList(),
                    colors = entry?.colors ?: emptyList(),
                    style = entry?.style,
                    season = entry?.season,
                    size = entry?.size,
                    source = entry?.source,
                    linkedItemId = entry?.linkedItemId,
                    isLoading = false,
                    isSaving = false
                )
            }
        }
    }

    private suspend fun loadReferenceData() {
        if (supportingDataLoaded) return

        val brands = brandRepository.getAllBrands().first()
        val categories = categoryRepository.getAllCategories().first()
        val styles = styleRepository.getAllStyles().first()
        val seasons = seasonRepository.getAllSeasons().first()
        val sources = sourceRepository.getAllSources().first()

        _uiState.update {
            it.copy(
                brands = brands,
                categories = categories,
                styleOptions = styles.map { style -> style.name },
                seasonOptions = seasons.map { season -> season.name },
                sourceOptions = sources.map { source -> source.name }
            )
        }
        supportingDataLoaded = true
    }

    fun updateName(name: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(name = name) }
    }

    fun updateDescription(description: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(description = description) }
    }

    fun updateBrand(brandId: Long?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(brandId = brandId) }
    }

    fun updateCategory(categoryId: Long?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun updateSeriesName(seriesName: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(seriesName = seriesName) }
    }

    fun updateReferenceUrl(referenceUrl: String) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(referenceUrl = referenceUrl) }
    }

    fun addImage(url: String) {
        hasUnsavedChanges = true
        if (_uiState.value.imageUrls.size >= 9) return
        _uiState.update { it.copy(imageUrls = it.imageUrls + url) }
    }

    fun removeImage(index: Int) {
        hasUnsavedChanges = true
        val current = _uiState.value.imageUrls.toMutableList()
        if (index !in current.indices) return
        val removed = current.removeAt(index)
        _uiState.update {
            it.copy(
                imageUrls = current,
                imageUrlsToDelete = it.imageUrlsToDelete + removed
            )
        }
    }

    fun updateColors(colors: List<String>) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(colors = colors) }
    }

    fun updateStyle(style: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(style = style) }
    }

    fun updateSeason(season: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(season = season) }
    }

    fun updateSize(size: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(size = size) }
    }

    fun updateSource(source: String?) {
        hasUnsavedChanges = true
        _uiState.update { it.copy(source = source) }
    }

    suspend fun saveCatalogEntry(): Result<Long> {
        val state = _uiState.value
        if (state.name.isBlank()) {
            return Result.failure(Exception("请输入图鉴名称"))
        }

        _uiState.update { it.copy(isSaving = true) }

        return try {
            val now = System.currentTimeMillis()
            val entry = if (state.entry != null) {
                state.entry.copy(
                    name = state.name,
                    description = state.description,
                    brandId = state.brandId,
                    categoryId = state.categoryId,
                    seriesName = state.seriesName.ifBlank { null },
                    referenceUrl = state.referenceUrl.ifBlank { null },
                    imageUrls = state.imageUrls,
                    colors = state.colors,
                    style = state.style,
                    season = state.season,
                    size = state.size,
                    source = state.source,
                    updatedAt = now
                )
            } else {
                CatalogEntry(
                    name = state.name,
                    description = state.description,
                    brandId = state.brandId,
                    categoryId = state.categoryId,
                    seriesName = state.seriesName.ifBlank { null },
                    referenceUrl = state.referenceUrl.ifBlank { null },
                    imageUrls = state.imageUrls,
                    colors = state.colors,
                    style = state.style,
                    season = state.season,
                    size = state.size,
                    source = state.source,
                    linkedItemId = state.linkedItemId,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val savedId = if (state.entry != null) {
                catalogRepository.updateCatalogEntry(entry)
                entry.id
            } else {
                catalogRepository.insertCatalogEntry(entry)
            }

            state.imageUrlsToDelete.forEach { path ->
                try {
                    ImageFileHelper.deleteImage(path)
                } catch (_: Exception) {
                }
            }
            pendingImageDeletions.forEach { path ->
                try {
                    ImageFileHelper.deleteImage(path)
                } catch (_: Exception) {
                }
            }
            pendingImageDeletions.clear()
            hasUnsavedChanges = false
            _uiState.update {
                it.copy(
                    entry = entry.copy(id = savedId),
                    imageUrlsToDelete = emptyList(),
                    isSaving = false
                )
            }
            Result.success(savedId)
        } catch (e: Exception) {
            _uiState.update { it.copy(isSaving = false) }
            Result.failure(e)
        }
    }

    suspend fun deleteCatalogEntry(): Result<Unit> {
        val entry = _uiState.value.entry ?: return Result.failure(Exception("图鉴记录不存在"))
        return try {
            catalogRepository.deleteCatalogEntry(entry)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
