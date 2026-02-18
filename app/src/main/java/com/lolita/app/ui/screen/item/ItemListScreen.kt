package com.lolita.app.ui.screen.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.coordinate.CoordinateListContent
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    onNavigateToCoordinateDetail: (Long) -> Unit = {},
    onNavigateToCoordinateAdd: () -> Unit = {},
    onNavigateToCoordinateEdit: (Long) -> Unit = {},
    viewModel: ItemListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemToDelete by remember { mutableStateOf<Item?>(null) }
    var showFilterPanel by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val activeFilterCount = listOfNotNull(
        uiState.filterSeason, uiState.filterStyle, uiState.filterColor,
        uiState.filterBrandId?.let { "" }
    ).size

    // Sync pager -> filter
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> viewModel.filterByStatus(null)
            1 -> viewModel.filterByStatus(ItemStatus.OWNED)
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
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
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
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (pagerState.currentPage == 2) onNavigateToCoordinateAdd()
                    else onNavigateToEdit(null)
                },
                containerColor = Pink400,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (pagerState.currentPage == 2) "添加套装" else "添加服饰",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar with filter + column toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("搜索服饰") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pink400,
                        cursorColor = Pink400
                    )
                )
                Box {
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "过滤",
                            tint = if (activeFilterCount > 0) Pink400 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (activeFilterCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(16.dp),
                            shape = CircleShape,
                            color = Pink400
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$activeFilterCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = {
                        val next = when (uiState.columnsPerRow) {
                            1 -> 2; 2 -> 3; else -> 1
                        }
                        viewModel.setColumns(next)
                    }
                ) {
                    Icon(
                        imageVector = when (uiState.columnsPerRow) {
                            1 -> Icons.Default.ViewAgenda
                            2 -> Icons.Default.GridView
                            else -> Icons.Default.Apps
                        },
                        contentDescription = "切换列数",
                        tint = Pink400
                    )
                }
            }

            // Filter panel
            AnimatedVisibility(visible = showFilterPanel) {
                FilterPanel(
                    uiState = uiState,
                    onSeasonSelected = { viewModel.filterBySeason(it) },
                    onStyleSelected = { viewModel.filterByStyle(it) },
                    onColorSelected = { viewModel.filterByColor(it) },
                    onBrandSelected = { viewModel.filterByBrand(it) }
                )
            }

            // Tab row: 全部 / 已拥有 / 套装
            ItemFilterTabRow(
                selectedIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            // Category group filter + total price (only on item tabs)
            if (pagerState.currentPage < 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val groupOptions = listOf<Pair<String, CategoryGroup?>>(
                        "全部" to null,
                        "服装" to CategoryGroup.CLOTHING,
                        "小物" to CategoryGroup.ACCESSORY
                    )
                    groupOptions.forEach { (label, group) ->
                        FilterChip(
                            selected = uiState.filterGroup == group,
                            onClick = { viewModel.filterByGroup(group) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Pink400.copy(alpha = 0.15f),
                                selectedLabelColor = Pink400
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (uiState.showTotalPrice && uiState.totalPrice > 0) {
                        Text(
                            "¥%.0f".format(uiState.totalPrice),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Pink400
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < 2) {
                    // Item list content
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Pink400)
                            }
                        } else if (uiState.filteredItems.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Home,
                                title = "暂无服饰",
                                subtitle = "点击右下角 + 添加新服饰",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (uiState.columnsPerRow == 1) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(
                                    items = uiState.filteredItems,
                                    key = { it.id }
                                ) { item ->
                                    ItemCard(
                                        item = item,
                                        brandName = uiState.brandNames[item.brandId],
                                        categoryName = uiState.categoryNames[item.categoryId],
                                        itemPrice = uiState.itemPrices[item.id],
                                        showPrice = uiState.showTotalPrice,
                                        onClick = { onNavigateToDetail(item.id) },
                                        onEdit = { onNavigateToEdit(item.id) },
                                        onDelete = { itemToDelete = item },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(uiState.columnsPerRow),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(
                                    items = uiState.filteredItems,
                                    key = { it.id }
                                ) { item ->
                                    ItemGridCard(
                                        item = item,
                                        brandName = uiState.brandNames[item.brandId],
                                        categoryName = uiState.categoryNames[item.categoryId],
                                        itemPrice = uiState.itemPrices[item.id],
                                        showPrice = uiState.showTotalPrice,
                                        onClick = { onNavigateToDetail(item.id) },
                                        onEdit = { onNavigateToEdit(item.id) },
                                        onDelete = { itemToDelete = item }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Coordinate list content (tab 3)
                    CoordinateListContent(
                        onNavigateToDetail = onNavigateToCoordinateDetail,
                        onNavigateToEdit = onNavigateToCoordinateEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemFilterTabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("全部", "已拥有", "套装")

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = Pink400,
        divider = { HorizontalDivider(color = Pink100) }
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selectedContentColor = Pink400,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCard(
    item: Item,
    brandName: String?,
    categoryName: String?,
    itemPrice: Double?,
    showPrice: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val categoryInitial = categoryName?.firstOrNull()?.toString() ?: "?"
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(Pink300.copy(alpha = 0.5f), Pink400.copy(alpha = 0.3f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = categoryInitial,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pink400
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (showPrice && itemPrice != null && itemPrice > 0) {
                        Text(
                            text = "¥%.0f".format(itemPrice),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pink400,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Pink400)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = { showMenu = false; onEdit() },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }

                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    brandName?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                    categoryName?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                    item.color?.let { color ->
                        if (color.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = color,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = when (item.status) {
                        ItemStatus.OWNED -> Pink300.copy(alpha = 0.3f)
                        ItemStatus.WISHED -> Pink400.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                ItemStatus.OWNED -> Icons.Default.Check
                                ItemStatus.WISHED -> Icons.Default.Favorite
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = when (item.status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "愿望单"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemGridCard(
    item: Item,
    brandName: String?,
    categoryName: String?,
    itemPrice: Double?,
    showPrice: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    LolitaCard(
        modifier = modifier.fillMaxWidth()
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
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = categoryName?.firstOrNull()?.toString() ?: "?"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Pink300.copy(alpha = 0.5f),
                                            Pink400.copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Pink400
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Info area
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    brandName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                                        ItemStatus.OWNED -> Pink300
                                        ItemStatus.WISHED -> Pink400
                                    }
                                )
                        )
                        Text(
                            text = when (item.status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "愿望单"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                )
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
    onBrandSelected: (Long?) -> Unit
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
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = if (selectedValue != null) {
                    {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清除",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onClear() }
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Pink400.copy(alpha = 0.15f),
                    selectedLabelColor = Pink400
                ),
                modifier = Modifier.height(30.dp)
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
                        placeholder = { Text("搜索", fontSize = 13.sp) },
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
                                fontSize = 13.sp,
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
