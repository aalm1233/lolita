package com.lolita.app.ui.screen.personalmodel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.domain.model.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalModelEditScreen(
    onBack: () -> Unit,
    onTask: (String) -> Unit,
    viewModel: PersonalModelEditViewModel = viewModel(),
    onConfigureApi: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val context = LocalContext.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var pendingRole by rememberSaveable { mutableStateOf(PersonalModelPhotoRole.FACE.name) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ImageGenerationProvider?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addPhoto(context, uri, PersonalModelPhotoRole.valueOf(pendingRole))
    }
    val editable = !state.isLoading && !state.loadFailed && !state.busy
    val canExit = !state.busy && !state.isSavingDraft && !state.isLoading
    val exit = { if (canExit) { if (state.dirty) discard = true else onBack() } }
    BackHandler { exit() }
    LaunchedEffect(state.taskId) { state.taskId?.let(onTask) }
    LaunchedEffect(state.discarded) { if (state.discarded) onBack() }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("创建模特") }, navigationIcon = {
            IconButton(onClick = exit, enabled = canExit) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
        })
    }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                val count = state.effectivePhotos().size
                Text(if (state.draft.mode == PersonalModelMode.PHOTO)
                    "上传所选${count}张参考照、布局模板与身体参数至 ${state.provider.recipient}；API 单独计费。"
                    else "上传身体参数${if (count > 0) "、已确认的虚拟面容参考" else ""}及布局模板至 ${state.provider.recipient}；不上传保留的本人照片。API 单独计费。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("personalModelUploadSummary"))
                Button(onClick = { if (!state.hasKey) onConfigureApi() else confirm = state.provider }, enabled = if (state.hasKey) state.canGenerate else !state.isLoading && !state.loadFailed && !state.busy && state.keyLoaded,
                    modifier = Modifier.fillMaxWidth().testTag("generatePersonalModel")) {
                    if (state.isSubmitting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(skin.spacingMedium)) }
                    Text(if (state.isSubmitting) "正在加入任务…" else if (!state.hasKey) "配置服务密钥" else "生成模特")
                }
            }
        }
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("personalModelEditor"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) item("loading") { LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                ShimmerLine(widthFraction = .7f, height = 24.dp); ShimmerRect(width = 200.dp, height = 180.dp, modifier = Modifier.fillMaxWidth())
            } } }
            item("mode") {
                LolitaSection(title = "创建方式") { row {
                    FlowRow(Modifier.fillMaxWidth().padding(top = skin.spacingMedium), horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        FilterChip(selected = state.draft.mode == PersonalModelMode.PHOTO, onClick = { viewModel.update { it.copy(mode = PersonalModelMode.PHOTO) } },
                            enabled = editable, label = { Text("照片生成") })
                        FilterChip(selected = state.draft.mode == PersonalModelMode.PARAMETERS, onClick = { viewModel.update { it.copy(mode = PersonalModelMode.PARAMETERS) } },
                            enabled = editable, label = { Text("参数生成") })
                    }
                    Text(if (state.draft.mode == PersonalModelMode.PHOTO) "添加脸部照或正面全身照，也可同时提供。" else "使用虚拟面容。切换前的本人照片只保留在本机草稿中。", style = MaterialTheme.typography.bodySmall)
                } }
            }
            if (state.draft.mode == PersonalModelMode.PHOTO) {
                item("photos") {
                    LolitaSection(title = "参考照片") { row {
                        Row(Modifier.fillMaxWidth().padding(top = skin.spacingMedium),
                            horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                            listOf(PersonalModelPhotoRole.FACE, PersonalModelPhotoRole.FRONT, PersonalModelPhotoRole.SIDE).forEach { role ->
                                val title = when (role) { PersonalModelPhotoRole.FACE -> "脸部照"; PersonalModelPhotoRole.FRONT -> "正面全身"; else -> "侧面照" }
                                val path = state.paths.getOrNull(state.draft.photoRoles.indexOf(role))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                                    ImageFrame(Modifier.fillMaxWidth()) {
                                        if (path != null) {
                                            LolitaShimmerImage(model = path, contentDescription = "放大$title", contentScale = ContentScale.Fit,
                                                circularRevealEnabled = false, modifier = Modifier.fillMaxWidth().aspectRatio(.8f).skinClickable { preview = path })
                                        } else {
                                            Box(Modifier.fillMaxWidth().aspectRatio(.8f).skinClickable(enabled = editable) {
                                                pendingRole = role.name; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            }, contentAlignment = Alignment.Center) {
                                                SkinIcon(IconKey.AddPhoto, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Text(title, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = {
                                        pendingRole = role.name; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }, enabled = editable, contentPadding = PaddingValues(horizontal = skin.spacingSmall)) { Text(if (path == null) "添加" else "替换") }
                                    if (path != null) TextButton(onClick = { viewModel.removePhoto(role) }, enabled = editable,
                                        contentPadding = PaddingValues(horizontal = skin.spacingSmall)) { Text("移除") }
                                }
                            }
                        }
                        Text("脸部照或正面全身照至少一张；侧面照可选。自然站立、光线清晰，点照片可完整放大。", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = skin.spacingSmall))
                        if (android.os.Build.VERSION.SDK_INT < 28) {
                            Text("此系统请使用完整的 PNG 或 JPEG 照片；其他格式请先另存后添加。", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = skin.spacingSmall))
                        }
                        if (state.isCopying) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("正在保存照片…", modifier = Modifier.weight(1f).padding(start = skin.spacingMedium))
                                TextButton(onClick = viewModel::cancelPhotoCopy) { Text("取消") }
                            }
                        }
                    } }
                }
            } else item("appearance") {
                LolitaSection(title = "虚拟面容") { row {
                    FlowRow(Modifier.fillMaxWidth().padding(top = skin.spacingMedium), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                        PersonalModelAppearance.entries.forEach { appearance ->
                            FilterChip(selected = state.draft.appearance == appearance, onClick = { viewModel.update { it.copy(appearance = appearance) } },
                                enabled = editable, label = { Text(when (appearance) { PersonalModelAppearance.FEMALE -> "女性"; PersonalModelAppearance.MALE -> "男性"; PersonalModelAppearance.NEUTRAL -> "中性" }) })
                        }
                    }
                    state.effectivePhotos().firstOrNull()?.second?.let { path ->
                        Text("沿用已确认模特的虚拟面容，调整身体参数后另存。", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { preview = path }) { Text("查看面容参考") }
                    }
                } }
            }
            item("measurements") {
                LolitaSection(title = "身体参数") { row {
                    MeasurementField("身高（必填）", "cm", state.draft.heightCm, editable) { value -> viewModel.update { it.copy(heightCm = value) } }
                    MeasurementField("体重（必填）", "kg", state.draft.weightKg, editable) { value -> viewModel.update { it.copy(weightKg = value) } }
                    val additionalCount = listOf(state.draft.bustCm, state.draft.waistCm, state.draft.hipCm, state.draft.shoulderCm, state.draft.inseamCm).count(String::isNotBlank)
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起围度与肩宽" else if (additionalCount > 0) "围度与肩宽（已填${additionalCount}项）" else "补充围度与肩宽")
                        SkinIcon(if (expanded) IconKey.ExpandLess else IconKey.ExpandMore)
                    }
                    if (expanded) {
                        MeasurementField("胸围（可选）", "cm", state.draft.bustCm, editable) { value -> viewModel.update { it.copy(bustCm = value) } }
                        MeasurementField("腰围（可选）", "cm", state.draft.waistCm, editable) { value -> viewModel.update { it.copy(waistCm = value) } }
                        MeasurementField("臀围（可选）", "cm", state.draft.hipCm, editable) { value -> viewModel.update { it.copy(hipCm = value) } }
                        MeasurementField("肩宽（可选）", "cm", state.draft.shoulderCm, editable) { value -> viewModel.update { it.copy(shoulderCm = value) } }
                        MeasurementField("内侧腿长（可选）", "cm", state.draft.inseamCm, editable) { value -> viewModel.update { it.copy(inseamCm = value) } }
                    }
                    Text("按实际测量填写，不会自动瘦身或拉长腿部。未填写的围度保持未知。", style = MaterialTheme.typography.bodySmall)
                } }
            }
            item("description") {
                LolitaSection(title = "模特名称与描述") { row {
                    OutlinedTextField(value = state.draft.name, onValueChange = { value -> viewModel.update { it.copy(name = value) } }, label = { Text("模特名称（1至60字）") },
                        modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium), enabled = editable, singleLine = true, shape = RoundedCornerShape(skin.cornerRadiusMedium))
                    OutlinedTextField(value = state.draft.instructions, onValueChange = { value -> viewModel.update { it.copy(instructions = value) } }, label = { Text("补充描述（可选）") },
                        placeholder = { Text("例如：保留短发与自然肤色") }, supportingText = { Text("${state.draft.instructions.length}/200") },
                        modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium), enabled = editable, minLines = 2, maxLines = 5, shape = RoundedCornerShape(skin.cornerRadiusMedium))
                } }
            }
            item("service") {
                LolitaSection(title = "图像服务 · ${state.provider.label}", action = { TextButton(onClick = { onConfigureApi() }, enabled = !state.busy && state.keyLoaded) { Text("API 配置") } }) { row {
                    ImageServiceSelector(state.provider, enabled = editable && state.keyLoaded, onSelect = viewModel::selectProvider)
                    Text(if (!state.keyLoaded) "正在检查本机配置…" else if (state.hasKey) "服务密钥已配置" else "尚未配置服务密钥", style = MaterialTheme.typography.bodySmall)
                    Text("生成结果会保留在任务中心，确认满意后才加入模特库。", style = MaterialTheme.typography.bodySmall)
                } }
            }
            item("validation") {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.draftSaveFailed) TextButton(onClick = viewModel::retrySaveDraft, enabled = editable && !state.isSavingDraft) { Text("重试保存草稿") }
                    if (!state.isLoading && !state.loadFailed) state.validationIssue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (state.isSavingDraft) Text("正在保存本机草稿…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    preview?.let { path -> FullScreenImageViewer(listOf(path), onDismiss = { preview = null }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃本次编辑？") },
        text = { Text("本机草稿及仅由草稿使用的照片会删除，已保存模特和历史任务继续保留。") },
        confirmButton = { TextButton(onClick = { discard = false; viewModel.discard() }, enabled = canExit) { Text("放弃并返回") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    confirm?.let { provider -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("确认生成模特？") },
        text = { Text("将上传${if (state.draft.mode == PersonalModelMode.PHOTO) "所选参考照片、" else if (state.effectivePhotos().isNotEmpty()) "已确认的虚拟面容参考、" else ""}身体参数与布局模板至 ${provider.recipient}。API 单独计费，应用不会自动付费重试。") },
        confirmButton = { TextButton(onClick = { confirm = null; viewModel.generate(provider) }) { Text("确认并加入任务") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("再核对一下") } }) }
}

@Composable
private fun MeasurementField(label: String, unit: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val skin = LolitaSkin.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, suffix = { Text(unit) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = skin.spacingMedium), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, shape = RoundedCornerShape(skin.cornerRadiusMedium))
}
