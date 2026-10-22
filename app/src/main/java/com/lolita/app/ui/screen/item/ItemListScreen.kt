package com.lolita.app.ui.screen.item

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.lolita.app.ui.navigation.LocalHazeState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.animation.SkinTabIndicator
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.heroSharedElement
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.domain.model.WardrobeFamily
import com.lolita.app.ui.screen.common.StorageLocationPicker
import com.lolita.app.ui.screen.common.SkinEmptyState
import com.lolita.app.ui.screen.common.WardrobeEmptyIllustration
import com.lolita.app.ui.screen.common.SortMenuButton
import com.lolita.app.ui.screen.common.SortOption
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.BrandLogo
import com.lolita.app.ui.screen.coordinate.CoordinateListContent
import com.lolita.app.ui.screen.coordinate.CoordinateListViewModel
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.screen.common.findColorHex
import com.lolita.app.ui.screen.common.ViewMode
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as staggeredItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.lolita.app.ui.screen.common.GalleryCard
import com.lolita.app.ui.component.GalleryPreviewDialog
import com.lolita.app.ui.theme.metadata
import com.lolita.app.ui.theme.LolitaSkin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.screen.common.ShimmerCircle
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    onNavigateToCoordinateDetail: (Long) -> Unit = {},
    onNavigateToCoordinateAdd: () -> Unit = {},
    onNavigateToCoordinateEdit: (Long) -> Unit = {},
    onNavigateToQuickOutfit: () -> Unit = {},
    onNavigateToLocationDetail: (Long) -> Unit = {},
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> },
    viewModel: ItemListViewModel = viewModel(),
    onAddItem: (WardrobeFamily?) -> Unit = { onNavigateToEdit(null) },
    onImportOrders: () -> Unit = {},
    onImportProduct: () -> Unit = {},
    onRestoreBackup: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshTodayOutfitDay()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refreshTodayOutfitDay()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModel.refreshTodayOutfitDay()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    val coordinateViewModel: CoordinateListViewModel = viewModel()
    val coordinateUiState by coordinateViewModel.uiState.collectAsState()
    var itemToDelete by remember { mutableStateOf<Item?>(null) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val filterAccent = if (isDark) skin.accentColorDark else skin.accentColor
    val hazeState = LocalHazeState.current
    val topBarBlurEnabled = skin.topBarBlurEnabled && hazeState != null
    val topBarGradient = if (isSystemInDarkTheme()) {
        Brush.horizontalGradient(skin.gradientColorsDark)
    } else {
        Brush.horizontalGradient(skin.gradientColors)
    }
    val pagerState = rememberPagerState(pageCount = { 4 })
    ObserveSkinScroll(pagerState)
    val coroutineScope = rememberCoroutineScope()
    val activeFilterCount = listOfNotNull(
        uiState.filterSeason, uiState.filterStyle, uiState.filterColor,
        uiState.filterBrandId?.let { "" }, uiState.filterFamily?.let { "" }, uiState.filterCategoryId?.let { "" },
        uiState.filterLocationId?.let { "" }, uiState.filterStorageNodeId?.let { "" }, uiState.filterSize.takeIf { it.isNotBlank() }, uiState.filterSpecification.takeIf { it.isNotBlank() }
    ).size
    val emptyWardrobe = pagerState.currentPage == 1 && !uiState.isLoading && uiState.filteredItems.isEmpty()
    var chooseImport by remember { mutableStateOf(false) }
    if (chooseImport) AlertDialog(onDismissRequest = { chooseImport = false }, title = { Text("从淘宝导入") }, text = {
        Column {
            TextButton(onClick = { chooseImport = false; onImportProduct() }) { Text("商品链接导入（试用）") }
            TextButton(onClick = { chooseImport = false; onImportOrders() }) { Text("订单文件导入") }
        }
    }, confirmButton = { TextButton(onClick = { chooseImport = false }) { Text("取消") } })

    // Auto-focus search field when entering search mode
    LaunchedEffect(isSearchMode) {
        if (isSearchMode) searchFocusRequester.requestFocus()
    }

    // Sync pager -> filter
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> { /* Location tab - no status filter needed */ }
            1 -> viewModel.filterByStatuses(setOf(ItemStatus.OWNED, ItemStatus.PENDING_BALANCE, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_REVIEW))
        }
    }
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${itemToDelete!!.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(itemToDelete!!)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    SkinIcon(IconKey.Delete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // H5: Show error message when delete fails (e.g. RESTRICT foreign key)
    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("操作未完成") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!emptyWardrobe) {
            FloatingActionButton(
                onClick = {
                    when (pagerState.currentPage) {
                        1 -> onAddItem(uiState.filterFamily)
                        2 -> onNavigateToCoordinateAdd()
                        3 -> onNavigateToEdit(null)
                        else -> onNavigateToEdit(null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                SkinIcon(IconKey.Add, tint = Color.White)
            }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Unified top bar (Xiaohongshu-style: search icon | tabs | actions)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (topBarBlurEnabled) {
                            Modifier.hazeEffect(
                                state = hazeState!!,
                                style = HazeStyle(
                                    backgroundColor = if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint,
                                    tint = HazeTint(
                                        (if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint)
                                            .copy(alpha = skin.topBarBlurAlpha)
                                    ),
                                    blurRadius = 25.dp
                                )
                            )
                        } else {
                            Modifier
                        }
                    ),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (topBarBlurEnabled) Modifier else Modifier.background(topBarGradient))
                        .statusBarsPadding()
                ) {
                    AnimatedContent(
                        targetState = isSearchMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "topBarMode"
                    ) { searchMode ->
                        if (searchMode) {
                            // Search mode: pill TextField + cancel
                            SearchModeBar(
                                query = uiState.searchQuery,
                                onQueryChange = { viewModel.search(it) },
                                onCancel = {
                                    viewModel.search("")
                                    isSearchMode = false
                                },
                                focusRequester = searchFocusRequester,
                                placeholder = "搜索物品"
                            )
                        } else {
                            // Normal mode: search icon | tabs | action icons
                            NormalModeBar(
                                pagerState = pagerState,
                                onSearchClick = { isSearchMode = true },
                                onTabSelected = { index ->
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                showFilterPanel = showFilterPanel,
                                onFilterClick = {
                                    if (pagerState.currentPage == 1) {
                                        showFilterPanel = !showFilterPanel
                                    }
                                },
                                activeFilterCount = if (pagerState.currentPage == 1) activeFilterCount else 0,
                                showViewControls = pagerState.currentPage == 1 || pagerState.currentPage == 2,
                                currentSort = when (pagerState.currentPage) {
                                    2 -> coordinateUiState.sortOption
                                    else -> uiState.sortOption
                                },
                                showPriceOptions = when (pagerState.currentPage) {
                                    1 -> uiState.showTotalPrice
                                    2 -> coordinateUiState.showPrice
                                    else -> false
                                },
                                onSortSelected = {
                                    when (pagerState.currentPage) {
                                        2 -> coordinateViewModel.setSortOption(it)
                                        else -> viewModel.setSortOption(it)
                                    }
                                },
                                onViewModeToggle = {
                                    if (pagerState.currentPage == 2) {
                                        val next = when (coordinateUiState.columnsPerRow) { 1 -> 2; 2 -> 3; else -> 1 }
                                        coordinateViewModel.setColumns(next)
                                    } else {
                                        when {
                                            uiState.viewMode == ViewMode.LIST -> {
                                                viewModel.setViewMode(ViewMode.GRID)
                                                viewModel.setColumns(2)
                                            }
                                            uiState.viewMode == ViewMode.GRID && uiState.columnsPerRow < 3 -> {
                                                viewModel.setColumns(3)
                                            }
                                            uiState.viewMode == ViewMode.GRID -> {
                                                viewModel.setViewMode(ViewMode.GALLERY)
                                            }
                                            else -> {
                                                viewModel.setViewMode(ViewMode.LIST)
                                                viewModel.setColumns(1)
                                            }
                                        }
                                    }
                                },
                                viewModeIcon = when (pagerState.currentPage) {
                                    2 -> when (coordinateUiState.columnsPerRow) {
                                        1 -> IconKey.ViewAgenda
                                        2 -> IconKey.GridView
                                        else -> IconKey.Apps
                                    }
                                    else -> when {
                                        uiState.viewMode == ViewMode.LIST -> IconKey.ViewAgenda
                                        uiState.viewMode == ViewMode.GRID && uiState.columnsPerRow == 2 -> IconKey.GridView
                                        uiState.viewMode == ViewMode.GRID -> IconKey.Apps
                                        else -> IconKey.Gallery
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Filter panel
            AnimatedVisibility(visible = showFilterPanel && pagerState.currentPage == 1) {
                FilterPanel(
                    uiState = uiState,
                    onSeasonSelected = { viewModel.filterBySeason(it) },
                    onStyleSelected = { viewModel.filterByStyle(it) },
                    onColorSelected = { viewModel.filterByColor(it) },
                    onBrandSelected = { viewModel.filterByBrand(it) },
                    viewModel = viewModel, locations = viewModel.locations.collectAsState().value
                )
            }

            if (pagerState.currentPage == 0) TodayOutfitShortcut(
                hasTodayOutfit = uiState.hasTodayOutfit,
                imageUrl = uiState.todayOutfitItemImages.firstOrNull { !it.isNullOrBlank() },
                onClick = onNavigateToQuickOutfit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = skin.spacingSmall)
            )

            PurchaseReviewShortcut(
                reviewCount = uiState.purchaseReviews.size,
                showPrices = uiState.showTotalPrice,
                onClick = { onNavigateToFilteredList("status_review", "", "待核对购买资料") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = skin.spacingSmall)
            )

            // Category group filter + total price (only on item tabs)
            if (pagerState.currentPage == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val familyOptions = listOf<Pair<String, WardrobeFamily?>>("全部" to null) + WardrobeFamily.entries.map { it.label to it }
                    familyOptions.forEach { (label, family) ->
                        FilterChip(selected = uiState.filterFamily == family,
                            onClick = { viewModel.filterByFamily(family) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) })
                    }
                    FilterChip(
                        selected = uiState.filterPendingPaymentOnly,
                        onClick = { viewModel.togglePendingPaymentOnly() },
                        label = { Text("待付款", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = filterAccent.copy(alpha = 0.15f),
                            selectedLabelColor = filterAccent
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (uiState.showTotalPrice && uiState.totalPrice > 0) {
                        Text(
                            "¥%.0f".format(uiState.totalPrice),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                0 -> {
                    val locations by viewModel.locations.collectAsState()
                    val locationItemCounts by viewModel.locationItemCounts.collectAsState()
                    val locationItemImages by viewModel.locationItemImages.collectAsState()
                    val unassignedCount by viewModel.unassignedItemCount.collectAsState()
                    LocationListContent(
                        locations = locations,
                        locationItemCounts = locationItemCounts,
                        locationItemImages = locationItemImages,
                        unassignedItemCount = unassignedCount,
                        onLocationClick = { locationId ->
                            onNavigateToLocationDetail(locationId)
                        }
                    )
                }
                1 -> {
                    // Item list content
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.isLoading) {
                            val shimmer = rememberShimmer(ShimmerBounds.Window)
                            if (uiState.viewMode == ViewMode.GALLERY) {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    verticalItemSpacing = 8.dp,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    items(4) {
                                        GalleryCardSkeleton(
                                            modifier = Modifier.shimmer(shimmer)
                                        )
                                    }
                                }
                            } else if (uiState.columnsPerRow == 1) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    items(5) {
                                        ItemCardSkeleton(
                                            modifier = Modifier.shimmer(shimmer)
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(uiState.columnsPerRow),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    items(6) {
                                        ItemGridCardSkeleton(
                                            modifier = Modifier.shimmer(shimmer)
                                        )
                                    }
                                }
                            }
                        } else if (uiState.filteredItems.isEmpty()) {
                            val filtered = activeFilterCount > 0 || uiState.searchQuery.isNotBlank() || uiState.filterPendingPaymentOnly
                            SkinEmptyState(
                                iconKey = if (filtered) IconKey.Search else IconKey.Home,
                                title = if (filtered) "没有匹配的物品" else "从心爱之物开始整理",
                                subtitle = if (filtered) "调整筛选或搜索，已有资料仍然保留" else "照片、名称，先记下来就好",
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                actionLabel = if (filtered) "清除筛选与搜索" else if (uiState.items.isEmpty()) "添加第一件物品" else "添加物品",
                                onAction = { if (filtered) viewModel.clearWardrobeFilters() else onAddItem(null) },
                                illustration = if (filtered) null else ({ WardrobeEmptyIllustration() }),
                                secondaryActions = { if (!filtered) {
                                    TextButton(onClick = { chooseImport = true }) { Text("从淘宝导入") }
                                    TextButton(onClick = onRestoreBackup) { Text("已有资料？恢复备份") }
                                } }
                            )
                        } else if (uiState.viewMode == ViewMode.GALLERY) {
                            var showPreview by remember { mutableStateOf(false) }
                            var previewStartIndex by remember { mutableIntStateOf(0) }
                            val flingBehavior = rememberSkinFlingBehavior()
                            val staggeredState = rememberLazyStaggeredGridState()
                            ObserveSkinScroll(staggeredState)
                            LazyVerticalStaggeredGrid(
                                state = staggeredState,
                                flingBehavior = flingBehavior,
                                columns = StaggeredGridCells.Fixed(2),
                                verticalItemSpacing = 8.dp,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                staggeredItemsIndexed(
                                    items = uiState.galleryCardDataList,
                                    key = { _, data -> data.item.id }
                                ) { index, data ->
                                    GalleryCard(
                                        data = data,
                                        onClick = {
                                            previewStartIndex = index
                                            showPreview = true
                                        },
                                        onLongClick = { itemToDelete = data.item },
                                        modifier = Modifier.skinItemAppear(index)
                                    )
                                }
                            }
                            if (showPreview) {
                                GalleryPreviewDialog(
                                    items = uiState.galleryCardDataList,
                                    startIndex = previewStartIndex,
                                    onDismiss = { showPreview = false },
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                        } else if (uiState.columnsPerRow == 1) {
                            val flingBehavior = rememberSkinFlingBehavior()
                            val listState = rememberLazyListState()
                            ObserveSkinScroll(listState)
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                flingBehavior = flingBehavior
                            ) {
                                itemsIndexed(
                                    items = uiState.itemCardDataList,
                                    key = { _, data -> data.item.id }
                                ) { index, data ->
                                    SwipeToDeleteContainer(
                                        onDelete = { itemToDelete = data.item }
                                    ) {
                                        ItemCard(
                                            data = data,
                                            onClick = { onNavigateToDetail(data.item.id) },
                                            onEdit = { onNavigateToEdit(data.item.id) },
                                            onDelete = { itemToDelete = data.item },
                                            modifier = Modifier
                                                .skinItemAppear(index)
                                                .animateItem()
                                        )
                                    }
                                }
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            ObserveSkinScroll(gridState)
                            LazyVerticalGrid(
                                state = gridState,
                                flingBehavior = rememberSkinFlingBehavior(),
                                columns = GridCells.Fixed(uiState.columnsPerRow),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                items(
                                    items = uiState.itemCardDataList,
                                    key = { it.item.id }
                                ) { data ->
                                    ItemGridCard(
                                        data = data,
                                        showBrand = !(uiState.columnsPerRow == 3 &&
                                            uiState.categorySemantics[data.item.categoryId]?.family == WardrobeFamily.WIG &&
                                            (data.item.brandId == uiState.unsetBrandId || data.brandName.isNullOrBlank())),
                                        onClick = { onNavigateToDetail(data.item.id) },
                                        onEdit = { onNavigateToEdit(data.item.id) },
                                        onDelete = { itemToDelete = data.item }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Coordinate list content (tab 3)
                    CoordinateListContent(
                        onNavigateToDetail = onNavigateToCoordinateDetail,
                        onNavigateToEdit = onNavigateToCoordinateEdit,
                        viewModel = coordinateViewModel
                    )
                }
                3 -> {
                    WishlistContent(
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToEdit = onNavigateToEdit
                    )
                }
                }
            }
        }
    }
}

@Composable
internal fun PurchaseReviewShortcut(reviewCount: Int, showPrices: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!showPrices || reviewCount <= 0) return
    val skin = LolitaSkin.current
    LolitaCard(
        onClick = onClick,
        modifier = modifier.testTag("purchaseReviewShortcut").semantics(mergeDescendants = true) {},
        variant = CardVariant.COMPACT,
        contentPadding = PaddingValues(skin.spacingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text("待核对购买资料", style = MaterialTheme.typography.titleSmall)
                Text("$reviewCount 件服饰与小物 · 查看清单", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("去核对", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayOutfitShortcut(
    hasTodayOutfit: Boolean,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor
    val status = if (hasTodayOutfit) "已记录" else "尚未记录"

    LolitaCard(
        modifier = modifier.semantics(mergeDescendants = true) { stateDescription = status },
        onClick = onClick,
        variant = CardVariant.COMPACT,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(skin.cornerRadiusMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spacingSmall)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(skin.cornerRadiusSmall))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (hasTodayOutfit && imageUrl != null) {
                    LolitaShimmerImage(
                        model = java.io.File(imageUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        circularRevealEnabled = false
                    )
                } else {
                    SkinIcon(IconKey.Outfit, modifier = Modifier.size(24.dp), tint = accent)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "今日穿搭",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasTodayOutfit) "已记录" else "尚未记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)
            ) {
                Text(
                    text = if (hasTodayOutfit) "查看" else "记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SkinIcon(
                    IconKey.KeyboardArrowRight,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun NormalModeBar(
    pagerState: PagerState,
    onSearchClick: () -> Unit,
    onTabSelected: (Int) -> Unit,
    showFilterPanel: Boolean,
    onFilterClick: () -> Unit,
    activeFilterCount: Int,
    currentSort: SortOption,
    showPriceOptions: Boolean,
    showViewControls: Boolean = true,
    onSortSelected: (SortOption) -> Unit,
    onViewModeToggle: () -> Unit,
    viewModeIcon: IconKey
) {
    val tabs = listOf("位置", "服饰", "套装")
    val pageTabs = listOf("位置", "服饰", "套装", "心愿")
    val contentColor = LocalContentColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search icon (left)
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(32.dp)
        ) {
            SkinIcon(IconKey.Search, tint = contentColor)
        }

        // Tabs (centered)
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .weight(1f),
            containerColor = Color.Transparent,
            contentColor = contentColor,
            divider = {},
            indicator = { tabPositions ->
                SkinTabIndicator(
                    tabPositions = tabPositions,
                    selectedTabIndex = pagerState.currentPage
                )
            }
        ) {
            pageTabs.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.heightIn(min = 34.dp),
                    selectedContentColor = contentColor,
                    unselectedContentColor = contentColor.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }

        // Keep the badge inside a growing container, above the filter's lower half.
        Layout(content = {
            IconButton(onClick = onFilterClick, modifier = Modifier.size(32.dp).testTag("homeFilterButton")) {
                SkinIcon(IconKey.FilterList,
                    tint = if (activeFilterCount > 0) contentColor else contentColor.copy(alpha = 0.7f))
            }
            if (activeFilterCount > 0) {
                Surface(modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .testTag("homeFilterBadge"), shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$activeFilterCount", modifier = Modifier.padding(horizontal = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, softWrap = false,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }) { measurables, constraints ->
            val button = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val badge = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = button.width))
            val topSpace = badge?.let { it.height / 2 + 4.dp.roundToPx() } ?: 0
            layout(button.width, button.height + topSpace) {
                button.placeRelative(0, topSpace)
                badge?.placeRelative(button.width - badge.width, 0)
            }
        }

        // Sort (only on pages with view controls)
        if (showViewControls) {
            SortMenuButton(
                currentSort = currentSort,
                showPriceOptions = showPriceOptions,
                onSortSelected = onSortSelected,
                modifier = Modifier.size(32.dp),
                iconTint = contentColor.copy(alpha = 0.8f)
            )

            // View mode toggle
            IconButton(
                onClick = onViewModeToggle,
                modifier = Modifier.size(32.dp)
            ) {
                SkinIcon(viewModeIcon, tint = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
internal fun SearchModeBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String
) {
    val contentColor = LocalContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = contentColor.copy(alpha = 0.2f)
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                cursorBrush = SolidColor(contentColor),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkinIcon(IconKey.Search, modifier = Modifier.size(18.dp), tint = contentColor.copy(alpha = 0.7f))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text("搜索物品", color = contentColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                            }
                            innerTextField()
                        }
                        if (query.isNotEmpty()) {
                            SkinIcon(
                                IconKey.Close,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onQueryChange("") },
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            )
        }
        Text(
            "取消",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable { onCancel() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemCard(
    data: ItemCardData,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = data.item
    val skin = LolitaSkin.current
    var showMenu by remember { mutableStateOf(false) }
    val imageShape = RoundedCornerShape(skin.cornerRadiusSmall)
    val details = listOfNotNull(
        data.categoryName?.takeIf { it.isNotBlank() },
        item.colors.takeIf { it.isNotEmpty() }?.joinToString("、")
    ).joinToString(" · ")

    LolitaCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)
        ) {
            if (item.imageUrls.isNotEmpty()) {
                LolitaShimmerImage(
                    model = item.imageUrls.firstOrNull()?.let { java.io.File(it) },
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(88.dp)
                        .heroSharedElement("itemImage-${item.id}")
                        .clip(imageShape),
                    contentScale = ContentScale.Crop,
                    placeholderInitial = item.name.firstOrNull()?.toString()
                )
            } else {
                Box(
                    modifier = Modifier.size(88.dp).clip(imageShape).background(
                        Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.categoryName?.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (data.showPrice && data.itemPrice != null && data.itemPrice > 0) {
                        Text(
                            text = "¥%.0f".format(data.itemPrice),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = skin.spacingSmall)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            SkinIcon(IconKey.Edit, tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = { showMenu = false; onEdit() },
                                leadingIcon = { SkinIcon(IconKey.Edit) }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error) },
                                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }
                data.brandName?.let { brand ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)
                    ) {
                        BrandLogo(logoUrl = data.brandLogoUrl, brandName = brand, size = 14.dp)
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.parentItemId != null) {
                    Text(
                        text = if (item.includedInParentPrice) "小物 · 随主件计价" else "小物 · 独立计价",
                        style = MaterialTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(
                            when (item.status) {
                                ItemStatus.OWNED -> MaterialTheme.colorScheme.tertiary
                                ItemStatus.WISHED -> MaterialTheme.colorScheme.primary
                                ItemStatus.PENDING_BALANCE, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_REVIEW -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    )
                    Text(
                        text = when (item.status) {
                            ItemStatus.OWNED -> "已拥有"
                            ItemStatus.WISHED -> "心愿"
                            ItemStatus.PENDING_BALANCE -> "待补尾款"
                            ItemStatus.PENDING_PAYMENT -> "待付款"
                            ItemStatus.PENDING_REVIEW -> "待核对"
                        },
                        style = MaterialTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// region Skeleton composables for loading state

@Composable
private fun ItemCardSkeleton(modifier: Modifier = Modifier) {
    LolitaCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerRect(
                width = 88.dp,
                height = 88.dp,
                shape = RoundedCornerShape(12.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerLine(widthFraction = 0.7f, height = 20.dp)
                ShimmerLine(widthFraction = 0.5f, height = 16.dp)
                ShimmerLine(widthFraction = 0.4f, height = 14.dp)
            }
        }
    }
}

@Composable
private fun ItemGridCardSkeleton(modifier: Modifier = Modifier) {
    LolitaCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            ShimmerRect(
                width = 200.dp,
                height = 160.dp,
                shape = RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerLine(widthFraction = 0.8f, height = 40.dp)
                ShimmerLine(widthFraction = 0.5f, height = 14.dp)
                ShimmerLine(widthFraction = 0.4f, height = 14.dp)
            }
        }
    }
}

@Composable
private fun GalleryCardSkeleton(modifier: Modifier = Modifier) {
    val skin = LolitaSkin.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = skin.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) skin.cardContainerColorDark else skin.cardContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation),
        border = skin.cardBorderStroke
    ) {
        ShimmerRect(
            width = 200.dp,
            height = 240.dp,
            shape = skin.cardShape,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// endregion

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ItemGridCard(
    data: ItemCardData,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showBrand: Boolean = true
) {
    val item = data.item
    val brandName = data.brandName.takeIf { showBrand }
    val brandLogoUrl = data.brandLogoUrl
    val categoryName = data.categoryName
    val itemPrice = data.itemPrice
    val showPrice = data.showPrice
    var showMenu by remember { mutableStateOf(false) }
    val skin = LolitaSkin.current
    val filterAccent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor

    LolitaCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            Column {
                // Image area with price overlay
                Box {
                    if (item.imageUrls.isNotEmpty()) {
                        LolitaShimmerImage(
                            model = java.io.File(item.imageUrls.first()),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .heroSharedElement("itemImage-${item.id}")
                                .clip(RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium)),
                            contentScale = ContentScale.Crop,
                            placeholderInitial = item.name.firstOrNull()?.toString()
                        )
                    } else {
                        val initial = categoryName?.firstOrNull()?.toString() ?: "?"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = LolitaSkin.current.cornerRadiusMedium, topEnd = LolitaSkin.current.cornerRadiusMedium))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (showPrice && itemPrice != null && itemPrice > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "¥%.0f".format(itemPrice),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.metadata,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Info area
                Column(
                    modifier = Modifier.padding(horizontal = skin.spacingMedium, vertical = skin.spacingMedium),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.parentItemId != null) {
                        Text(if (item.includedInParentPrice) "小物 · 随主件计价" else "小物 · 独立计价",
                            style = MaterialTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    brandName?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BrandLogo(logoUrl = brandLogoUrl, brandName = brandName, size = 14.dp)
                            Text(
                                text = it,
                                style = MaterialTheme.typography.metadata,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (item.status) {
                                        ItemStatus.OWNED -> MaterialTheme.colorScheme.tertiary
                                        ItemStatus.WISHED -> MaterialTheme.colorScheme.primary
                                        ItemStatus.PENDING_BALANCE, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_REVIEW -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                        )
                        Text(
                            text = when (item.status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "心愿"
                                ItemStatus.PENDING_BALANCE -> "待补尾款"
                                ItemStatus.PENDING_PAYMENT -> "待付款"
                                ItemStatus.PENDING_REVIEW -> "待核对"
                            },
                            style = MaterialTheme.typography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showMenu) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { SkinIcon(IconKey.Edit) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = {
                            SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error)
                        },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    uiState: ItemListUiState,
    onSeasonSelected: (String?) -> Unit,
    onStyleSelected: (String?) -> Unit,
    onColorSelected: (String?) -> Unit,
    onBrandSelected: (Long?) -> Unit,
    viewModel: ItemListViewModel,
    locations: List<com.lolita.app.data.local.entity.Location>
) {
    // Collect brand options that items actually use
    val usedBrandIds = uiState.items.map { it.brandId }.toSet()
    val brandOptions = uiState.brandNames.filter { it.key in usedBrandIds }
        .entries.sortedBy { it.value }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WardrobeCategorySelector(uiState.filterCategoryId ?: 0L,
                uiState.categoryOptions.filter { uiState.filterFamily == null || it.categorySemantic.family == uiState.filterFamily },
                { viewModel.filterByCategory(it) })
            if (uiState.filterCategoryId != null) TextButton(onClick = { viewModel.filterByCategory(null) }) { Text("清除类型筛选") }
            OutlinedTextField(uiState.filterSize, viewModel::filterBySize, label = { Text("尺码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            StorageLocationPicker(uiState.filterLocationId, uiState.filterStorageNodeId, locations, uiState.storageNodes, viewModel::filterByStorage)
            if (uiState.filterFamily == WardrobeFamily.WIG || uiState.filterFamily == WardrobeFamily.FOOTWEAR) {
                OutlinedTextField(uiState.filterSpecification, viewModel::filterBySpecification,
                    label = { Text(if (uiState.filterFamily == WardrobeFamily.WIG) "发长或直卷" else "鞋码或跟高") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            // Season
            if (uiState.seasonOptions.isNotEmpty()) {
                FilterOptionRow(
                    label = "季节",
                    options = uiState.seasonOptions,
                    selectedValue = uiState.filterSeason,
                    onSelected = { onSeasonSelected(it) },
                    onClear = { onSeasonSelected(null) }
                )
            }
            // Style
            if (uiState.styleOptions.isNotEmpty()) {
                FilterOptionRow(
                    label = "风格",
                    options = uiState.styleOptions,
                    selectedValue = uiState.filterStyle,
                    onSelected = { onStyleSelected(it) },
                    onClear = { onStyleSelected(null) }
                )
            }
            // Color
            if (uiState.colorOptions.isNotEmpty()) {
                FilterOptionRow(
                    label = "颜色",
                    options = uiState.colorOptions,
                    selectedValue = uiState.filterColor,
                    onSelected = { onColorSelected(it) },
                    onClear = { onColorSelected(null) }
                )
            }
            // Brand
            if (brandOptions.isNotEmpty()) {
                FilterOptionRow(
                    label = "品牌",
                    options = brandOptions.map { it.value },
                    selectedValue = uiState.filterBrandId?.let { uiState.brandNames[it] },
                    onSelected = { name ->
                        val id = brandOptions.firstOrNull { it.value == name }?.key
                        onBrandSelected(id)
                    },
                    onClear = { onBrandSelected(null) },
                    searchable = true
                )
            }
        }
    }
}

@Composable
private fun FilterOptionRow(
    label: String,
    options: List<String>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
    onClear: () -> Unit,
    searchable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            FilterChip(
                selected = selectedValue != null,
                onClick = {
                    searchQuery = ""
                    expanded = true
                },
                label = {
                    Text(
                        text = selectedValue ?: "全部",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (selectedValue != null) {
                    {
                        SkinIcon(IconKey.Close, modifier = Modifier
                                .size(14.dp)
                                .clickable { onClear() })
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.heightIn(min = 30.dp)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                if (searchable) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .width(200.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                val filtered = if (searchQuery.isBlank()) options
                    else options.filter { it.contains(searchQuery, ignoreCase = true) }
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
