package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.*
import java.io.File

@Composable
fun ImageTaskDetailScreen(taskId: String, onNavigateBack: () -> Unit, onRetry: (ImageTask) -> Unit,
    viewModel: ImageTaskViewModel = viewModel(), onOpenPersonalModels: () -> Unit = {}, onOpenMaterial: (ImageTask) -> Unit = onRetry) {
    val state by viewModel.uiState.collectAsState()
    val task = state.tasks.firstOrNull { it.id == taskId }
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var confirmation by remember(taskId) { mutableStateOf<String?>(null) }
    var makeDefault by rememberSaveable(taskId) { mutableStateOf(true) }
    var zoom by remember(taskId) { mutableStateOf(false) }
    val path = task?.let(state::resultPath)?.takeIf { File(it).isFile }
    val busy = taskId in state.mutating
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("图像任务详情") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
        })
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("imageTaskDetail"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            if (state.isLoading) item("loading") { LolitaCard { ShimmerRect(160.dp, 220.dp, modifier = Modifier.fillMaxWidth()) } }
            if (!state.isLoading && task == null) item("missing") { Text("任务已删除或无法读取，请返回任务中心。") }
            if (task != null) {
                item("status") { LolitaSection(title = task.title) { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    Text("${task.displayTypeLabel} · ${task.provider.label}", style = MaterialTheme.typography.bodyMedium)
                    TaskStage(task)
                    Text("创建于 ${formatTaskDate(task.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("更新于 ${formatTaskDate(task.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                    val request = remember(task.requestJson) { runCatching { ImageTaskRequest.decode(task.requestJson) }.getOrNull() }
                    request?.let {
                        if (it.garments.isNotEmpty()) Text(it.garments.joinToString("、") { row -> row.name }, Modifier.padding(top = skin.spacingSmall))
                        it.tryOnModel?.let { person -> Text("本次试衣人物：${person.name}", Modifier.padding(top = skin.spacingSmall)) }
                        if (task.kind == ImageTaskKind.TRY_ON && it.instructions.isNotBlank()) Text("穿戴要求：${it.instructions}", Modifier.padding(top = skin.spacingSmall))
                        it.personalModel?.let { person ->
                            Text("${person.name} · ${person.measurements.heightCm} 厘米 · ${person.measurements.weightKg} 公斤", Modifier.padding(top = skin.spacingSmall))
                        }
                    }
                    (if (task.sourceResourcesMissing) ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE else task.error)?.let { Text(it, Modifier.padding(top = skin.spacingSmall), color = MaterialTheme.colorScheme.error) }
                    if (task.requestSent && task.status != ImageTaskStatus.COMPLETED && task.status != ImageTaskStatus.AWAITING_CONFIRMATION) {
                        Text("请求可能已发送，停止本地等待不代表远端取消，服务可能仍会计费。", Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall)
                    }
                } } }
                if (path != null) item("result") { LolitaSection(title = if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) "待确认${task.displayTypeLabel}" else "生成结果") { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    ImageFrame(Modifier.fillMaxWidth()) {
                        LolitaShimmerImage(model = path, contentDescription = "放大生成结果", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 440.dp).aspectRatio(0.75f).skinClickable { zoom = true }, circularRevealEnabled = false)
                    }
                    Text("点击图片放大核对", Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall)
                } } }
                else if (task.status in setOf(ImageTaskStatus.COMPLETED, ImageTaskStatus.AWAITING_CONFIRMATION)) item("missing-result") {
                    Text(if (task.status == ImageTaskStatus.COMPLETED && task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && task.materialId == null) "主视图已用于后续分层，请在任务中心查看分层任务。" else if (task.status == ImageTaskStatus.COMPLETED && task.kind == ImageTaskKind.PERSONAL_MODEL) "已保存的个人模特已删除或底图已丢失。任务记录仍保留。" else if (task.status == ImageTaskStatus.COMPLETED) "原结果已删除或图片已丢失。任务记录仍保留。" else "预览图片已丢失，可舍弃后重新发起。")
                }
                item("actions") { LolitaSection(title = "任务操作") { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) {
                        when (task.kind) {
                            ImageTaskKind.PERSONAL_MODEL -> {
                                Text("请完整核对头脚、面容与身体比例后保存。离开页面和重启均保留预览；调整会另存，原预览保留至明确舍弃。", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it }, enabled = !busy)
                                    TextButton(onClick = { makeDefault = !makeDefault }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("设为默认试衣人物") }
                                }
                                Button(onClick = { confirmation = "save-personal" }, enabled = !busy && path != null, modifier = Modifier.fillMaxWidth()) { Text("保存个人模特") }
                                OutlinedButton(onClick = { onRetry(task) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("调整后另存") }
                            }
                            ImageTaskKind.MANNEQUIN -> {
                                Text("核对花纹、版型与细节后再添加。离开页面和重启均保留此预览。", style = MaterialTheme.typography.bodySmall)
                                if (!state.canAppend(task)) Text("原物品已删除，可查看或舍弃预览。", color = MaterialTheme.colorScheme.error)
                                Button(onClick = { confirmation = "append" }, enabled = !busy && path != null && state.canAppend(task), modifier = Modifier.fillMaxWidth()) { Text("添加到原物品图片") }
                            }
                            ImageTaskKind.SINGLE_ITEM_MATERIAL -> {
                                Text("请进入核对页检查主视角或透明单品；保存不会追加到商品相册。", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onOpenMaterial(task) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("核对试衣素材") }
                            }
                            ImageTaskKind.TRY_ON -> Unit
                        }
                        OutlinedButton(onClick = { confirmation = "discard" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("舍弃预览") }
                    }
                    if (task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL && task.status == ImageTaskStatus.COMPLETED) {
                        TextButton(onClick = { onOpenMaterial(task) }, modifier = Modifier.fillMaxWidth()) { Text("查看当前试衣素材") }
                    }
                    if (task.kind == ImageTaskKind.PERSONAL_MODEL && task.status == ImageTaskStatus.COMPLETED) {
                        val acceptedId = task.personalModelId ?: state.savedPersonalIds[taskId]
                        if (state.personalModels.any { it.id == acceptedId }) {
                            Text("个人模特已保存，可在模特管理中查看或用于试衣。", style = MaterialTheme.typography.bodySmall)
                            if (taskId !in state.defaultSaveFailed) OutlinedButton(onClick = { viewModel.confirmPersonal(taskId, true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("设为默认试衣人物") }
                        }
                        TextButton(onClick = onOpenPersonalModels, modifier = Modifier.fillMaxWidth()) { Text("查看个人模特") }
                    }
                    if (taskId in state.defaultSaveFailed) {
                        Button(onClick = { viewModel.confirmPersonal(taskId, true) }, enabled = !busy && state.personalModels.any { it.id == (task.personalModelId ?: state.savedPersonalIds[taskId]) }, modifier = Modifier.fillMaxWidth()) { Text("重试设为默认") }
                    }
                    if (task.status.retainsSourceImages) OutlinedButton(onClick = {
                        if (task.status == ImageTaskStatus.QUEUED) viewModel.cancel(taskId) else confirmation = "cancel"
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消任务") }
                    if (task.canRetry) {
                        Text(if (task.kind == ImageTaskKind.PERSONAL_MODEL) "重新发起会载入原人物参数与可用参考照片，请核对并再次确认费用。原任务记录保留。" else "重新发起会载入当前服饰图片与原试衣人物，请核对并再次确认费用。原任务记录保留。", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onRetry(task) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("核对后重新发起") }
                    }
                    if (!task.status.retainsSourceImages) TextButton(onClick = { confirmation = "delete" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("删除任务记录") }
                    if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("正在处理…", style = MaterialTheme.typography.bodySmall) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } } }
            }
        }
    }
    if (zoom && path != null) FullScreenImageViewer(listOf(path), onDismiss = { zoom = false })
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when (action) { "save-personal" -> "保存个人模特？"; "append" -> "添加到原物品图片？"; "discard" -> "舍弃此预览？"; "cancel" -> "停止等待？"; else -> "删除任务记录？" }) },
            text = { Text(when (action) {
                "save-personal" -> "将保存已确认底图与人物资料，可用于后续试衣；此操作不会重新生成或计费。"
                "append" -> "此图片将追加到原物品图片末尾，原图片顺序保持不变。"
                "discard" -> "未保存的预览将被清理。"
                "cancel" -> "停止本地等待可能仍会计费，应用不会自动重试。"
                else -> if (task?.status == ImageTaskStatus.AWAITING_CONFIRMATION) "将删除任务记录并清理未保存预览。" else "删除记录后，已保存的个人模特、试衣历史和物品图片仍保留。"
            }) },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (action) { "save-personal" -> viewModel.confirmPersonal(taskId, makeDefault); "append" -> viewModel.confirm(taskId); "discard" -> viewModel.discard(taskId); "cancel" -> viewModel.cancel(taskId); else -> viewModel.delete(taskId) }
            }, enabled = !busy) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("返回核对") } })
    }
}
