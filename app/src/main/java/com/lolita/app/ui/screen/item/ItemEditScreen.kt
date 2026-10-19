package com.lolita.app.ui.screen.item

import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import com.lolita.app.ui.theme.skin.component.skinClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.StorageLocationPicker
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.component.MultiImageEditor
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.BrandLogo
import com.lolita.app.ui.screen.common.ColorSelector
import com.lolita.app.ui.screen.common.SizeSelector
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.ShimmerRect
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

/**
 * Item Edit Screen - 添加/编辑服饰界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    itemId: Long?,
    defaultStatus: String = "",
    importedDraftJson: String? = null,
    defaultParentItemId: Long? = null,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    draftId: String? = null,
    viewModel: ItemEditViewModel = viewModel(),
    defaultLocationId: Long? = null,
    defaultStorageNodeId: Long? = null,
    defaultFamily: com.lolita.app.domain.model.WardrobeFamily? = null,
    onConfigureApi: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val skin = LolitaSkin.current
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    var showError by remember { mutableStateOf<String?>(null) }
    var isLeaving by remember { mutableStateOf(false) }
    val exitEditor: () -> Unit = {
        if (!viewModel.uiState.value.isSaving && !isLeaving) {
            isLeaving = true
            coroutineScope.launch {
                try { viewModel.keepDraft(); onBack() }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    isLeaving = false; showError = failure.message
                }
            }
        }
    }

    val canEdit = !uiState.isLoading && !uiState.isSaving && uiState.loadError == null && !isLeaving
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showConflictDiscardDialog by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    var supplementaryExpanded by rememberSaveable(itemId, draftId) { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var photoPreview by rememberSaveable { mutableStateOf<Int?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) coroutineScope.launch {
            try {
                val path = ImageFileHelper.copyToInternalStorage(context, uri)
                val latest = viewModel.uiState.value
                if (isLeaving || latest.isSaving || latest.isLoading || latest.loadError != null) {
                    ImageFileHelper.cleanupUnreferencedImages(listOf(path))
                } else viewModel.addImage(path)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                showError = failure.message ?: "照片未能添加，请重试"
            }
        }
    }

    androidx.activity.compose.BackHandler {
        if (!uiState.isSaving) exitEditor()
    }

    val importProgress = remember(importedDraftJson) { importedDraftJson?.let { json ->
        runCatching { com.google.gson.Gson().fromJson(json, com.lolita.app.data.file.TaobaoProductDraft::class.java) }.getOrNull()
    } }

    // Load item data if editing
    LaunchedEffect(itemId, defaultStatus, defaultParentItemId, importedDraftJson, draftId, defaultLocationId, defaultStorageNodeId, defaultFamily) {
        viewModel.selectDraft(draftId)
        viewModel.loadItem(
            itemId = itemId ?: 0L,
            defaultStatus = defaultStatus,
            importedDraftJson = importedDraftJson,
            defaultParentItemId = defaultParentItemId,
            defaultLocationId = defaultLocationId, defaultStorageNodeId = defaultStorageNodeId, defaultFamily = defaultFamily
        )
    }

    LaunchedEffect(uiState.recoveredImport) {
        if (uiState.recoveredImport) onSaveSuccess()
    }

    LaunchedEffect(uiState.draftError) { uiState.draftError?.let { showError = it } }

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

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这件服饰吗？相关的价格和付款记录也会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        coroutineScope.launch {
                            viewModel.deleteItem()
                                .onSuccess { onSaveSuccess() }
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
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Ordinary back keeps the durable draft. Discarding remains a separate explicit action.
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃这份草稿？") },
            text = { Text("本次未保存的编辑将被放弃，已保存的物品资料保持不变。") },
            confirmButton = {
                TextButton(onClick = {
                    isLeaving = true
                    coroutineScope.launch {
                        runCatching { viewModel.discardDraft() }
                            .onSuccess { showDiscardDialog = false; onBack() }
                            .onFailure { isLeaving = false; showError = it.message }
                    }
                }, enabled = !uiState.isSaving && !isLeaving) { Text("放弃草稿") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            }
        )
    }

    if (showColors && canEdit) AlertDialog(onDismissRequest = { showColors = false },
        title = { Text("选择颜色") }, text = {
            val colorScroll = rememberScrollState()
            ObserveSkinScroll(colorScroll)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(colorScroll)) {
                ColorSelector(uiState.colors, viewModel::updateColors, enabled = canEdit)
            }
        }, confirmButton = { TextButton(onClick = { showColors = false }) { Text("完成") } })
    photoPreview?.takeIf { it in uiState.imageUrls.indices }?.let { page ->
        com.lolita.app.ui.component.FullScreenImageViewer(uiState.imageUrls, page) { photoPreview = null }
    }

    uiState.loadError?.takeUnless { showConflictDiscardDialog }?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if(uiState.hasDraftConflict) "草稿与最新资料不同" else "读取失败") },
            text = { Text(message) },
            confirmButton = { if(uiState.hasDraftConflict) {
                TextButton(onClick = exitEditor) { Text("保留草稿并返回") }
            } else TextButton(onClick = { viewModel.loadItem(itemId ?: 0L, defaultStatus, defaultParentItemId, importedDraftJson,
                defaultLocationId, defaultStorageNodeId, defaultFamily) }) { Text("重试") } },
            dismissButton = { if(uiState.hasDraftConflict) {
                TextButton(onClick = { showConflictDiscardDialog=true }) { Text("放弃旧草稿并重载") }
            } else TextButton(onClick = exitEditor) { Text("返回") } }
        )
    }
    if(showConflictDiscardDialog) AlertDialog(
        onDismissRequest = { showConflictDiscardDialog=false },
        title = { Text("放弃旧草稿？") },
        text = { Text("旧草稿的未保存编辑将被放弃，重新读取已保存物品的最新照片、位置与资料。") },
        confirmButton = { TextButton(onClick = { coroutineScope.launch {
            runCatching { viewModel.discardConflictingDraftAndReload() }.onFailure { showError=it.message }
            showConflictDiscardDialog=false
        } }) { Text("放弃并重载") } },
        dismissButton = { TextButton(onClick = { showConflictDiscardDialog=false }) { Text("继续保留草稿") } }
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(
                title = { Text(if (itemId == null) "添加物品" else "编辑物品") },
                navigationIcon = {
                    IconButton(modifier = Modifier.semantics { contentDescription = "返回" },
                        enabled = !uiState.isSaving, onClick = exitEditor) { SkinIcon(IconKey.ArrowBack) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { moreActions = true }, enabled = canEdit,
                            modifier = Modifier.semantics { contentDescription = "更多操作" }) {
                            SkinIcon(IconKey.MoreVert)
                        }
                        DropdownMenu(expanded = moreActions && canEdit, onDismissRequest = { moreActions = false }) {
                            DropdownMenuItem(text = { Text("放弃草稿") }, onClick = {
                                moreActions = false; showDiscardDialog = true
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (uiState.isLoading) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(skin.spacingLarge),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)) {
                    listOf(44.dp, 56.dp, 48.dp, 172.dp).forEach { height ->
                        ShimmerRect(320.dp, height, RoundedCornerShape(skin.cornerRadiusMedium), Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(skin.spacingLarge),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)
                ) {
                    if (importProgress != null && importProgress.batchTotal > 1) {
                        LolitaSection("拆分导入") {
                            row { Text("第 ${importProgress.batchIndex} / ${importProgress.batchTotal} 件 · 保存后继续下一件") }
                            row { Text("请核对本件的类型和尺码，商品详情图已保留。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (uiState.imageUrls.isEmpty()) {
                        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
                            SkinIcon(IconKey.AddPhoto, Modifier.size(20.dp))
                            Spacer(Modifier.width(skin.spacingMedium))
                            Text("添加照片（可选）")
                        }
                    } else {
                        MultiImageEditor(imageUrls = uiState.imageUrls, maxImages = 5,
                            onAddImage = viewModel::addImage, onRemoveImage = viewModel::removeImage,
                            onImageClick = { photoPreview = it }, enabled = canEdit)
                    }
                    com.lolita.app.ui.screen.item.recognition.ItemRecognitionPanel(viewModel, onConfigureApi = onConfigureApi)
                    OutlinedTextField(
                        value = uiState.name, onValueChange = viewModel::updateName, enabled = canEdit,
                        label = { Text("物品名称 *") }, placeholder = { Text("例如：草莓花园 JSK") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = hasAttemptedSave && uiState.name.isBlank()
                    )
                    CategorySelector(enabled = canEdit, selectedCategoryId = uiState.categoryId,
                        categories = uiState.categories, onCategorySelected = viewModel::updateCategory,
                        isError = hasAttemptedSave && uiState.categoryId == 0L, preferredFamily = uiState.preferredFamily)
                    Text("名称和类型填好即可保存，其他资料可稍后补充。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LolitaSection("常用资料（选填）") {
                        row { SizeSelector(uiState.size, viewModel::updateSize, enabled = canEdit, modifier = Modifier.fillMaxWidth()) }
                        row {
                            OutlinedButton(onClick = { showColors = true }, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
                                Text("颜色：${uiState.colors.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "未设置"}")
                            }
                        }
                        if (uiState.status.supportsStorage) row {
                            StorageLocationPicker(selectedLocationId = uiState.locationId, selectedNodeId = uiState.storageNodeId,
                                locations = uiState.locations, nodes = uiState.storageNodes,
                                onLocationSelected = viewModel::updateStorage, enabled = canEdit)
                        }
                    }
                    WardrobeSpecificationEditor(
                        semantic = uiState.categories.firstOrNull { it.id == uiState.categoryId }?.categorySemantic ?: CategorySemantic.UNCONFIRMED,
                        specifications = uiState.specifications, composition = uiState.composition, enabled = canEdit,
                        onSpecifications = viewModel::updateSpecifications, onComposition = viewModel::updateComposition)

                    // Existing financial relationships and warnings remain visible even while the form is folded.
                    val parent = uiState.allItems.firstOrNull { it.id == uiState.parentItemId }
                    if (uiState.parentItemId != null || uiState.children.isNotEmpty()) {
                        LolitaSection("主件与小物") {
                            if (uiState.parentItemId != null) row {
                                Text("所属主件：${parent?.name ?: "正在读取"}")
                                Text(if (uiState.includedInParentPrice) "包含在主件价格中，不重复计价，收货状态随主件"
                                    else "保留独立价格与付款，收货状态按本件付款计算",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!uiState.includedInParentPrice && uiState.pricesWithPayments.isNotEmpty()) {
                                    Text("已有价格记录，请先在价格与付款中核对后再改为共用价格",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (uiState.children.isNotEmpty()) row {
                                Text("已有 ${uiState.children.size} 件所属小物，解除挂载后才能挂到其他主件下面")
                            }
                        }
                    }
                    if (uiState.includedInParentPrice || uiState.pricesWithPayments.isNotEmpty() || uiState.legacyPriceCount > 0) {
                        LolitaSection("收货状态") {
                            row {
                                Text(itemStatusLabel(uiState.status))
                                Text(if (uiState.includedInParentPrice) "包含在主件价格中，随主件付清后变为已拥有"
                                    else if (uiState.legacyPriceCount > 0 && uiState.pricesWithPayments.isEmpty()) "历史价格待核对，请在价格与付款中确定唯一订单"
                                    else "按实际付款自动更新，付清后即为已拥有",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val brand = uiState.brands.firstOrNull { it.id == uiState.brandId }?.takeUnless { it.isUnsetBrand }
                    val extraSummary = buildList {
                        brand?.let { add("品牌：${it.name}") }
                        if (uiState.description.isNotBlank()) add("备注已填写")
                        uiState.style?.takeIf { it.isNotBlank() }?.let { add("风格：$it") }
                        if (uiState.seasons.isNotEmpty()) add(uiState.seasons.joinToString("、"))
                        uiState.source?.takeIf { it.isNotBlank() }?.let { add("来源：$it") }
                        uiState.coordinates.firstOrNull { it.id == uiState.coordinateId }?.let { add("套装：${it.name}") }
                        if (uiState.parentItemId != null) add("主件挂载已设置")
                        val chartCount = listOfNotNull(uiState.sizeChartImageUrl).size + uiState.sizeChartImageUrls.size
                        if (chartCount > 0) add("资料图 $chartCount 张")
                        add(itemStatusLabel(uiState.status))
                        if (uiState.status == ItemStatus.WISHED) add("愿望优先级：${when (uiState.priority) {
                            ItemPriority.HIGH -> "高"; ItemPriority.MEDIUM -> "中"; ItemPriority.LOW -> "低"
                        }}")
                    }.joinToString(" · ")
                    LolitaSection("补充资料", action = {
                        TextButton(onClick = { supplementaryExpanded = !supplementaryExpanded }, enabled = canEdit,
                            modifier = Modifier.semantics { contentDescription = if (supplementaryExpanded) "收起补充资料" else "展开补充资料" }) {
                            Text(if (supplementaryExpanded) "收起" else "展开")
                        }
                    }) {
                        row { Text(extraSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (supplementaryExpanded) row {
                            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingLarge)) {
                                OutlinedTextField(uiState.description, viewModel::updateDescription, enabled = canEdit,
                                    label = { Text("描述与备注（选填）") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                                BrandSelector(enabled = canEdit, selectedBrandId = uiState.brandId, brands = uiState.brands,
                                    onBrandSelected = viewModel::updateBrand)
                                if (!uiState.includedInParentPrice && uiState.pricesWithPayments.isEmpty() && uiState.legacyPriceCount == 0) {
                                    StatusSelector(canEdit, uiState.status, viewModel::updateStatus)
                                }
                                if (uiState.status == ItemStatus.WISHED) PrioritySelector(canEdit, uiState.priority, viewModel::updatePriority)
                                SeasonSelector(canEdit, uiState.seasons, uiState.seasonOptions, viewModel::toggleSeason)
                                StyleSelector(canEdit, uiState.style, uiState.styleOptions, viewModel::updateStyle)
                                SourceSelector(canEdit, uiState.source, uiState.sourceOptions, viewModel::updateSource)
                                CoordinateSelector(canEdit, uiState.coordinateId, uiState.coordinates, viewModel::updateCoordinate)
                                ParentItemSelector(canEdit, uiState.parentItemId, uiState.item?.id, uiState.allItems,
                                    uiState.includedInParentPrice, uiState.pricesWithPayments.isEmpty(), uiState.children.isNotEmpty(),
                                    viewModel::updateParent, viewModel::updateIncludedInParentPrice)
                                SizeChartImageSection(canEdit, listOfNotNull(uiState.sizeChartImageUrl) + uiState.sizeChartImageUrls,
                                    viewModel::addSizeChartImage, viewModel::removeSizeChartImage)
                                if (itemId != null && uiState.item != null) {
                                    OutlinedButton(onClick = { showDeleteConfirm = true }, enabled = canEdit, modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                        SkinIcon(IconKey.Delete); Spacer(Modifier.width(skin.spacingMedium)); Text("删除服饰")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(modifier = Modifier.navigationBarsPadding(), color = MaterialTheme.colorScheme.surface) {
                Button(onClick = {
                    hasAttemptedSave = true
                    coroutineScope.launch {
                        viewModel.saveItem().onSuccess { onSaveSuccess() }.onFailure { showError = it.message }
                    }
                }, enabled = canEdit && !uiState.isSettingCover && uiState.name.isNotBlank() && uiState.categoryId != 0L,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium)
                        .heightIn(min = 52.dp).semantics { contentDescription = "保存物品" },
                    shape = RoundedCornerShape(skin.cornerRadiusMedium)) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else SkinIcon(IconKey.Save, Modifier.size(20.dp))
                    Spacer(Modifier.width(skin.spacingMedium))
                    Text(if (uiState.isSaving) "保存中…" else "保存物品")
                }
            }
        }
    }
}

/**
 * Brand Selector Component - with search dialog
 */
