package com.lolita.app.ui.screen.tryon

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.data.tryon.TryOnGenerationPhase
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
fun TryOnScreen(onNavigateBack: () -> Unit, viewModel: TryOnViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var showPicker by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var imageItemId by remember { mutableStateOf<Long?>(null) }
    var roleItemId by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<TryOnResult?>(null) }
    var delete by remember { mutableStateOf<TryOnResult?>(null) }
    var cancel by remember { mutableStateOf(false) }
    var leaveAfterCancel by remember { mutableStateOf(false) }
    val navigateBack = {
        if (state.isGenerating) { leaveAfterCancel = true; cancel = true } else onNavigateBack()
    }
    BackHandler(state.isGenerating) { navigateBack() }
    LaunchedEffect(state.latestResultId, state.results) {
        val result = state.results.firstOrNull { it.id == state.latestResultId }
        if (result != null) { preview = result.imageUrl; viewModel.consumeLatestResult() }
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(title = { Text("虚拟试衣 · 实验") }, navigationIcon = {
                IconButton(onClick = navigateBack) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
            })
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    if (state.hasKey && !state.isGenerating) {
                        Text("上传共 ${state.selected.size + 1} 张图片及名称、穿戴位置至 ${state.provider.recipient}，可能计费。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("tryOnUploadSummary"))
                    }
                    if (state.isGenerating) {
                        Text("请保持页面开启；取消等待后服务端仍可能处理并计费。", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { if (!state.hasKey) showKey = true else viewModel.generate() },
                        enabled = !state.busy && !state.isLoading && state.keyLoaded && (!state.hasKey || state.canGenerate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(skin.spacingMedium))
                        }
                        Text(when {
                            state.isGenerating -> when (state.phase) {
                                TryOnGenerationPhase.PREPARING -> "准备参考图片…"
                                TryOnGenerationPhase.GENERATING -> "正在生成试衣图…"
                                TryOnGenerationPhase.SAVING -> "正在保存试衣图…"
                                else -> "正在生成…"
                            }
                            state.globalGenerating -> "另一试衣任务正在进行"
                            !state.hasKey -> "配置 API Key"
                            else -> "生成一张试衣图"
                        })
                    }
                    if (state.isGenerating) TextButton(onClick = { leaveAfterCancel = false; cancel = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消等待") }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = scroll,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("tryOnWorkspace"),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item(key = "model") {
                LolitaSection(title = "固定成年虚拟模特") {
                    row {
                        Spacer(Modifier.height(skin.spacingMedium))
                        ImageFrame(Modifier.fillMaxWidth()) {
                            LolitaShimmerImage(model = "file:///android_asset/tryon/model.png",
                                contentDescription = "固定成年虚拟模特参考图", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(210.dp), circularRevealEnabled = false)
                        }
                        Spacer(Modifier.height(skin.spacingMedium))
                        Text("将衣橱服饰穿到同一位虚拟模特身上，预览整套搭配。生成可能偏离花纹、版型与细节，不能用于判断真实尺码。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "service") {
                LolitaSection(title = "图像服务 · ${state.provider.label}", action = {
                    TextButton(onClick = { showKey = true }, enabled = !state.busy) { Text("服务配置") }
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
                LolitaSection(title = "本次搭配（${state.selected.size}/8）") {
                    row {
                        Text("每件使用一张已有图片；请核对下方穿戴位置。至少选择一件服装，最多8件。",
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
                        TryOnItemImage(row.imagePath, "${row.name}的参考图片", Modifier.fillMaxWidth().height(160.dp))
                        if (item == null) Text("服饰已不在已拥有衣橱，请移除后重新选择", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (row.imagePath.isBlank()) Text("这件服饰没有参考图片，请先到服饰编辑页添加", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { imageItemId = row.itemId }, enabled = !state.busy && item?.imageUrls?.any { it.isNotBlank() } == true,
                            modifier = Modifier.fillMaxWidth()) { Text("更换参考图片") }
                        OutlinedButton(onClick = { roleItemId = row.itemId }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("穿戴位置：${row.role.label}") }
                    }
                }
            }
            item(key = "disclosure") {
                LolitaSection(title = "生成前确认") {
                    row {
                        Text("点击生成后，将上传固定模特与已选的 ${state.selected.size} 张服饰图片到 ${state.provider.recipient}，共 ${state.selected.size + 1} 张。仅上传这些图片和服饰名称、穿戴位置；生成可能产生费用。",
                            modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
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

    if (showPicker) TryOnPicker(state, viewModel::updateSearchQuery, viewModel::selectCategory, viewModel::toggleItem) { showPicker = false }
    val imageItem = state.items.firstOrNull { it.id == imageItemId }
    if (imageItem != null) TryOnImagePicker(imageItem, state.selected.firstOrNull { it.itemId == imageItem.id }?.imagePath.orEmpty(),
        onSelect = { viewModel.setImage(imageItem.id, it) }, onDismiss = { imageItemId = null })
    val role = state.selected.firstOrNull { it.itemId == roleItemId }
    if (role != null) TryOnRolePicker(role.role, onSelect = { viewModel.setRole(role.itemId, it) }, onDismiss = { roleItemId = null })
    if (showKey) ApiKeyDialog(state.hasKey, state.keyRevision, state.isConfiguring, state.busy, state.error, viewModel::saveKey, viewModel::clearKey, onDismiss = { showKey = false }, provider = state.provider)
    preview?.let { FullScreenImageViewer(listOf(it), onDismiss = { preview = null }) }
    rename?.let { result -> TryOnRenameDialog(result.name, onSave = { viewModel.rename(result.id, it); rename = null }, onDismiss = { rename = null }) }
    delete?.let { result -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除试衣图？") },
        text = { Text("将删除“${result.name}”及对应结果图，衣橱服饰不会改变。") },
        confirmButton = { TextButton(onClick = { viewModel.delete(result.id); delete = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("保留") } }) }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("取消等待？") },
        text = { Text("服务端可能仍在处理并计费，应用不会自动重试。已选服饰会保留。") },
        confirmButton = { TextButton(onClick = { cancel = false; viewModel.cancelGeneration(); if (leaveAfterCancel) onNavigateBack() }) { Text("取消等待") } },
        dismissButton = { TextButton(onClick = { cancel = false }) { Text("继续等待") } })
}


@Composable
private fun TryOnRenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("重命名试衣图") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("名称") }, singleLine = false, maxLines = 3) },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank() && value.trim().length <= 120) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
