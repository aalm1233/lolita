package com.lolita.app.ui.screen.material

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.data.local.entity.Item
import com.lolita.app.domain.model.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.tasks.LatestImageTask
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaterialWorkspaceScreen(viewModel: MaterialWorkspaceViewModel, addAccessory: Boolean,
    onBack: () -> Unit, onTask: (String) -> Unit, onTasks: () -> Unit, onConfigure: () -> Unit,
    onNewMaterial: (Long) -> Unit, onFullTryOn: (Long) -> Unit, onAddAccessory: (Long) -> Unit,
    onModels: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    var zoom by remember { mutableStateOf<String?>(null) }
    var sourcePicker by rememberSaveable { mutableStateOf(false) }
    var crop by remember { mutableStateOf<String?>(null) }
    var basePicker by rememberSaveable { mutableStateOf(false) }
    var modePicker by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var darkCheck by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.submittedTaskId) { state.submittedTaskId?.let { viewModel.consumeSubmission(); onTask(it) } }
    val managing = !addAccessory && state.task == null && state.material != null
    val primary: String = when {
        managing -> "重新生成展示图"
        addAccessory -> if (state.hasKey) "核对输入与试衣费用" else "配置图像服务"
        (!state.pending || state.front) && state.provider != ImageGenerationProvider.SEEDREAM -> "切换到即梦服务"
        state.pending && state.front || state.retryLayers -> "核对分层输入与费用"
        state.pending -> "确认并保存素材"
        state.task != null -> "重新选择图片整理"
        !state.hasKey -> "配置图像服务"
        else -> "核对输入与整理费用"
    }
    val canSubmit = !state.busy && !state.loading && when {
        managing -> state.eligible
        addAccessory -> state.accessoryEligible && state.keyLoaded && (!state.hasKey || state.material != null && state.selectedBase != null && state.clothingConfirmed)
        (!state.pending || state.front) && state.provider != ImageGenerationProvider.SEEDREAM -> state.eligible && state.keyLoaded
        state.pending && state.front || state.retryLayers -> state.eligible && state.frontConfirmed && state.keyLoaded && state.hasKey
        state.pending -> state.eligible && state.checks == setOf(0,1,2) && state.selectedPath != null
        state.task != null -> state.item != null
        else -> state.eligible && state.keyLoaded && (!state.hasKey || state.source.isNotBlank())
    }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0,0,0,0),
        topBar = { GradientTopAppBar(title = { Text(when {
            addAccessory -> "加配饰 · 实验"
            state.pending && state.front -> "核对主视图"
            state.pending -> "核对试衣素材"
            else -> "展示图"
        }, maxLines = 2) }, navigationIcon = {
            IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack, Modifier.semantics { contentDescription = "返回" }) }
        }, actions = { IconButton(onClick = onTasks) { SkinIcon(IconKey.ViewAgenda, Modifier.semantics { contentDescription = "任务中心" }) } }) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                if (!managing) Text(when {
                    addAccessory -> "穿搭底图＋腰饰素材，共2张；试衣另行计费。"
                    state.pending && state.front || state.retryLayers -> "分层是一次新请求，按实际成功输出张数计费。"
                    state.pending -> "本地保存不计费，原商品照片继续保留。"
                    else -> "整理、分层与试衣分别确认，不会自动连续生成。"
                }, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    when {
                        managing -> state.item?.id?.let(onNewMaterial)
                        addAccessory -> if (state.hasKey) viewModel.prepareFee(MaterialFeeAction.ADD_ACCESSORY) else onConfigure()
                        (!state.pending || state.front) && state.provider != ImageGenerationProvider.SEEDREAM -> onConfigure()
                        state.pending && state.front || state.retryLayers -> viewModel.prepareFee(MaterialFeeAction.DECOMPOSE)
                        state.pending -> if (state.material != null) replaceId = state.material?.id else viewModel.saveMaterial(null)
                        state.task != null -> onNewMaterial(state.item!!.id)
                        !state.hasKey -> onConfigure()
                        else -> viewModel.prepareFee(MaterialFeeAction.PREPARE)
                    }
                }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().testTag("materialPrimary")) {
                    if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(skin.spacingMedium)) }
                    Text(if (state.busy) "正在处理…" else primary)
                }
            }
        } }
    ) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("materialWorkspace"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.loading) item("loading") { Column { BoxWithConstraints(Modifier.fillMaxWidth()) { ShimmerRect(width = maxWidth, height = 210.dp) }; Spacer(Modifier.height(skin.spacingMedium)); ShimmerLine(widthFraction = 0.7f, modifier = Modifier.fillMaxWidth()) } }
            if (state.error != null) item("error") { LolitaSection(title = "操作提示") { row { Text(state.error!!, color = MaterialTheme.colorScheme.error) } } }
            if (!state.loading && state.item == null) item("missing") { LolitaSection(title = "原物品已删除") { row { Text("预览和任务仍可查看，无法保存到已删除物品；不会自动新建或改绑。") } } }
            if (addAccessory) {
                item("base") { LolitaSection(title = "原穿搭图", action = { TextButton(onClick = { basePicker = true }, enabled = !state.busy) { Text("更换") } }) { row {
                    val base = state.selectedBase
                    if (base == null) {
                        Text("请选择已保存试衣图，或已确认且穿着所需服装的个人模特。")
                        OutlinedButton(onClick = { basePicker = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("选择原穿搭图") }
                    } else {
                        MaterialPhoto(base.imagePath, "放大本次穿搭底图", onClick = { zoom = base.imagePath }, height = 250)
                        Text(base.snapshot.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = skin.spacingMedium))
                        MaterialCheck("保留这张底图上的现有服装", state.clothingConfirmed, !state.busy, viewModel::clothingConfirmed)
                    }
                    Text("底图中若已有冲突腰饰，请先更换适合的穿搭底图。", style = MaterialTheme.typography.bodySmall)
                } } }
                item("accessory") { LolitaSection(title = "本次新增一件腰饰") { row {
                    val material = state.material
                    if (material == null) {
                        Text("请先整理并确认这件腰饰的试衣素材。")
                        OutlinedButton(onClick = { state.item?.id?.let(onNewMaterial) }, modifier = Modifier.fillMaxWidth()) { Text("整理试衣素材") }
                    } else {
                        MaterialPhoto(material.imagePath, "已确认腰饰素材", onClick = { zoom = material.imagePath }, height = 160, checker = true)
                        Text(state.item?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text("已确认主视单品 · 穿戴位置：腰部", style = MaterialTheme.typography.bodySmall)
                    }
                } } }
                item("requirements") { LolitaSection(title = "穿戴要求") { row {
                    Text("添加这件腰饰，尽量保留原人物与搭配。")
                    MaterialInstructions(state.instructions, !state.busy, viewModel::instructions)
                    Text("人物、原裙纹和其他区域仍可能重画；这是穿搭效果预览，不能判断真实尺码。", style = MaterialTheme.typography.bodySmall)
                } } }
            } else if (state.retryLayers) {
                item("retry-layer") { LolitaSection(title = "重新核对主视图") { row {
                    Text("上次分层未完成；可复用这张主视图，重新核对分层费用。")
                    state.layerSource?.let { path -> MaterialPhoto(path, "放大待重新分层的主视图", onClick = { zoom = path }, height = 230) }
                    MaterialCheck("主体视角与结构正确", state.frontConfirmed, !state.busy, viewModel::frontConfirmed)
                    TextButton(onClick = { state.item?.id?.let(onNewMaterial) }, enabled = !state.busy) { Text("换图重新整理") }
                } } }
            } else if (state.pending) {
                item("result") { LolitaSection(title = if (state.front) "待核对主视图" else "待确认透明单品") { row {
                    if (state.layers.size > 1) {
                        Text("返回 ${state.layers.size} 个前景层，请选择包含完整单品的一层。", style = MaterialTheme.typography.bodySmall)
                        state.layers.forEach { layer ->
                            MaterialCheck(layer.name.ifBlank { "图层 ${layer.zIndex}" }, state.selectedPath == layer.imagePath, !state.busy) { viewModel.selectLayer(layer.imagePath) }
                        }
                    }
                    state.selectedPath?.let { path -> MaterialPhoto(path, "放大待核对图片", onClick = { zoom = path }, height = 230, checker = !state.front, darkChecker = darkCheck) }
                    if (!state.front) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(selected = !darkCheck, onClick = { darkCheck = false }, label = { Text("浅底检查") })
                        Spacer(Modifier.width(skin.spacingMedium))
                        FilterChip(selected = darkCheck, onClick = { darkCheck = true }, label = { Text("深底检查") })
                    }
                    Text(if (state.front) "此图尚未分离透明单品。主视正确后，另行确认图层拆分费用。" else "点击放大细链与透明间隙；检查底色不会写入图片。", style = MaterialTheme.typography.bodySmall)
                    if (state.task!!.generatedImageCount > 1) Text("本次分层成功输出 ${state.task!!.generatedImageCount} 张，含底图；按服务实际资费计费。", style = MaterialTheme.typography.bodySmall)
                } } }
                item("original") { LolitaSection(title = "对照本次输入") { row {
                    val path = state.request?.materialSpec?.originalInputPath ?: state.task?.sourceImagePaths?.firstOrNull()
                    path?.let { MaterialPhoto(it, "放大原输入", onClick = { zoom = it }, height = 130) }
                    Text("原图有透视时，摆正后主体更对称可以是正确结果。真实不对称细节仍应保留。", style = MaterialTheme.typography.bodySmall)
                } } }
                item("checks") { LolitaSection(title = if (state.front) "确认主视角" else "核对这件单品") { row {
                    if (state.front) MaterialCheck("主体视角与结构正确", state.frontConfirmed, !state.busy, viewModel::frontConfirmed)
                    else listOf("组成与附属部件完整", "颜色、造型与数量正确", "无多余人台、文字或其他物品").forEachIndexed { index, label ->
                        MaterialCheck(label, index in state.checks, !state.busy) { viewModel.check(index, it) }
                    }
                    Text("成双、成组及系带链条等附属部件，按物品原组成一起核对。", style = MaterialTheme.typography.bodySmall)
                } } }
                item("review-actions") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { state.item?.id?.let(onNewMaterial) }, enabled = !state.busy && state.item != null) { Text("换图再整理") }
                    TextButton(onClick = { discard = true }, enabled = !state.busy) { Text("舍弃预览") }
                } }
            } else {
                state.task?.let { task -> item("task") { LatestImageTask(task, onOpen = { onTask(task.id) }) } }
                if (state.saved || state.material != null) item("current") { LolitaSection(title = if (state.saved) "素材已保存" else "已确认展示图") { row {
                    state.material?.let { material ->
                        MaterialPhoto(material.imagePath, "放大当前确认素材", onClick = { zoom = material.imagePath }, height = 200, checker = true)
                        Text("AI 整理 · 已确认", style = MaterialTheme.typography.bodySmall)
                        val isCover = state.item?.imageUrls?.firstOrNull() == material.imagePath
                        OutlinedButton(onClick = { viewModel.setCover(material.id) }, enabled = !state.busy && !isCover && material.matchesSource(state.semantic, state.item?.composition ?: ItemComposition.UNCONFIRMED), modifier = Modifier.fillMaxWidth().testTag("materialSetCover")) {
                            Text(if (isCover) "当前封面" else "设为封面")
                        }
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = { if(state.accessoryEligible) modePicker = true else state.item?.id?.let(onFullTryOn) }, enabled = state.material?.matchesSource(state.semantic, state.item?.composition ?: ItemComposition.UNCONFIRMED) == true, modifier = Modifier.fillMaxWidth()) { Text("用于试衣") }
                        TextButton(onClick = { removeId = material.id }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("移除当前素材") }
                    }
                } } }
                if (state.task == null && !managing) {
                    item("source") { LolitaSection(title = "选择参考图", action = { TextButton(onClick = { sourcePicker = true }, enabled = !state.busy) { Text("换图") } }) { row {
                        if (state.source.isNotBlank()) MaterialPhoto(state.source, "放大所选商品图", onClick = { zoom = state.source }, height = 230)
                        else Text("这件物品还没有参考图片，请先在服饰编辑中添加。")
                        OutlinedButton(onClick = { crop = state.originalPath ?: state.source }, enabled = !state.busy && state.source.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("框选这件单品") }
                        Text(if (state.originalPath != null) "已框选：只发送本次选区，原图保留。" else "可选商品相册或详情图，只发送当前选中的一张。", style = MaterialTheme.typography.bodySmall)
                    } } }
                    item("prepare") { LolitaSection(title = "整理成主视单品") { row {
                        Text("按物品类型整理主视图，保留原结构与组成；AI 可能改变细节，请核对。")
                        MaterialInstructions(state.instructions, !state.busy, viewModel::instructions)
                        Text("素材整理使用即梦服务：先核对主视图，再单独确认分层费用；选出并核对单品层后保存。", style = MaterialTheme.typography.bodySmall)
                    } } }
                }
                if (!state.loading && !state.eligible && state.item != null) item("eligibility") { LolitaSection(title = "使用范围") { row { Text("支持已拥有或心愿中的服饰和小物，请先核对物品类型；旧任务的类型或组合改变后需重新整理。") } } }
            }
        }
    }
    state.fee?.let { fee -> MaterialFeeDialog(fee, state.busy, viewModel::dismissFee, { viewModel.submitFee(fee) }, { zoom = it }) }
    if (sourcePicker) state.item?.let { MaterialSourcePicker(it, state.originalPath ?: state.source, { viewModel.selectSource(it); sourcePicker = false }, { sourcePicker = false }) }
    crop?.let { original -> ImageRegionCropDialog(original, onDismiss = { crop = null }, onCropped = { viewModel.acceptCrop(original, it); crop = null }) }
    if (basePicker) MaterialBasePicker(state.bases, { viewModel.selectBase(it); basePicker = false }, { basePicker = false }, onModels)
    if (modePicker) AlertDialog(onDismissRequest = { modePicker = false }, title = { Text("如何使用这份素材") }, text = { Column {
        Text("完整换装继续选择基础服装；加配饰使用一张已有穿搭底图。")
        OutlinedButton(onClick = { modePicker = false; state.item?.id?.let(onFullTryOn) }, modifier = Modifier.fillMaxWidth()) { Text("完整换装") }
        if(state.accessoryEligible) Button(onClick = { modePicker = false; state.item?.id?.let(onAddAccessory) }, modifier = Modifier.fillMaxWidth()) { Text("在原穿搭上加配饰") }
    } }, confirmButton = { TextButton(onClick = { modePicker = false }) { Text("返回") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("舍弃本次预览？") }, text = { Text("只清理未被其他资料使用的预览；原图与当前确认素材继续保留。") }, confirmButton = { TextButton(onClick = { discard = false; viewModel.discardTask() }) { Text("舍弃") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("保留") } })
    removeId?.let { id -> AlertDialog(onDismissRequest = { removeId = null }, title = { Text("移除当前试衣素材？") }, text = { Text("原商品相册、已提交任务和试衣历史各自保留；重新整理需要再次确认生成费用。") }, confirmButton = { TextButton(onClick = { removeId = null; viewModel.removeMaterial(id) }) { Text("移除素材") } }, dismissButton = { TextButton(onClick = { removeId = null }) { Text("保留") } }) }
    replaceId?.let { id -> AlertDialog(onDismissRequest = { replaceId = null }, title = { Text("替换当前确认素材？") }, text = { Column {
        Text("这件物品已有一份确认素材。核对新旧结果后再替换，已提交任务继续使用原输入。")
        state.material?.let { MaterialPhoto(it.imagePath, "当前素材", onClick = { zoom = it.imagePath }, height = 110, checker = true) }
        state.selectedPath?.let { MaterialPhoto(it, "新素材", onClick = { zoom = it }, height = 110, checker = true) }
    } }, confirmButton = { TextButton(onClick = { replaceId = null; viewModel.saveMaterial(id) }) { Text("替换素材") } }, dismissButton = { TextButton(onClick = { replaceId = null }) { Text("返回核对") } }) }
    zoom?.let { path -> FullScreenImageViewer(imageUrls = listOf(path), initialPage = 0, onDismiss = { zoom = null }) }
}