@Composable
private fun BrandSelector(
    enabled: Boolean,
    selectedBrandId: Long,
    brands: List<com.lolita.app.data.local.entity.Brand>,
    onBrandSelected: (Long) -> Unit,
    isError: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .skinClickable(enabled = enabled) { showDialog = true }
    ) {
        OutlinedTextField(
            value = brands.find { it.id == selectedBrandId }?.name ?: "未设置",
            onValueChange = {},
            readOnly = true,
            label = { Text("品牌（可不设置）") },
            trailingIcon = { SkinIcon(IconKey.Search) },
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            supportingText = if (isError) {
                { Text("请选择品牌") }
            } else null,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog && enabled) {
        var searchQuery by remember { mutableStateOf("") }
        val filtered = remember(searchQuery, brands) {
            brands.filterNot { it.isUnsetBrand }.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择品牌") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索品牌...") },
                        leadingIcon = { SkinIcon(IconKey.Search) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onBrandSelected(0L); showDialog = false }) { Text("未设置品牌") }
                    val brandsScroll = androidx.compose.foundation.lazy.rememberLazyListState()
                    ObserveSkinScroll(brandsScroll)
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp), state = brandsScroll) {
                        items(filtered, key = { it.id }) { brand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .skinClickable {
                                        onBrandSelected(brand.id)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrandLogo(brand = brand, size = 24.dp)
                                Text(
                                    text = brand.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (brand.id == selectedBrandId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * Category Selector Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    enabled: Boolean,
    selectedCategoryId: Long,
    categories: List<com.lolita.app.data.local.entity.Category>,
    onCategorySelected: (Long) -> Unit,
    isError: Boolean = false,
    preferredFamily: com.lolita.app.domain.model.WardrobeFamily? = null
) {
    WardrobeCategorySelector(selectedCategoryId, categories, onCategorySelected, enabled, isError, preferredFamily)
}

/**
 * Coordinate Selector Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinateSelector(
    enabled: Boolean,
    selectedCoordinateId: Long?,
    coordinates: List<com.lolita.app.data.local.entity.Coordinate>,
    onCoordinateSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedCoordinateId == null) {
        "无"
    } else {
        coordinates.find { it.id == selectedCoordinateId }?.name ?: "无"
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("所属套装 (可选)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                onClick = {
                    onCoordinateSelected(null)
                    expanded = false
                }
            )
            coordinates.forEach { coordinate ->
                DropdownMenuItem(
                    text = { Text(coordinate.name) },
                    onClick = {
                        onCoordinateSelected(coordinate.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Status Selector Component
 */
@Composable
private fun StatusSelector(
    enabled: Boolean,
    selectedStatus: ItemStatus,
    onStatusSelected: (ItemStatus) -> Unit
) {
    Column {
        Text(
            text = "收货状态",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ItemStatus.OWNED, ItemStatus.WISHED).forEach { status ->
                FilterChip(
                    enabled = enabled,
                    selected = selectedStatus == status,
                    onClick = { onStatusSelected(status) },
                    label = {
                        Text(
                            when (status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "愿望单"
                                ItemStatus.PENDING_BALANCE -> "待补尾款"
                                ItemStatus.PENDING_PAYMENT -> "待付款"
                                ItemStatus.PENDING_REVIEW -> "待核对"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Priority Selector Component
 */
@Composable
private fun PrioritySelector(
    enabled: Boolean,
    selectedPriority: ItemPriority,
    onPrioritySelected: (ItemPriority) -> Unit
) {
    Column {
        Text(
            text = "愿望单优先级",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemPriority.entries.forEach { priority ->
                FilterChip(
                    enabled = enabled,
                    selected = selectedPriority == priority,
                    onClick = { onPrioritySelected(priority) },
                    label = {
                        Text(
                            when (priority) {
                                ItemPriority.HIGH -> "高"
                                ItemPriority.MEDIUM -> "中"
                                ItemPriority.LOW -> "低"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Season Selector Component - loads from database
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonSelector(
    enabled: Boolean,
    selectedSeasons: List<String>,
    seasonOptions: List<String>,
    onSeasonToggled: (String) -> Unit
) {
    Column {
        Text(
            text = "季节 (可多选)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            seasonOptions.forEach { season ->
                FilterChip(
                    enabled = enabled,
                    selected = season in selectedSeasons,
                    onClick = { onSeasonToggled(season) },
                    label = { Text(season) }
                )
            }
        }
    }
}

/**
 * Style Selector Component - loads from database
 */
@Composable
private fun StyleSelector(
    enabled: Boolean,
    selectedStyle: String?,
    styleOptions: List<String>,
    onStyleSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = "风格 (可选)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Split into rows of 3
        styleOptions.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { style ->
                    FilterChip(
                    enabled = enabled,
                        selected = selectedStyle == style,
                        onClick = {
                            onStyleSelected(if (selectedStyle == style) null else style)
                        },
                        label = { Text(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if row has fewer than 3 items
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Source Selector Component - loads from database
 */
@Composable
private fun SourceSelector(
    enabled: Boolean,
    selectedSource: String?,
    sourceOptions: List<String>,
    onSourceSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = "来源 (可选)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Split into rows of 3
        sourceOptions.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { source ->
                    FilterChip(
                    enabled = enabled,
                        selected = selectedSource == source,
                        onClick = {
                            onSourceSelected(if (selectedSource == source) null else source)
                        },
                        label = { Text(source) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Size Chart Image Section Component
 */
@Composable
private fun SizeChartImageSection(
    enabled: Boolean,
    imageUrls: List<String>,
    onAddImage: (String) -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    LolitaSection("尺码表图片（可选）") {
        row { Text("共 ${imageUrls.size} 张，点击图片可放大查看。") }
        row {
            MultiImageEditor(enabled = enabled, imageUrls = imageUrls, maxImages = 50, onAddImage = onAddImage,
                onRemoveImage = onRemoveImage, contentScale = ContentScale.Fit,
                onImageClick = { preview = it })
        }
    }
    preview?.let { page ->
        com.lolita.app.ui.component.FullScreenImageViewer(imageUrls, page) { preview = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelector(
    enabled: Boolean,
    selectedLocationId: Long?,
    locations: List<com.lolita.app.data.local.entity.Location>,
    onLocationSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedLocationId == null) {
        "无"
    } else {
        locations.find { it.id == selectedLocationId }?.name ?: "无"
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("存放位置 (可选)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                onClick = { onLocationSelected(null); expanded = false }
            )
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    onClick = { onLocationSelected(location.id); expanded = false }
                )
            }
        }
    }
}


@Composable
private fun ParentItemSelector(
    enabled: Boolean,
    selectedParentId: Long?,
    currentItemId: Long?,
    items: List<Item>,
    includedInParentPrice: Boolean,
    canSharePrice: Boolean,
    hasChildren: Boolean,
    onParentSelected: (Long?) -> Unit,
    onIncludedChanged: (Boolean) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val parent = items.find { it.id == selectedParentId }
    LolitaSection(title = "所属主件与价格") {
        row {
            OutlinedButton(onClick = { showPicker = true }, enabled = enabled && !hasChildren, modifier = Modifier.fillMaxWidth()) {
                SkinIcon(IconKey.Search, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(parent?.name ?: "独立服饰（选择主件可挂为小物）")
            }
            if (hasChildren) {
                Text("已有所属小物，解除挂载后才能挂到其他主件下面", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedParentId != null) {
            row {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includedInParentPrice, onCheckedChange = onIncludedChanged,
                        enabled = enabled && (canSharePrice || includedInParentPrice))
                    Column(Modifier.weight(1f)) {
                        Text("包含在主件价格中")
                        Text(if (includedInParentPrice) "不单独计价，收货状态随主件"
                            else "独立价格与付款，收货状态按自己的付款计算",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!canSharePrice && !includedInParentPrice) {
                    Text("已有价格记录，请先在价格与付款中核对后再改为共用价格",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = enabled, onClick = { onParentSelected(null) }) { Text("解除主件挂载") }
            }
        }
    }
    if (showPicker && enabled) {
        ItemSearchPickerDialog(
            title = "选择所属主件",
            items = items.filter { it.parentItemId == null && it.id != currentItemId },
            onSelect = { onParentSelected(it.id); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
internal fun ItemSearchPickerDialog(
    title: String,
    items: List<Item>,
    onSelect: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        items.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("搜索服饰") }, leadingIcon = { SkinIcon(IconKey.Search) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) Text("没有可选择的服饰", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Row(modifier = Modifier.fillMaxWidth().skinClickable { onSelect(item) }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LolitaShimmerImage(model = item.imageUrls.firstOrNull(), contentDescription = item.name,
                                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop,
                                placeholderInitial = item.name.firstOrNull()?.toString())
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(itemStatusLabel(item.status), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

internal fun itemStatusLabel(status: ItemStatus): String = when (status) {
    ItemStatus.OWNED -> "已拥有"
    ItemStatus.WISHED -> "愿望单"
    ItemStatus.PENDING_BALANCE -> "待补尾款"
    ItemStatus.PENDING_PAYMENT -> "待付款"
    ItemStatus.PENDING_REVIEW -> "待核对"
}
