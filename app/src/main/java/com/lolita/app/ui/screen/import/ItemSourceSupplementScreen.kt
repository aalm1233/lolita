package com.lolita.app.ui.screen.`import`

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.WardrobeCategorySelector
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*

@Composable
fun ItemSourceSupplementScreen(itemId: Long, onBack: () -> Unit, onEditDraft: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: ItemSourceSupplementViewModel =
        viewModel(
            key = "supplement:$itemId",
            factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ItemSourceSupplementViewModel(app, itemId) as T
                },
        )
    val state by vm.state.collectAsState()
    val product: TaobaoProductViewModel = viewModel(key = "supplement-web:$itemId")
    var prefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sourceUrl) {
        if (!prefilled && state.sourceUrl.isNotBlank()) {
            product.input(state.sourceUrl)
            prefilled = true
        }
    }
    if (state.item == null) {
        Column(Modifier.fillMaxSize().padding(LolitaSkin.current.spacingLarge)) {
            Text(state.error ?: "正在读取原物品…")
            TextButton(onClick = onBack) { Text("返回") }
        }
    } else if (state.candidate == null) {
        TaobaoProductScreen(
            onBack = onBack,
            onEdit = {},
            vm = product,
            supplementTargetName = state.item!!.name,
            onSupplementReady = vm::stage,
        )
    } else ProductSupplementCompare(state, vm, onBack, onEditDraft)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductSupplementCompare(
    state: ProductSupplementState,
    vm: ItemSourceSupplementViewModel,
    onBack: () -> Unit,
    onEditDraft: (String) -> Unit,
) {
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    var confirmed by rememberSaveable(state.candidate?.productUrl) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<String>?>(null) }
    BackHandler(enabled = state.busy) {}
    val item = state.item ?: return
    val candidate = state.candidate ?: return
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(
                title = { Text("补全本件商品资料") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
            )
        },
        bottomBar = {
            EditorSaveBar(
                if (state.existingEditor) "补充到已有编辑草稿" else "保存所选补充资料",
                state.busy,
                state.selected.isNotEmpty(),
            ) {
                vm.save(confirmed) { draftId ->
                    if (draftId == null) onBack() else onEditDraft(draftId)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingLarge),
        ) {
            item { ImportIntro(item.name, "已绑定原物品。价格、实付、拥有状态及收纳位置保持原值。") }
            state.error?.let { message ->
                item {
                    ImportNotice(message, true)
                    TextButton(
                        onClick = {
                            confirmed = false
                            vm.reload()
                        }
                    ) {
                        Text("重新读取并比较当前资料")
                    }
                }
            }
            if (state.existingEditor) item { ImportNotice("本件已有编辑草稿。所选补充会合入同一草稿，进入编辑页确认后才写入物品。") }
            item {
                LolitaSection("核对规格") {
                    row { Text(candidate.properties.ifBlank { "网页没有明确规格，请核对本件名称、颜色与尺码。" }) }
                    row {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(confirmed, { confirmed = it })
                            Text("确认网页商品和规格属于本件物品", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                SupplementChoice(
                    "name",
                    "商品名称",
                    item.name,
                    candidate.name,
                    state.selected,
                    vm::select,
                )
            }
            item {
                LolitaSection("品牌与类型") {
                    row {
                        Text(
                            "当前品牌：${state.brands.firstOrNull { it.id==item.brandId }?.name.orEmpty()}；网页：${candidate.brand.ifBlank { "未识别" }}"
                        )
                        ImportBrandPicker(
                            state.brands,
                            state.selectedBrand ?: item.brandId,
                            vm::brand,
                        )
                    }
                    row {
                        Text(
                            "当前类型：${state.categories.firstOrNull { it.id==item.categoryId }?.name.orEmpty()}；网页：${candidate.category.ifBlank { "未识别" }}"
                        )
                        WardrobeCategorySelector(
                            state.selectedCategory ?: item.categoryId,
                            state.categories,
                            vm::category,
                        )
                    }
                }
            }
            item {
                LolitaSection("颜色") {
                    row {
                        Text("当前：${item.colors.joinToString("、").ifBlank { "未设置" }}")
                        Text(
                            "请从上方网页规格核对本件颜色，多个颜色用顿号分隔。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            state.colorInput,
                            vm::color,
                            Modifier.fillMaxWidth(),
                            label = { Text("本件颜色") },
                        )
                        SupplementToggle("colors", "采用核对后的颜色", state.selected, vm::select)
                    }
                }
            }
            item {
                LolitaSection("尺码") {
                    row {
                        Text(
                            "当前：${item.size.orEmpty().ifBlank { "未设置" }}；网页：${candidate.size.ifBlank { "未选定" }}"
                        )
                        SizeSelector(state.sizeInput, vm::size, enabled = !state.busy)
                        SupplementToggle("size", "采用本次核对的尺码", state.selected, vm::select)
                    }
                }
            }
            if (candidate.properties.isNotBlank())
                item {
                    SupplementChoice(
                        "specifications",
                        "商品参数说明",
                        item.description,
                        candidate.properties,
                        state.selected,
                        vm::select,
                    )
                }
            item {
                LolitaSection("商品照片") {
                    row {
                        SupplementToggle(
                            "photos",
                            "追加 ${candidate.imagePaths.size} 张商品照片，保留原封面",
                            state.selected,
                            vm::select,
                        )
                        candidate.imagePaths.forEach { path ->
                            ImageFrame {
                                LolitaShimmerImage(
                                    path,
                                    "完整商品图",
                                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable {
                                        preview = candidate.imagePaths
                                    },
                                    contentScale = ContentScale.Fit,
                                    circularRevealEnabled = false,
                                )
                            }
                        }
                    }
                }
            }
            item {
                LolitaSection("尺码与详情图片") {
                    row {
                        SupplementToggle(
                            "details",
                            "追加 ${candidate.sizeChartImagePaths.size} 张完整资料图",
                            state.selected,
                            vm::select,
                        )
                        candidate.sizeChartImagePaths.forEach { path ->
                            LolitaShimmerImage(
                                path,
                                "完整资料图",
                                Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable {
                                    preview = candidate.sizeChartImagePaths
                                },
                                contentScale = ContentScale.Fit,
                                circularRevealEnabled = false,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "图片按内容去重。点图查看完整原图并放大。资料保留在本机草稿，退出可从本件继续。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    preview?.let { FullScreenImageViewer(it, onDismiss = { preview = null }) }
}

@Composable
private fun SupplementChoice(
    key: String,
    title: String,
    current: String,
    candidate: String,
    selected: Set<String>,
    onSelect: (String, Boolean) -> Unit,
) {
    LolitaSection(title) {
        row {
            SupplementToggle(key, "采用本项网页资料", selected, onSelect)
            Text("保留：${current.ifBlank { "未设置" }}")
            Text("网页：$candidate", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupplementToggle(
    key: String,
    label: String,
    selected: Set<String>,
    onSelect: (String, Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(key in selected, { onSelect(key, it) })
        Text(label, Modifier.weight(1f))
    }
}
