package com.lolita.app.ui.screen.tryon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.TryOnSelection
import com.lolita.app.ui.screen.tasks.LatestImageTask
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TryOnScreen(onNavigateBack: () -> Unit, viewModel: TryOnViewModel = viewModel(),
    onNavigateToTasks: () -> Unit = {}, onTaskSubmitted: (String) -> Unit = {},
    onManagePersonalModels: () -> Unit = {}, onConfigureApi: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var showModelPicker by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var imageItemId by remember { mutableStateOf<Long?>(null) }
    var roleItemId by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<TryOnResult?>(null) }
    var delete by remember { mutableStateOf<TryOnResult?>(null) }
    var confirmation by remember { mutableStateOf<TryOnConfirmation?>(null) }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(title = { Text("虚拟试衣 · 实验") }, navigationIcon = {
                IconButton(onClick = onNavigateBack) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
            }, actions = {
                IconButton(onClick = onNavigateToTasks) { SkinIcon(IconKey.ViewAgenda, modifier = Modifier.semantics { contentDescription = "任务中心" }) }
            })
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    if (state.hasKey && !state.isSubmitting) {
                        Text("上传“${state.modelName}”与服饰共 ${state.generationSelected.size + 1} 张图片及名称、穿戴位置至 ${state.provider.recipient}，可能计费。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("tryOnUploadSummary"))
                    }
                    Button(
                        onClick = { if (!state.hasKey) onConfigureApi() else confirmation = TryOnConfirmation(state.provider, state.selectedPersonalModelId, state.selectedPersonalModel?.imageUrl, state.modelName, state.generationSelected.toList(), state.keyRevision) },
                        enabled = !state.busy && !state.isLoading && state.keyLoaded && (!state.hasKey || state.canGenerate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(skin.spacingMedium))
                        }
                        Text(if (state.isSubmitting) "正在加入任务…" else if (!state.hasKey) "配置 API Key" else "生成一张试衣图")
                    }

                }
            }
        }
    ) { padding ->
        LazyColumn(state = scroll,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("tryOnWorkspace"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            state.latestTaskId?.let { id -> item(key = "latest-task") {
                LatestImageTask(state.latestTask, onOpen = { onTaskSubmitted(id) })
            } }
            item(key = "model") {
                LolitaSection(title = "本次试衣人物", action = {
                    TextButton(onClick = onManagePersonalModels, enabled = !state.busy) { Text("管理模特") }
                }) {
                    row {
                        Spacer(Modifier.height(skin.spacingMedium))
                        if (!state.modelsLoaded) ShimmerRect(140.dp, 210.dp, modifier = Modifier.fillMaxWidth())
                        else {
                            Text(state.modelName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("tryOnSelectedModel"))
                            val modelPath = state.selectedPersonalModel?.imageUrl
                                ?: if (state.selectedPersonalModelId == null) "file:///android_asset/tryon/model.png" else null
                            if (modelPath != null) ImageFrame(Modifier.fillMaxWidth()) {
                                LolitaShimmerImage(model = modelPath,
                                    contentDescription = "放大本次试衣人物：${state.modelName}", contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(210.dp).skinClickable { preview = modelPath }, circularRevealEnabled = false)
                            }
                        }
                        OutlinedButton(onClick = { showModelPicker = true }, enabled = !state.busy && !state.isLoading && state.modelsLoaded,
                            modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium)) { Text("更换试衣人物") }
                        state.modelIssue?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Text("使用已确认的个人模特或内置虚拟模特预览搭配，更换人物保留已选服饰。生成可能偏离花纹、版型与细节，不能用于判断真实尺码。",
                            modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "service") {
                LolitaSection(title = "图像服务 · ${state.provider.label}", action = {
                    TextButton(onClick = { onConfigureApi() }, enabled = !state.busy) { Text("API 配置") }
                }) {
                    row {
                        ImageServiceSelector(state.provider, enabled = !state.busy && state.keyLoaded, onSelect = viewModel::selectProvider)
                        Text(if (!state.keyLoaded) "正在检查本机配置…" else if (state.hasKey) "个人 API Key 已配置" else "尚未配置个人 API Key",
                            modifier = Modifier.padding(vertical = skin.spacingMedium), style = MaterialTheme.typography.bodyMedium)
                        Text("${state.provider.keyHelp} 两家密钥分开加密保存在本机，不进入备份。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "selection-header") {
                LolitaSection(title = "搭配共 ${state.selected.size} 件 · 本次试衣 ${state.generationSelected.size} 件") {
                    row {
                        Text("每件使用一张已有图片；请核对穿戴位置。搭配可超过8件，本次试衣须明确选择1至8件，并包含至少一件基础服装。",
                            modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { showPicker = true }, enabled = !state.busy && !state.isLoading && !state.loadFailed,
                            modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium)) {
                            SkinIcon(IconKey.Add, Modifier.size(18.dp)); Spacer(Modifier.width(skin.spacingSmall)); Text("从衣橱选择")
                        }
                    }
                }
            }
            if (state.isLoading) item(key = "loading") {
                LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    ShimmerRect(width = 100.dp, height = 100.dp, modifier = Modifier.fillMaxWidth())
                    ShimmerLine(widthFraction = 0.8f, height = 16.dp)
                } }
            }
            items(state.selected, key = { "selected-${it.itemId}" }) { row ->
                val item = state.items.firstOrNull { it.id == row.itemId }
                LolitaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.toggleItem(row.itemId) }, enabled = !state.busy,
                                modifier = Modifier.semantics { contentDescription = "移除${row.name}" }) { SkinIcon(IconKey.Close) }
                        }
                        if (state.requiresExplicitSubset) Row(Modifier.fillMaxWidth().skinClickable(enabled = !state.busy) { viewModel.toggleGenerationItem(row.itemId) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = row.itemId in state.generationItemIds, onCheckedChange = { viewModel.toggleGenerationItem(row.itemId) }, enabled = !state.busy)
                            Text("用于本次试衣", style = MaterialTheme.typography.bodyMedium)
                        }
                        TryOnItemImage(row.imagePath, "${row.name}的参考图片", Modifier.fillMaxWidth().height(160.dp))
                        if (item == null) Text("服饰已不在已拥有衣橱，请移除后重新选择", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (row.imagePath.isBlank()) Text("这件服饰没有参考图片，请先到服饰编辑页添加", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { imageItemId = row.itemId }, enabled = !state.busy && item?.imageUrls?.any { it.isNotBlank() } == true,
                            modifier = Modifier.fillMaxWidth()) { Text("更换参考图片") }
                        OutlinedButton(onClick = { roleItemId = row.itemId }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("穿戴位置：${row.role.label}") }
                    }
                }
            }
            item("background-advice") { ImageBackgroundAdvice() }
            item(key = "disclosure") {
                LolitaSection(title = "生成前确认") {
                    row {
                        Text("点击生成后，将上传“${state.modelName}”底图与已选的 ${state.generationSelected.size} 张服饰图片到 ${state.provider.recipient}，共 ${state.generationSelected.size + 1} 张。仅上传这些图片和服饰名称、穿戴位置；生成可能产生费用。",
                            modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
                        Text("未选择鞋履时沿用人物底图鞋；未选择袜装时保留底图相应区域，这些默认外观不会登记为已选物品。", style = MaterialTheme.typography.bodySmall)
                        Text("试衣图独立保存，不会改变服饰归属、穿搭次数或账目。取消、超时后服务端仍可能处理；应用不会自动重试。",
                            modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
                        state.selectionIssue?.let { Text(it, modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        state.error?.let { Text(it, modifier = Modifier.padding(top = skin.spacingMedium), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item(key = "history-header") {
                LolitaSection(title = "已保存试衣图（${state.results.size}）") {
                    row { Text(if (state.historyFailed) "历史读取失败，请返回后重试" else if (state.results.isEmpty()) "生成成功后会自动保存在这里" else "点按图片可放大查看；原服饰删除后仍保留历史图。",
                        modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall) }
                }
            }
            items(state.results, key = { "result-${it.id}" }) { result ->
                LolitaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        Text(result.name, style = MaterialTheme.typography.titleMedium)
                        val date = remember(result.createdAt) { SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(result.createdAt)) }
                        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ImageFrame(Modifier.fillMaxWidth()) {
                            LolitaShimmerImage(model = result.imageUrl, contentDescription = "放大试衣图：${result.name}", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(260.dp)
                                    .then(Modifier.semantics { contentDescription = "放大试衣图：${result.name}" })
                                    .skinClickable { preview = result.imageUrl }, circularRevealEnabled = false)
                        }
                        Text("试衣人物：${result.personalModelName ?: "内置虚拟模特"}", style = MaterialTheme.typography.bodySmall)
                        Text(result.itemNames.joinToString("、"), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            TextButton(onClick = { rename = result }, modifier = Modifier.weight(1f)) { SkinIcon(IconKey.Edit, Modifier.size(18.dp)); Spacer(Modifier.width(skin.spacingSmall)); Text("重命名") }
                            TextButton(onClick = { delete = result }, modifier = Modifier.weight(1f)) { SkinIcon(IconKey.Delete, Modifier.size(18.dp)); Spacer(Modifier.width(skin.spacingSmall)); Text("删除") }
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) PersonalModelPicker(state, onSelect = { viewModel.selectPersonalModel(it); showModelPicker = false },
        onManage = { showModelPicker = false; onManagePersonalModels() }, onDismiss = { showModelPicker = false })
    if (showPicker) TryOnPicker(state, viewModel::updateSearchQuery, viewModel::selectCategory, viewModel::toggleItem) { showPicker = false }
    val imageItem = state.items.firstOrNull { it.id == imageItemId }
    if (imageItem != null) TryOnImagePicker(imageItem, state.selected.firstOrNull { it.itemId == imageItem.id }?.imagePath.orEmpty(),
        onSelect = { viewModel.setImage(imageItem.id, it) }, onDismiss = { imageItemId = null })
    val role = state.selected.firstOrNull { it.itemId == roleItemId }
    if (role != null) TryOnRolePicker(role.role, onSelect = { viewModel.setRole(role.itemId, it) }, onDismiss = { roleItemId = null })
    preview?.let { FullScreenImageViewer(listOf(it), onDismiss = { preview = null }) }
    rename?.let { result -> TryOnRenameDialog(result.name, onSave = { viewModel.rename(result.id, it); rename = null }, onDismiss = { rename = null }) }
    delete?.let { result -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除试衣图？") },
        text = { Text("将删除“${result.name}”及对应结果图，衣橱服饰不会改变。") },
        confirmButton = { TextButton(onClick = { viewModel.delete(result.id); delete = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("保留") } }) }
    state.pendingConflict?.let { pending ->
        AlertDialog(onDismissRequest = viewModel::cancelConflict, title = { Text("核对穿戴位置") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text(pending.conflict.message)
                if (pending.conflict.requiresReplacement) pending.conflict.itemIds.forEach { id ->
                    val old = state.selected.firstOrNull { it.itemId == id }
                    OutlinedButton(onClick = { viewModel.resolveConflict(id) }, modifier = Modifier.fillMaxWidth()) { Text("替换 ${old?.name.orEmpty()}") }
                }
            } },
            confirmButton = { if (!pending.conflict.requiresReplacement) TextButton(onClick = { viewModel.resolveConflict() }) { Text("确认叠穿并保留") } },
            dismissButton = { TextButton(onClick = viewModel::cancelConflict) { Text("返回保留原选择") } })
    }
    confirmation?.let { captured ->
        val provider = captured.provider
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("确认生成试衣图？") },
            text = { Text("将使用“${captured.name}”，上传底图与本次明确选择的${captured.selections.size}件物品至 ${provider.recipient}：\n" +
                captured.selections.joinToString("\n") { "${it.name} · ${it.role.label}" } + "\n未选鞋袜沿用底图，API 单独计费。任务提交后可在任务中心查看。") },
            confirmButton = { TextButton(onClick = { confirmation = null; viewModel.generate(provider, captured.id, captured.path, captured.selections, captured.keyRevision, captured.name) }) { Text("确认并加入任务") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("再核对一下") } })
    }
}


@Composable
private fun TryOnRenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("重命名试衣图") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("名称") }, singleLine = false, maxLines = 3) },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank() && value.trim().length <= 120) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun PersonalModelPicker(state: TryOnUiState, onSelect: (Long?) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val skin = LolitaSkin.current
    var query by rememberSaveable { mutableStateOf("") }
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择试衣人物") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索个人模特") }, singleLine = true)
            LazyColumn(state = scroll, modifier = Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                item("builtin") { OutlinedButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.selectedPersonalModelId == null) "内置虚拟模特 · 已选择" else "内置虚拟模特")
                } }
                items(state.personalModels.filter { it.name.contains(query.trim(), ignoreCase = true) }, key = { it.id }) { model ->
                    OutlinedButton(onClick = { onSelect(model.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(model.name + if (state.selectedPersonalModelId == model.id) " · 已选择" else "")
                    }
                }
                if (state.personalModels.isEmpty()) item("empty") { Text("暂无已保存的个人模特，可前往管理创建。") }
            }
        }
    }, confirmButton = { TextButton(onClick = onManage) { Text("管理模特") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

private data class TryOnConfirmation(val provider: ImageGenerationProvider, val id: Long?, val path: String?, val name: String, val selections: List<TryOnSelection>, val keyRevision: Int)
