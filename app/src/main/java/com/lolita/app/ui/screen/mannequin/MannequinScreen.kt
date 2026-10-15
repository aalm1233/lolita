package com.lolita.app.ui.screen.mannequin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun MannequinScreen(
    onNavigateBack: () -> Unit,
    viewModel: MannequinViewModel = viewModel(),
    onNavigationBlockedChanged: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var showKey by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var leaveAfterCancel by remember { mutableStateOf(false) }
    var leaveAfterDiscard by remember { mutableStateOf(false) }
    val blockedCallback by rememberUpdatedState(onNavigationBlockedChanged)
    SideEffect { blockedCallback(state.navigationBlocked) }
    DisposableEffect(Unit) { onDispose { blockedCallback(false) } }
    val navigateBack = {
        when {
            state.backUnavailable -> Unit
            state.isGenerating -> { leaveAfterCancel = true; cancel = true }
            state.previewPath != null -> { leaveAfterDiscard = true; discard = true }
            else -> viewModel.requestDeparture()
        }
    }
    BackHandler(state.navigationBlocked) { navigateBack() }
    LaunchedEffect(state.readyToLeave) { if (state.readyToLeave) onNavigateBack() }
    LaunchedEffect(state.isLeaving, state.isGenerating, state.previewPath) {
        // Completion may win the race with cancel-and-return. A newly delivered
        // preview still requires a discard decision before its entry can depart.
        if (state.isLeaving && !state.isGenerating && state.previewPath != null) {
            leaveAfterDiscard = true
            discard = true
        }
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(title = { Text("正面人台图（实验）", maxLines = 2) }, navigationIcon = {
                IconButton(onClick = navigateBack, enabled = !state.backUnavailable) {
                    SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" })
                }
            })
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    if (state.previewPath != null) {
                        Button(onClick = viewModel::savePreview, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                            if (state.isSaving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(skin.spacingMedium))
                            } else { SkinIcon(IconKey.Save, Modifier.size(18.dp)); Spacer(Modifier.width(skin.spacingMedium)) }
                            Text(if (state.isSaving) "正在添加图片…" else "添加到裙子图片")
                        }
                        TextButton(onClick = { leaveAfterDiscard = false; discard = true }, enabled = !state.isSaving,
                            modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("放弃预览") }
                    } else {
                        Text("上传所选原图、固定模板及补充说明至 OpenAI，共2张图片；API 单独计费。",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("mannequinUploadSummary"))
                        Button(onClick = { if (!state.hasKey) showKey = true else viewModel.generate() },
                            enabled = !state.busy && !state.isLoading && !state.loadFailed && state.keyLoaded && state.selectionIssue == null && (!state.hasKey || state.canGenerate),
                            modifier = Modifier.fillMaxWidth()) {
                            if (state.isGenerating) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(skin.spacingMedium))
                            }
                            Text(when {
                                state.isGenerating -> when (state.phase) {
                                    TryOnGenerationPhase.PREPARING -> "准备参考图片…"
                                    TryOnGenerationPhase.GENERATING -> "正在生成人台图…"
                                    TryOnGenerationPhase.SAVING -> "正在保存预览…"
                                    else -> "正在生成…"
                                }
                                state.globalGenerating -> "另一图像任务正在进行"
                                !state.hasKey -> "配置 API Key"
                                else -> "生成正面人台图"
                            })
                        }
                        if (state.isGenerating) TextButton(onClick = { leaveAfterCancel = false; cancel = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消等待") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("mannequinWorkspace"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) item("loading") {
                LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    ShimmerLine(widthFraction = 0.7f, height = 18.dp)
                    ShimmerRect(width = 160.dp, height = 220.dp, modifier = Modifier.fillMaxWidth())
                } }
            }
            item("notice") {
                LolitaSection(title = state.item?.name ?: "裙子参考图") { row {
                    Text("仅生成正面；AI可能改绘印花、蕾丝和比例，请核对后保存。", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = skin.spacingMedium))
                    Text(if (state.previewPath == null) "生成结果先供预览，确认后追加到原图片末尾。" else "当前预览尚未保存，也尚未纳入备份。原图与主图顺序保持不变。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                    state.selectionIssue?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = skin.spacingSmall)) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall) }
                } }
            }
            val preview = state.previewPath
            if (preview != null) {
                item("preview") {
                    LolitaSection(title = "待保存人台图") { row {
                        Spacer(Modifier.height(skin.spacingMedium))
                        ImageFrame(Modifier.fillMaxWidth()) {
                            LolitaShimmerImage(model = preview, contentDescription = "放大正面人台图", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(300.dp).skinClickable { zoom = true }, circularRevealEnabled = false)
                        }
                        Text("点击放大核对人台图", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                    } }
                }
            } else {
                item("service") {
                    LolitaSection(title = "OpenAI 图像服务", action = {
                        TextButton(onClick = { showKey = true }, enabled = !state.busy) { Text("服务配置") }
                    }) { row {
                        Text(if (!state.keyLoaded) "正在检查本机配置…" else if (state.hasKey) "个人 API Key 已配置" else "尚未配置个人 API Key",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = skin.spacingMedium))
                        Text("与虚拟试衣共用本机加密密钥；API 与 ChatGPT 订阅分开计费。取消等待后服务端仍可能处理并计费，应用不会自动重试。",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                    } }
                }
                item("source") {
                    LolitaSection(title = "选择一张原图") { row {
                        Text("选清晰、完整的裙子正面；只上传选中的这一张。", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = skin.spacingMedium))
                    } }
                }
                itemsIndexed(state.item?.imageUrls.orEmpty().filter { it.isNotBlank() }.distinct(), key = { _, path -> "source-$path" }) { index, path ->
                    LolitaCard {
                        Column(Modifier.fillMaxWidth()) {
                            ImageFrame(Modifier.fillMaxWidth()) {
                                LolitaShimmerImage(model = path, contentDescription = "原图 ${index + 1}", contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(160.dp), circularRevealEnabled = false)
                            }
                            Row(Modifier.fillMaxWidth().semantics { selected = state.selectedImage == path }.skinClickable(enabled = !state.busy) { viewModel.selectImage(path) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.selectedImage == path, onClick = null, enabled = !state.busy)
                                Text("参考图片 ${index + 1}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                item("instructions") {
                    LolitaSection(title = "补充说明") { row {
                        OutlinedTextField(value = state.instructions, onValueChange = viewModel::updateInstructions,
                            label = { Text("补充说明（可选）") }, placeholder = { Text("例如：排除独立手袖") }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium), minLines = 2, maxLines = 5,
                            supportingText = { Text("${state.instructions.length}/200") }, shape = RoundedCornerShape(skin.cornerRadiusMedium))
                        Text("使用固定空白正面人台模板，不生成侧面或背面。", style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
    if (showKey) ApiKeyDialog(state.hasKey, state.keyRevision, state.isConfiguring, state.busy, state.error,
        viewModel::saveKey, viewModel::clearKey, onDismiss = { showKey = false })
    if (zoom && state.previewPath != null) FullScreenImageViewer(listOf(state.previewPath!!), onDismiss = { zoom = false })
    if (cancel) AlertDialog(onDismissRequest = { cancel = false; leaveAfterCancel = false }, title = { Text("取消等待？") },
        text = { Text("服务端可能仍在处理并计费，应用不会自动重试。") },
        confirmButton = { TextButton(onClick = { cancel = false; viewModel.cancelGeneration(leavePage = leaveAfterCancel); leaveAfterCancel = false }) { Text("取消等待") } },
        dismissButton = { TextButton(onClick = { cancel = false; leaveAfterCancel = false }) { Text("继续等待") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false; leaveAfterDiscard = false; viewModel.cancelDeparture() }, title = { Text("放弃未保存预览？") },
        text = { Text("这张预览尚未添加到裙子图片，放弃后将清理。原图片保留。") },
        confirmButton = { TextButton(onClick = {
            discard = false; viewModel.discardPreview(leavePage = leaveAfterDiscard); zoom = false
            leaveAfterDiscard = false
        }) { Text(if (leaveAfterDiscard) "放弃并返回" else "确认放弃") } },
        dismissButton = { TextButton(onClick = { discard = false; leaveAfterDiscard = false; viewModel.cancelDeparture() }) { Text("继续核对") } })
}
