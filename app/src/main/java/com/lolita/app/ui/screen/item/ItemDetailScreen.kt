package com.lolita.app.ui.screen.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.domain.model.mannequinSubject
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.supportsMaterialPreparation
import com.lolita.app.domain.model.supportsVirtualTryOn
import com.lolita.app.domain.model.productReferenceImages
import com.lolita.app.domain.usecase.OrderAccounting
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.component.ImageGalleryPager
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.BrandLogo
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.FlowRow
import com.lolita.app.ui.screen.common.findColorHex
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lolita.app.ui.screen.common.SectionHeader
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.ShimmerLine
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import androidx.compose.ui.platform.testTag
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

/**
 * Item Detail Screen - 服饰详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToPriceManage: () -> Unit = {},
    onNavigateToRecommendation: (Long) -> Unit = {},
    onNavigateToItem: (Long) -> Unit = {},
    onCreateChild: (Long) -> Unit = {},
    onNavigateToParentPriceManage: (Long) -> Unit = onNavigateToItem,
    onNavigateToTryOn: (Long) -> Unit = {},
    onNavigateToMannequin: (Long) -> Unit = {},
    onNavigateToLocation: (Long) -> Unit = {},
    onCopyToNew: (String) -> Unit = {},
    viewModel: ItemEditViewModel = viewModel(),
    currentMaterial: com.lolita.app.data.local.entity.ItemTryOnMaterial? = null,
    onNavigateToMaterial: (Long) -> Unit = {},
    onViewMaterial: (Long) -> Unit = onNavigateToMaterial
) {
    val uiState by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.36f).dp.coerceIn(240.dp, 320.dp)
    val heroShape = (skin.cardShape as? CornerBasedShape)?.copy(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(0.dp)
    ) ?: RoundedCornerShape(bottomStart = skin.cornerRadiusMedium, bottomEnd = skin.cornerRadiusMedium)
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showAttachPicker by remember { mutableStateOf(false) }
    var childToAttach by remember { mutableStateOf<Item?>(null) }
    var shareChildPrice by remember { mutableStateOf(false) }
    var childToDetach by remember { mutableStateOf<Item?>(null) }
    LaunchedEffect(uiState.coverError) {
        uiState.coverError?.let { showError = it; viewModel.clearCoverError() }
    }

    // Load item data
    LaunchedEffect(itemId) {
        viewModel.loadDetail(itemId)
    }

    // Show error dialog
    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("提示") },
            text = { Text(showError ?: "") },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    // Show delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这件服饰吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        coroutineScope.launch {
                            viewModel.deleteItem()
                                .onSuccess { onBack() }
                                .onFailure { showError = it.message }
                        }
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAttachPicker) {
        val parentIds = uiState.allItems.mapNotNull { it.parentItemId }.toSet()
        ItemSearchPickerDialog(
            title = "挂入已有小物",
            items = uiState.allItems.filter { it.id != itemId && it.parentItemId == null && it.id !in parentIds },
            onSelect = { childToAttach = it; shareChildPrice = false; showAttachPicker = false },
            onDismiss = { showAttachPicker = false }
        )
    }
    childToAttach?.let { child ->
        AlertDialog(
            onDismissRequest = { childToAttach = null },
            title = { Text("挂入 ${child.name}") },
            text = {
                Column {
                    Text("挂载只改变所属主件，保留小物的图片、尺寸与穿搭记录。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shareChildPrice, onCheckedChange = { shareChildPrice = it })
                        Text("包含在主件价格中")
                    }
                    Text(if (shareChildPrice) "不单独计价，随主件付清后变为已拥有"
                        else "保留独立价格，按自己的付款判断收货状态",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        viewModel.attachExistingChild(child.id, shareChildPrice)
                            .onSuccess { childToAttach = null }
                            .onFailure { childToAttach = null; showError = it.message }
                    }
                }) { Text("挂入") }
            },
            dismissButton = { TextButton(onClick = { childToAttach = null }) { Text("取消") } }
        )
    }
    childToDetach?.let { child ->
        AlertDialog(
            onDismissRequest = { childToDetach = null },
            title = { Text("解除小物挂载") },
            text = { Text("${child.name} 将变为独立服饰，现有图片、穿搭和付款记录都会保留；共用价格的小物解除后需要单独录入价格。") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        viewModel.detachChild(child.id).onSuccess { childToDetach = null }
                            .onFailure { childToDetach = null; showError = it.message }
                    }
                }) { Text("解除挂载") }
            },
            dismissButton = { TextButton(onClick = { childToDetach = null }) { Text("取消") } }
        )
    }

    val hasImage = uiState.item?.imageUrls?.isNotEmpty() == true

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (!hasImage) {
                GradientTopAppBar(
                    title = { Text("服饰详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            SkinIcon(IconKey.ArrowBack)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEdit(itemId) }) {
                            SkinIcon(IconKey.Edit)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (uiState.isLoading) {
            val shimmer = rememberShimmer(ShimmerBounds.Window)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (hasImage) 0.dp else padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding()
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Hero image skeleton
                ShimmerRect(
                    width = 400.dp,
                    height = heroHeight,
                    shape = heroShape,
                    modifier = Modifier.fillMaxWidth().shimmer(shimmer)
                )
                // Content section skeleton
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Name + status
                    ShimmerLine(widthFraction = 0.7f, height = 24.dp, modifier = Modifier.shimmer(shimmer))
                    ShimmerLine(widthFraction = 0.4f, height = 16.dp, modifier = Modifier.shimmer(shimmer))
                    // Basic info card
                    LolitaCard(modifier = Modifier.fillMaxWidth().shimmer(shimmer)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ShimmerLine(widthFraction = 0.3f, height = 18.dp)
                            ShimmerLine(widthFraction = 0.8f, height = 14.dp)
                            ShimmerLine(widthFraction = 0.6f, height = 14.dp)
                            ShimmerLine(widthFraction = 0.7f, height = 14.dp)
                            ShimmerLine(widthFraction = 0.5f, height = 14.dp)
                        }
                    }
                    // Price info card
                    LolitaCard(modifier = Modifier.fillMaxWidth().shimmer(shimmer)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ShimmerLine(widthFraction = 0.3f, height = 18.dp)
                            ShimmerLine(widthFraction = 0.9f, height = 14.dp)
                            ShimmerLine(widthFraction = 0.6f, height = 14.dp)
                        }
                    }
                }
            }
        } else {
            val item = uiState.item
            if (item == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("服饰不存在")
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (hasImage) 0.dp else padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                            .verticalScroll(rememberScrollState())
                    ) {
                    // Image section
                    if (item.imageUrls.isNotEmpty()) {
                        key(item.id, item.imageUrls.first()) {
                            var showFullScreen by remember { mutableStateOf(false) }
                            var selectedPage by remember { mutableIntStateOf(0) }
                            var currentPage by remember { mutableIntStateOf(0) }
                            ImageGalleryPager(
                                imageUrls = item.imageUrls,
                                onImageClick = { page -> selectedPage = page; showFullScreen = true },
                                modifier = Modifier.fillMaxWidth().height(heroHeight).testTag("item-detail-images"),
                                contentDescription = item.name,
                                sharedTransitionKey = "itemImage-${item.id}",
                                contentScale = ContentScale.Fit,
                                imageShape = heroShape,
                                imageBackgroundColor = MaterialTheme.colorScheme.surface,
                                onPageChanged = { currentPage = it }
                            )
                            val path = item.imageUrls.getOrNull(currentPage)
                            val isCover = path == item.imageUrls.first()
                            Row(Modifier.fillMaxWidth().padding(horizontal = skin.spacingLarge),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${currentPage + 1} / ${item.imageUrls.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { path?.let(viewModel::setImageCover) },
                                    enabled = path != null && !isCover && !uiState.isSettingCover && !uiState.isSaving) {
                                    Text(if (uiState.isSettingCover) "设置中…" else if (isCover) "当前封面" else "设为封面")
                                }
                            }
                            if (showFullScreen) FullScreenImageViewer(imageUrls = item.imageUrls,
                                initialPage = selectedPage, onDismiss = { showFullScreen = false })
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = item.name.firstOrNull()?.toString() ?: "?",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "暂无图片",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Content section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(skin.spacingLarge),
                        verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)
                    ) {
                        // Name and status
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.weight(1f).padding(end = skin.spacingMedium),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                StatusBadge(item.status)
                            }

                            if (item.status == ItemStatus.WISHED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                PriorityBadge(item.priority)
                            }
                        }

                        ItemOrderSummary(
                            item = item,
                            uiState = uiState,
                            onNavigateToPriceManage = onNavigateToPriceManage,
                            onNavigateToItem = onNavigateToItem,
                            onNavigateToParentPriceManage = onNavigateToParentPriceManage
                        )

                        LolitaSection(title = "基本信息") {
                            val brand = uiState.brands.find { it.id == item.brandId }
                            row {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(6.dp),
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        ) {}
                                        Text(
                                            text = "品牌",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BrandLogo(brand = brand, size = 20.dp)
                                        Text(
                                            text = if (brand?.isUnsetBrand == true) "未设置品牌" else brand?.name ?: "未知",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            row {
                                DetailRow(
                                    label = "类型",
                                    value = uiState.categories.find { it.id == item.categoryId }?.name ?: "未知"
                                )
                            }

                            item.coordinateId?.let { coordinateId ->
                                row {
                                    DetailRow(
                                        label = "所属套装",
                                        value = uiState.coordinates.find { it.id == coordinateId }?.name ?: "未知"
                                    )
                                }
                            }

                            item.colors.takeIf { it.isNotEmpty() }?.let { colors ->
                                if (colors.isNotEmpty()) {
                                    row {
                                        ColorChipsRow(label = "颜色", colors = colors)
                                    }
                                }
                            }
                            item.season?.let { season ->
                                if (season.isNotEmpty()) row { DetailRow(label = "季节", value = season.replace(",", "、")) }
                            }
                            item.style?.let { style ->
                                if (style.isNotEmpty()) row { DetailRow(label = "风格", value = style) }
                            }
                            item.source?.let { source ->
                                if (source.isNotEmpty()) row { DetailRow(label = "来源", value = source) }
                            }
                        }

                        TextButton(onClick = { coroutineScope.launch {
                            runCatching { viewModel.copyToNewDraft() }.onSuccess(onCopyToNew).onFailure { showError = it.message }
                        } }, modifier = Modifier.fillMaxWidth()) { Text("复制资料为新物品") }

                        WardrobeSpecificationDetails(
                            uiState.categories.firstOrNull { it.id == item.categoryId }?.categorySemantic ?: com.lolita.app.domain.model.CategorySemantic.UNCONFIRMED,
                            com.lolita.app.domain.model.WardrobeSpecifications.decode(item.specificationsJson), item.composition)

                        item.locationId?.takeIf { item.status.supportsStorage }?.let { roomId ->
                            LolitaSection("存放位置") {
                                row { TextButton(onClick = { onNavigateToLocation(roomId) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(com.lolita.app.domain.model.StorageTree.pathFor(roomId, item.storageNodeId, uiState.locations, uiState.storageNodes))
                                } }
                            }
                        }

                        item.parentItemId?.let { parentId ->
                            val parent = uiState.allItems.find { it.id == parentId }
                            LolitaSection(title = "所属主件") {
                                row {
                                    TextButton(onClick = { onNavigateToItem(parentId) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(parent?.name ?: "查看主件")
                                        Spacer(Modifier.width(8.dp))
                                        SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(16.dp))
                                    }
                                    Text(if (item.includedInParentPrice) "已包含在主件价格中，收货状态随主件"
                                        else "独立计价，收货状态按本件付款计算",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (item.parentItemId == null) {
                            if (uiState.children.isEmpty()) {
                                LolitaCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.COMPACT) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)
                                    ) {
                                        Text("小物", modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { onCreateChild(item.id) }) {
                                            SkinIcon(IconKey.Add, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(skin.spacingSmall))
                                            Text("新增小物")
                                        }
                                        TextButton(onClick = { showAttachPicker = true }) { Text("挂入已有") }
                                    }
                                }
                            } else {
                                LolitaSection(title = "所属小物", action = {
                                    TextButton(onClick = { onCreateChild(item.id) }) {
                                        SkinIcon(IconKey.Add, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("新增小物")
                                    }
                                }) {
                                    uiState.children.forEach { child ->
                                        row {
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Row(modifier = Modifier.weight(1f).skinClickable { onNavigateToItem(child.id) }
                                                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    LolitaShimmerImage(model = child.imageUrls.firstOrNull(), contentDescription = child.name,
                                                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                                                        contentScale = ContentScale.Crop, placeholderInitial = child.name.firstOrNull()?.toString())
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(child.name, style = MaterialTheme.typography.bodyMedium)
                                                        Text(if (child.includedInParentPrice) "包含在主件价格中" else "独立计价",
                                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(itemStatusLabel(child.status), style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                TextButton(onClick = { childToDetach = child }) { Text("解除") }
                                            }
                                        }
                                    }
                                    row {
                                        OutlinedButton(onClick = { showAttachPicker = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text("挂入已有小物")
                                        }
                                    }
                                }
                            }
                        }

                        if (item.description.isNotEmpty()) {
                            LolitaCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    SectionHeader(title = "描述")
                                    Spacer(Modifier.height(8.dp))
                                    Text(text = item.description, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        if (!item.size.isNullOrEmpty() || item.sizeChartImageUrl != null || item.sizeChartImageUrls.isNotEmpty()) {
                            LolitaCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SectionHeader(title = "尺码信息")
                                    if (!item.size.isNullOrEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        DetailRow(label = "尺码", value = item.size!!)
                                    }
                                    val charts = (listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls).distinct()
                                    if (charts.isNotEmpty()) {
                                        SizeChartGallery(charts)
                                    }
                                }
                            }
                        }

                        LolitaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader(
                                    title = "价格与付款",
                                    action = {
                                        OutlinedButton(
                                            onClick = onNavigateToPriceManage,
                                            enabled = !item.includedInParentPrice || uiState.legacyPriceCount > 0,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (item.includedInParentPrice) "历史价格" else "价格与付款", style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.width(4.dp))
                                            SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))

                                if (item.includedInParentPrice) {
                                    Text("已包含在主件价格中，不重复计价", style = MaterialTheme.typography.bodyMedium)
                                    if (uiState.legacyPriceCount > 0) {
                                        Text("原有 ${uiState.legacyPriceCount} 笔价格与付款记录已保留，可查看历史价格",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { item.parentItemId?.let(onNavigateToItem) }) { Text("查看主件价格与付款") }
                                } else if (uiState.pricesWithPayments.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                if (uiState.legacyPriceCount > 0) "历史价格待核对，请在价格与付款中确定唯一订单"
                                                else "尚未录入价格，点击价格与付款设置订单",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    val dateFormat = remember { java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()) }
                                    uiState.pricesWithPayments.forEach { priceWithPayments ->
                                        val price = priceWithPayments.price
                                        val payments = priceWithPayments.payments
                                        val paidAmount = OrderAccounting.sum(payments.filter { it.isPaid }.map { it.amount })

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        when (price.type) {
                                                            PriceType.FULL -> "全价"
                                                            PriceType.DEPOSIT_BALANCE -> "定金+尾款"
                                                            PriceType.MULTI_STAGE -> "多段付款"
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "¥${String.format("%.2f", price.totalPrice)}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                if (price.type == PriceType.DEPOSIT_BALANCE) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "定金 ¥${String.format("%.2f", price.deposit ?: 0.0)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            "尾款 ¥${String.format("%.2f", price.balance ?: 0.0)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                if (priceWithPayments.binding?.purchaseRecordStatus != com.lolita.app.data.local.entity.PurchaseRecordStatus.TRACKED) {
                                                    Text(
                                                        if (priceWithPayments.binding?.purchaseRecordStatus == com.lolita.app.data.local.entity.PurchaseRecordStatus.HISTORICAL_OWNED)
                                                            "历史已购，付款记录未补全" else "历史付款资料待核对",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (paidAmount > 0) Text("已录实付 ¥${String.format("%.2f", paidAmount)}",
                                                        style = MaterialTheme.typography.bodySmall)
                                                } else run {
                                                    val unpaidAmount = OrderAccounting.remaining(price.totalPrice, paidAmount)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "已付 ¥${String.format("%.2f", paidAmount)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        if (unpaidAmount > 0) {
                                                            Text(
                                                                "待付 ¥${String.format("%.2f", unpaidAmount)}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }

                                                payments.filter { it.isPaid }
                                                    .minByOrNull { it.paidDate ?: Long.MAX_VALUE }
                                                    ?.paidDate?.let { date ->
                                                        Text(
                                                            "付款日期: ${dateFormat.format(java.util.Date(date))}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                if (uiState.legacyPriceCount > 0 && uiState.pricesWithPayments.isNotEmpty()) {
                                    Text("另有 ${uiState.legacyPriceCount} 笔历史价格已保留，可在价格与付款中核对",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                MannequinDetailEntry(item, uiState.categories.firstOrNull { it.id == item.categoryId }, onNavigateToMannequin)

                                if (item.status == ItemStatus.OWNED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        OutlinedButton(
                                            onClick = { onNavigateToRecommendation(item.id) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            SkinIcon(IconKey.Star, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("推荐搭配")
                                        }
                                    }
                                }
                                if (item.status.supportsVirtualTryOn) {
                                    OutlinedButton(
                                        onClick = { onNavigateToTryOn(item.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SkinIcon(IconKey.Outfit, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(skin.spacingMedium))
                                        Text("虚拟试衣")
                                    }
                                }
                            }
                        }

                        ItemDisplaySection(item, uiState.categories.firstOrNull { it.id == item.categoryId }, currentMaterial,
                            onNavigateToMaterial, onViewMaterial)

                        LolitaSection(title = "记录信息") {
                            row {
                                DetailRow(
                                    label = "创建时间",
                                    value = formatDate(item.createdAt)
                                )
                            }
                            row {
                                DetailRow(
                                    label = "更新时间",
                                    value = formatDate(item.updatedAt)
                                )
                            }
                        }
                    }
                }

                    // Floating top bar overlay for immersive image
                    if (hasImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = 160f
                                    )
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBack) {
                                    SkinIcon(IconKey.ArrowBack, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { onEdit(itemId) }) {
                                    SkinIcon(IconKey.Edit, tint = Color.White)
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    SkinIcon(IconKey.Delete, tint = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemOrderSummary(
    item: Item,
    uiState: ItemEditUiState,
    onNavigateToPriceManage: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    onNavigateToParentPriceManage: (Long) -> Unit = onNavigateToItem
) {
    val skin = LolitaSkin.current
    val currentOrder = uiState.pricesWithPayments.singleOrNull()
    val sharedParentId = item.parentItemId.takeIf { item.includedInParentPrice }
    val orderStatus = currentOrder?.binding?.purchaseRecordStatus ?: currentOrder?.price?.purchaseRecordStatus
    val hasUnknownPaymentDates = currentOrder?.payments?.any { it.isPaid && it.paidDate == null } == true
    // The Room projection is authoritative, including inherited parent data. The fallback also
    // supports an isolated summary before the full detail subscription supplies its projection.
    val fallbackNeedsReview = when {
        sharedParentId != null -> item.status == ItemStatus.PENDING_REVIEW
        currentOrder != null -> item.status == ItemStatus.PENDING_REVIEW ||
            orderStatus != PurchaseRecordStatus.TRACKED || hasUnknownPaymentDates
        else -> item.status != ItemStatus.WISHED
    }
    val projectedReview = uiState.purchaseReviews.firstOrNull { it.item.id == item.id }
    val purchaseReview = if (uiState.purchaseReviewsLoaded) projectedReview else {
        projectedReview ?: if (fallbackNeedsReview) ItemPurchaseReview(item, sharedParentId ?: item.id,
            currentOrder?.price?.id, orderStatus, hasUnknownPaymentDates) else null
    }

    LolitaCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.COMPACT,
        onClick = {
            if (sharedParentId != null) onNavigateToItem(sharedParentId)
            else onNavigateToPriceManage()
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            SectionHeader(title = "价格与付款", action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sharedParentId != null) "查看主件" else if (currentOrder == null && uiState.legacyPriceCount == 0) "录入价格" else "查看",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(skin.spacingSmall))
                    SkinIcon(IconKey.ArrowForward, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            })
            when {
                item.includedInParentPrice -> Text(
                    "已包含在主件价格中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                currentOrder != null -> {
                    val paidAmount = OrderAccounting.sum(currentOrder.payments.filter { it.isPaid }.map { it.amount })
                    val remainingAmount = OrderAccounting.remaining(currentOrder.price.totalPrice, paidAmount)
                    Text("¥${String.format("%.2f", currentOrder.price.totalPrice)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    if (currentOrder.binding?.purchaseRecordStatus != com.lolita.app.data.local.entity.PurchaseRecordStatus.TRACKED) {
                        Text(
                            if (currentOrder.binding?.purchaseRecordStatus == com.lolita.app.data.local.entity.PurchaseRecordStatus.HISTORICAL_OWNED)
                                "历史已购，付款记录未补全" else "历史付款资料待核对",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (paidAmount > 0) Text("已录实付 ¥${String.format("%.2f", paidAmount)}",
                            style = MaterialTheme.typography.bodySmall)
                    } else FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(skin.spacingLarge),
                        verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)
                    ) {
                        Text("实付 ¥${String.format("%.2f", paidAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("待付 ¥${String.format("%.2f", remainingAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remainingAmount > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> Text(
                    if (uiState.legacyPriceCount > 0) "历史价格待核对" else "尚未录入价格",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (purchaseReview != null) {
                if (currentOrder == null && uiState.legacyPriceCount > 0 && sharedParentId == null) Text(
                    "先选择一笔历史订单，再核对购买资料。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text((if (sharedParentId != null) "共用价格的小物随主件补全资料。" else "") + purchaseReview.reasonText,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        if (sharedParentId != null) onNavigateToParentPriceManage(sharedParentId)
                        else onNavigateToPriceManage()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reviewPurchaseStatusButton")
                ) { Text(if (sharedParentId != null) "核对主件购买资料" else "核对购买资料") }
            }
        }
    }
}

/**
 * Status Badge Component
 */
