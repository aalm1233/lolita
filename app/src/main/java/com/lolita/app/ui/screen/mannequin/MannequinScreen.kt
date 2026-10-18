package com.lolita.app.ui.screen.mannequin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.ui.screen.tasks.LatestImageTask
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

private data class MannequinConfirmation(val provider: ImageGenerationProvider, val request: MannequinRequest, val keyRevision: Int)

@Composable
fun MannequinScreen(
    onNavigateBack: () -> Unit,
    viewModel: MannequinViewModel = viewModel(),
    onNavigateToTasks: () -> Unit = {},
    onTaskSubmitted: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var showKey by remember { mutableStateOf(false) }
    var zoomLegacy by remember { mutableStateOf(false) }
    var discardLegacy by remember { mutableStateOf(false) }
    var headPreview by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<MannequinConfirmation?>(null) }
    val accessory = state.presentation == MannequinPresentation.ACCESSORY
    val wig = state.presentation == MannequinPresentation.WIG_HEAD

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(title = { Text("AI 展示图 · ${state.presentation.label}", maxLines = 2) }, navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" })
                }
            }, actions = {
                IconButton(onClick = onNavigateToTasks) { SkinIcon(IconKey.ViewAgenda, modifier = Modifier.semantics { contentDescription = "任务中心" }) }
            })
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    Text(if (wig) "上传所选头模与假发原图至 ${state.provider.recipient}，共2张图片；API 单独计费。"
                        else if (accessory) "上传所选小物原图及补充说明至 ${state.provider.recipient}，共1张图片；API 单独计费。"
                        else "上传所选原图、固定模板及补充说明至 ${state.provider.recipient}，共2张图片；API 单独计费。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("mannequinUploadSummary"))
                    Button(onClick = { if (!state.hasKey) showKey = true else state.generationRequest?.let { confirmation = MannequinConfirmation(state.provider, it, state.keyRevision) } },
                        enabled = !state.busy && !state.isLoading && !state.loadFailed && state.keyLoaded && state.selectionIssue == null,
                        modifier = Modifier.fillMaxWidth()) {
                        if (state.isSubmitting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(skin.spacingMedium)) }
                        Text(if (state.isSubmitting) "正在加入任务…" else if (!state.hasKey) "配置 API Key" else "生成${state.presentation.label}")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("mannequinWorkspace"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            state.latestTaskId?.let { id -> item("latest-task") {
                LatestImageTask(state.latestTask, onOpen = { onTaskSubmitted(id) })
            } }
            if (state.legacyPreviewAvailable) item("legacy-preview") {
                LolitaSection(title = "旧版未保存预览") { row {
                    Text("旧版预览仍保留在本机，不属于任务中心。请在退出前添加到裙子图片；离开不会删除文件。", modifier = Modifier.padding(top = skin.spacingMedium))
                    state.legacyPreviewPath?.let { path ->
                        ImageFrame(Modifier.fillMaxWidth()) {
                            LolitaShimmerImage(model = path, contentDescription = "放大旧版预览", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(280.dp).skinClickable { zoomLegacy = true }, circularRevealEnabled = false)
                        }
                        Button(onClick = viewModel::saveLegacyPreview, enabled = !state.busy && state.item != null, modifier = Modifier.fillMaxWidth()) { Text("添加旧版预览到裙子图片") }
                        TextButton(onClick = { discardLegacy = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("放弃旧版预览") }
                    } ?: Text("旧版预览无法通过文件或归属校验；文件未删除。", color = MaterialTheme.colorScheme.error)
                } }
            }
            if (state.isLoading) item("loading") {
                LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    ShimmerLine(widthFraction = 0.7f, height = 18.dp)
                    ShimmerRect(width = 160.dp, height = 220.dp, modifier = Modifier.fillMaxWidth())
                } }
            }
            item("background-advice") { ImageBackgroundAdvice() }
            item("notice") {
                LolitaSection(title = state.item?.name ?: "物品参考图") { row {
                    Text(if (wig) "只将本次假发戴在选定头模上，保留头模脸部、颈部和必要底座，不复制商品图中的真人面孔。头模均为候选，尚未通过长短假发真实评审。"
                        else if (accessory) "单独展示所选小物，不带人物或人台；原本成对或成组的物品保留组合。AI可能改绘细节，请核对后保存。"
                        else "仅生成无杆正面人台图，不保留支撑杆和底座；AI可能改绘印花、蕾丝和比例，请核对后保存。", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = skin.spacingMedium))
                    Text("结果保存在任务中心，核对后可追加到原图片末尾。离开页面不会放弃预览。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                    state.selectionIssue?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = skin.spacingSmall)) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall) }
                } }
            }
            if (wig) {
                item("head-choice-title") {
                    LolitaSection(title = "选择头模 · 单选") { row {
                        Text("五款均为候选，尚未统一构图及验证短发、长直发、长卷发。切换或放大不会调用付费接口。", modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodySmall)
                        state.selectedHeadTemplate?.let { Text("本次：${it.name} · v${it.version} · 待真实评审", style = MaterialTheme.typography.labelMedium) }
                        if (state.isLoadingHead) ShimmerRect(100.dp, 120.dp, modifier = Modifier.fillMaxWidth())
                    } }
                }
                itemsIndexed(state.headTemplates, key = { _, template -> "head-${template.id}-${template.version}" }) { _, template ->
                    val chosen = state.selectedHeadTemplate?.let { it.id == template.id && it.version == template.version && it.sha256 == template.sha256 } == true
                    LolitaCard(variant = CardVariant.COMPACT, modifier = Modifier.fillMaxWidth().semantics { selected = chosen }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                            LolitaShimmerImage(model = template.previewPath, contentDescription = "放大头模候选：${template.name}", contentScale = ContentScale.Fit,
                                circularRevealEnabled = false, modifier = Modifier.width(84.dp).height(126.dp).skinClickable { headPreview = template.previewPath })
                            Column(Modifier.weight(1f)) {
                                Text(template.name, style = MaterialTheme.typography.titleSmall)
                                Text(template.statusLabel, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { viewModel.selectHeadTemplate(template.id) }, enabled = !state.busy) { Text(if (chosen) "已选用" else "选用此头模") }
                            }
                            RadioButton(selected = chosen, onClick = { viewModel.selectHeadTemplate(template.id) }, enabled = !state.busy)
                        }
                    }
                }
                item("head-view-confirmation") {
                    LolitaSection(title = "核对实际两张输入") { row {
                        state.selectedHeadTemplate?.let { selected ->
                            LolitaShimmerImage(model = selected.imagePath, contentDescription = "本次实际头模输入", contentScale = ContentScale.Fit,
                                circularRevealEnabled = false, modifier = Modifier.fillMaxWidth().height(180.dp).skinClickable { headPreview = selected.imagePath })
                        }
                        if (state.selectedImage.isNotBlank()) LolitaShimmerImage(model = state.selectedImage, contentDescription = "本次实际假发输入", contentScale = ContentScale.Fit,
                            circularRevealEnabled = false, modifier = Modifier.fillMaxWidth().height(180.dp).skinClickable { headPreview = state.selectedImage })
                        Text("正面模板需要看得到刘海、头顶和正面轮廓的参考；只有背面或侧面的图请更换。长发应包含完整发梢，不凭空补出看不见的结构。", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().skinClickable(enabled = !state.busy) { viewModel.confirmReferenceView(!state.referenceViewConfirmed) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.referenceViewConfirmed, onCheckedChange = viewModel::confirmReferenceView, enabled = !state.busy)
                            Text("已核对视角与发梢，并知晓头模仍为待评审候选", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    } }
                }
            }
            item("service") {
                LolitaSection(title = "图像服务 · ${state.provider.label}", action = {
                    TextButton(onClick = { showKey = true }, enabled = !state.busy) { Text("服务配置") }
                }) { row {
                    ImageServiceSelector(state.provider, enabled = !state.busy && state.keyLoaded, onSelect = viewModel::selectProvider)
                    Text(if (!state.keyLoaded) "正在检查本机配置…" else if (state.hasKey) "个人 API Key 已配置" else "尚未配置个人 API Key",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = skin.spacingMedium))
                    Text("${state.provider.keyHelp} 与虚拟试衣共用服务配置，两家密钥分开保存。取消等待后服务端仍可能处理并计费，应用不会自动重试。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                } }
            }
            item("source") {
                LolitaSection(title = "选择一张原图") { row {
                    Text(if (wig) "请选择一张正面轮廓、刘海和发梢完整的假发商品图；多视图请先选定目标视图，不发送拼图或多个头模。"
                        else if (accessory) "选清晰、完整的小物图；只上传选中的这一张。若图中有多件物品，请在补充说明中指明目标。"
                        else "选清晰、完整的裙子正面；只上传选中的这一张。", style = MaterialTheme.typography.bodySmall,
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
                        label = { Text("补充说明（可选）") }, placeholder = { Text(if (accessory) "例如：只保留头上的红色KC" else "例如：排除独立手袖") }, enabled = !state.busy && !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium), minLines = 2, maxLines = 5,
                        supportingText = { Text("${state.instructions.length}/200") }, shape = RoundedCornerShape(skin.cornerRadiusMedium))
                    Text(if (wig) "仅上传本次选定头模与假发图，输出一张戴头模图；保留原图，先确认后添加。"
                        else if (accessory) "不上传人台模板，按原图角度生成独立物品展示图。" else "使用固定无杆正面人台模板，不生成侧面或背面。", style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
    headPreview?.let { FullScreenImageViewer(listOf(it), onDismiss = { headPreview = null }) }
    if (showKey) ApiKeyDialog(state.hasKey, state.keyRevision, state.isConfiguring, state.busy, state.error,
        viewModel::saveKey, viewModel::clearKey, onDismiss = { showKey = false }, provider = state.provider)
    if (zoomLegacy && state.legacyPreviewPath != null) FullScreenImageViewer(listOf(state.legacyPreviewPath!!), onDismiss = { zoomLegacy = false })
    if (discardLegacy) AlertDialog(onDismissRequest = { discardLegacy = false }, title = { Text("放弃旧版预览？") },
        text = { Text("此预览尚未添加到裙子图片，放弃后将按引用保护清理。") },
        confirmButton = { TextButton(onClick = { discardLegacy = false; viewModel.discardLegacyPreview() }) { Text("确认放弃") } },
        dismissButton = { TextButton(onClick = { discardLegacy = false }) { Text("继续核对") } })
    confirmation?.let { confirmed ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("确认生成${confirmed.request.presentation.label}？") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                confirmed.request.wigHeadTemplate?.let { head ->
                    Text("头模：${head.name} · v${head.version} · 候选待真实评审")
                    LolitaShimmerImage(model = head.imagePath, contentDescription = "本次将上传的头模", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(120.dp), circularRevealEnabled = false)
                }
                ImageFrame(Modifier.fillMaxWidth()) {
                    LolitaShimmerImage(model = confirmed.request.imagePath, contentDescription = "本次将上传的原图", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(120.dp), circularRevealEnabled = false)
                }
                Text("分类：${confirmed.request.subject?.categoryName.orEmpty()}")
                if (confirmed.request.instructions.isNotBlank()) Text("补充说明：${confirmed.request.instructions}")
                Text(if (confirmed.request.presentation == MannequinPresentation.WIG_HEAD)
                    "将上传上方选定的头模与假发原图至 ${confirmed.provider.recipient}，共2张图片，API 单独计费。结果须核对颜色、形状、组成数量和发梢；不会替换封面。"
                    else if (confirmed.request.presentation == MannequinPresentation.ACCESSORY)
                    "将上传这张小物原图与补充说明至 ${confirmed.provider.recipient}，不上传人台模板，共1张图片；API 单独计费。结果在任务中心核对后再添加。"
                    else "将上传这张原图、无杆人台模板与补充说明至 ${confirmed.provider.recipient}，共2张图片；API 单独计费。结果在任务中心核对后再添加。")
            } },
            confirmButton = { TextButton(onClick = { confirmation = null; viewModel.generate(confirmed.provider, confirmed.request, confirmed.keyRevision) }) { Text("确认并加入任务") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("再核对一下") } })
    }
}