@Composable
internal fun MaterialPhoto(path: String, label: String, onClick: () -> Unit, height: Int = 200,
    checker: Boolean = false, darkChecker: Boolean = false) {
    ImageFrame(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height.dp).skinClickable(onClick = onClick)) {
            if (checker) Canvas(Modifier.matchParentSize()) {
                val cell = 10.dp.toPx()
                val base = if (darkChecker) Color(0xFF252730) else Color(0xFFFAFAFA)
                val other = if (darkChecker) Color(0xFF353943) else Color(0xFFE9E9EB)
                drawRect(base)
                for (y in 0..(size.height / cell).toInt()) for (x in 0..(size.width / cell).toInt()) if ((x+y)%2 == 0)
                    drawRect(other, androidx.compose.ui.geometry.Offset(x*cell,y*cell), androidx.compose.ui.geometry.Size(cell,cell))
            }
            LolitaShimmerImage(model = path, contentDescription = label, contentScale = ContentScale.Fit, circularRevealEnabled = false, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MaterialCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().skinClickable(enabled = enabled) { onChange(!checked) }.padding(vertical = LolitaSkin.current.spacingSmall), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(start = LolitaSkin.current.spacingSmall), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun MaterialInstructions(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    Text("需要保留或补充的特征（可选）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = LolitaSkin.current.spacingMedium))
    OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled, placeholder = { Text("例如：两枚扣具、保留细链") },
        supportingText = { Text("${value.length} / 200 · 将发送给所选图像服务") }, modifier = Modifier.fillMaxWidth().testTag("materialInstructions"), minLines = 2,
        shape = RoundedCornerShape(LolitaSkin.current.cornerRadiusMedium))
}

@Composable
private fun MaterialFeeDialog(fee: MaterialFeeConfirmation, busy: Boolean, onDismiss: () -> Unit, onSend: () -> Unit, onZoom: (String) -> Unit) {
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var checked by remember(fee) { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.testTag("materialFeeRoot"), containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = WindowInsets(0,0,0,0), topBar = {
            GradientTopAppBar(title = { Text("发送前确认") }, navigationIcon = { IconButton(onClick = { if (!busy) onDismiss() }) { SkinIcon(IconKey.ArrowBack) } })
        }, bottomBar = { Column(Modifier.fillMaxWidth().padding(skin.spacingLarge)) {
            Button(onClick = onSend, enabled = checked && !busy, modifier = Modifier.fillMaxWidth().testTag("materialFeeSend")) { Text(if (busy) "正在加入任务…" else "确认发送并${if (fee.action == MaterialFeeAction.DECOMPOSE) "分层" else "生成"}") }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("返回修改") }
        } }) { padding -> LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item { Text("接收方：${fee.provider.recipient} · ${fee.provider.label}", style = MaterialTheme.typography.titleSmall) }
            item { Text("实际发送 ${fee.inputs.size} 张图片；不附带未选相册、订单、价格、位置、身体参数或原始自拍。", style = MaterialTheme.typography.bodyMedium) }
            itemsIndexed(fee.inputs) { index, input -> LolitaSection(title = "将发送的图片 ${index+1}") { row { MaterialPhoto(input.imagePath, "放大实际输入${index+1}", onClick = { onZoom(input.imagePath) }, height = 210) } } }
            item { LolitaSection(title = "本次费用与结果") { row {
                Text(when (fee.action) {
                    MaterialFeeAction.DECOMPOSE -> "本次仅1次图层拆分请求，可能返回底图1张与最多16个透明层，共最多17张。按实际成功输出张数计费，不能预先确认金额。返回后需选择并核对单品层，不自动试衣。"
                    MaterialFeeAction.PREPARE -> if (fee.provider == ImageGenerationProvider.SEEDREAM) "本次生成 1 张主视图，按服务资费计费；核对后另行确认分层费用，不自动继续生成。" else "本次仅生成1张透明主视候选图，API单独计费。返回后需核对，不能保证商品细节完全一致。"
                    MaterialFeeAction.ADD_ACCESSORY -> "本次生成1张试衣图，API单独计费。结果自动进入试衣历史，不记为实际穿过。人物、原裙纹和其他区域仍可能重画。"
                })
                if (fee.instructions.isNotBlank()) Text("本次补充要求：${fee.instructions}")
                Text("取消、超时后服务端仍可能处理或计费；应用不会自动重试。", style = MaterialTheme.typography.bodySmall)
            } } }
            item("confirmed") { MaterialCheck("我已核对以上实际图片、发送内容与费用范围", checked, !busy) { checked = it } }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaterialSourcePicker(item: Item, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(selected in item.productDetailImages) }
    val scroll = rememberLazyListState(); ObserveSkinScroll(scroll)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择参考图片") }, text = { Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingMedium)) { FilterChip(selected = !details, onClick = { details = false }, label = { Text("商品相册") }); FilterChip(selected = details, onClick = { details = true }, label = { Text("详情图") }) }
        LazyColumn(state = scroll, modifier = Modifier.heightIn(max = 440.dp)) {
            val images = (if (details) item.productDetailImages else item.imageUrls).distinct()
            if (images.isEmpty()) item { Text("此分组暂无图片。") }
            itemsIndexed(images) { index, path -> Column(Modifier.padding(vertical = LolitaSkin.current.spacingSmall)) {
                MaterialPhoto(path, "参考图${index+1}", onClick = { onSelect(path) }, height = 130)
                TextButton(onClick = { onSelect(path) }, modifier = Modifier.fillMaxWidth()) { Text("${if (path == selected) "已选 · " else ""}${if (details) "详情图" else "商品图"} ${index+1}") }
            } }
        }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

@Composable
private fun MaterialBasePicker(choices: List<MaterialBaseChoice>, onSelect: (MaterialBaseChoice) -> Unit, onDismiss: () -> Unit, onModels: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState(); ObserveSkinScroll(list)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择已有穿搭底图") }, text = { Column {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索试衣图或个人模特") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        LazyColumn(state = list, modifier = Modifier.heightIn(max = 420.dp)) {
            items(choices.filter { it.snapshot.name.contains(query.trim(), true) }, key = { "${it.snapshot.type}-${it.snapshot.id}" }) { choice ->
                Column(Modifier.fillMaxWidth().skinClickable { onSelect(choice) }.padding(vertical = LolitaSkin.current.spacingMedium)) {
                    MaterialPhoto(choice.imagePath, "查看底图${choice.snapshot.name}", onClick = { onSelect(choice) }, height = 140)
                    Text(choice.snapshot.name, style = MaterialTheme.typography.titleSmall)
                    Text(if (choice.snapshot.type == AccessoryBaseType.TRY_ON_RESULT) "已保存试衣图" else "个人模特 · 需确认保留现有服装", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (choices.isEmpty()) item { Text("还没有可用底图，可先创建个人模特并完成一套试衣。") }
        }
    } }, confirmButton = { TextButton(onClick = { onDismiss(); onModels() }) { Text("管理个人模特") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}