@Composable
private fun StatusBadge(status: ItemStatus) {
    Surface(
        color = when (status) {
            ItemStatus.OWNED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            ItemStatus.WISHED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ItemStatus.PENDING_BALANCE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            ItemStatus.PENDING_PAYMENT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            ItemStatus.PENDING_REVIEW -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (status) {
                ItemStatus.OWNED -> "已拥有"
                ItemStatus.WISHED -> "愿望单"
                ItemStatus.PENDING_BALANCE -> "待补尾款"
                ItemStatus.PENDING_PAYMENT -> "待付款"
                ItemStatus.PENDING_REVIEW -> "待核对"
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Priority Badge Component
 */
@Composable
private fun PriorityBadge(priority: ItemPriority) {
    val priorityColor = when (priority) {
        ItemPriority.HIGH -> MaterialTheme.colorScheme.primary
        ItemPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        ItemPriority.LOW -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = priorityColor.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = when (priority) {
                ItemPriority.HIGH -> "高优先级"
                ItemPriority.MEDIUM -> "中优先级"
                ItemPriority.LOW -> "低优先级"
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Detail Row Component
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) {}
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChipsRow(label: String, colors: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(80.dp)
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) {}
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            colors.forEach { colorName ->
                val hex = findColorHex(colorName)
                val chipColor = if (hex != null) Color(hex) else Color.Gray
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(chipColor)
                        )
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format timestamp to readable date
 */
private fun formatDate(timestamp: Long): String {
    val javaDate = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault())
    return format.format(javaDate)
}

@Composable
private fun SizeChartGallery(images: List<String>) {
    var preview by remember { mutableStateOf<Int?>(null) }
    Text("共 ${images.size} 张，点击图片可放大查看。", style = MaterialTheme.typography.bodySmall)
    ImageGalleryPager(imageUrls = images, onImageClick = { preview = it },
        modifier = Modifier.fillMaxWidth().height(240.dp), contentDescription = "尺码表",
        contentScale = ContentScale.Fit)
    preview?.let { page -> FullScreenImageViewer(images, page) { preview = null } }
}

@Composable
internal fun MannequinDetailEntry(item: Item, category: com.lolita.app.data.local.entity.Category?, onNavigate: (Long) -> Unit) {
    val subject = mannequinSubject(category, confirmedSemantics = true)
    if (subject?.presentation != MannequinPresentation.WIG_HEAD || item.productReferenceImages.isEmpty()) return
    val skin = LolitaSkin.current
    OutlinedButton(onClick = { onNavigate(item.id) }, modifier = Modifier.fillMaxWidth()) {
        SkinIcon(IconKey.Outfit, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(skin.spacingMedium))
        Text("生成假发头模图")
    }
}

@Composable
internal fun ItemDisplaySection(item: Item, category: com.lolita.app.data.local.entity.Category?,
    material: com.lolita.app.data.local.entity.ItemTryOnMaterial?, onGenerate: (Long) -> Unit, onView: (Long) -> Unit) {
    if (category?.categorySemantic?.supportsMaterialPreparation != true || !item.status.supportsVirtualTryOn) return
    LolitaSection(title = "展示图") { row {
        material?.let {
            com.lolita.app.ui.screen.material.MaterialPhoto(it.imagePath, "查看已确认展示图", onClick = { onView(item.id) }, height = 150, checker = true)
            Text("已确认 · 点击查看，可设为封面或用于试衣", style = MaterialTheme.typography.bodySmall)
        } ?: Text("将商品照片整理成透明单品图，确认后可作封面，也可用于试衣。", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onGenerate(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("生成展示图") }
    } }
}
