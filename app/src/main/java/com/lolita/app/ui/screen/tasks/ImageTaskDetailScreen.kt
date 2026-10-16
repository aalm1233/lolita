package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    viewModel: ImageTaskViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val task = state.tasks.firstOrNull { it.id == taskId }
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var confirmation by remember(taskId) { mutableStateOf<String?>(null) }
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
                    Text("${if (task.kind == ImageTaskKind.TRY_ON) "虚拟试衣" else "正面人台图"} · ${task.provider.label}", style = MaterialTheme.typography.bodyMedium)
                    TaskStage(task)
                    Text("创建于 ${formatTaskDate(task.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("更新于 ${formatTaskDate(task.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                    val request = remember(task.requestJson) { runCatching { ImageTaskRequest.decode(task.requestJson) }.getOrNull() }
                    request?.let { Text(it.garments.joinToString("、") { row -> row.name }, Modifier.padding(top = skin.spacingSmall)) }
                    task.error?.let { Text(it, Modifier.padding(top = skin.spacingSmall), color = MaterialTheme.colorScheme.error) }
                    if (task.requestSent && task.status != ImageTaskStatus.COMPLETED && task.status != ImageTaskStatus.AWAITING_CONFIRMATION) {
                        Text("请求可能已发送，停止本地等待不代表远端取消，服务可能仍会计费。", Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall)
                    }
                } } }
                if (path != null) item("result") { LolitaSection(title = if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) "待确认人台图" else "生成结果") { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    ImageFrame(Modifier.fillMaxWidth()) {
                        LolitaShimmerImage(model = path, contentDescription = "放大生成结果", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 440.dp).aspectRatio(0.75f).skinClickable { zoom = true }, circularRevealEnabled = false)
                    }
                    Text("点击图片放大核对", Modifier.padding(top = skin.spacingSmall), style = MaterialTheme.typography.bodySmall)
                } } }
                else if (task.status in setOf(ImageTaskStatus.COMPLETED, ImageTaskStatus.AWAITING_CONFIRMATION)) item("missing-result") {
                    Text(if (task.status == ImageTaskStatus.COMPLETED) "原结果已删除或图片已丢失。任务记录仍保留。" else "预览图片已丢失，可舍弃后重新发起。")
                }
                item("actions") { LolitaSection(title = "任务操作") { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) {
                        Text("核对花纹、版型与细节后再添加。离开页面和重启均保留此预览。", style = MaterialTheme.typography.bodySmall)
                        if (!state.canAppend(task)) Text("原服饰已删除，可查看或舍弃预览。", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { confirmation = "append" }, enabled = !busy && path != null && state.canAppend(task), modifier = Modifier.fillMaxWidth()) { Text("添加到裙子图片") }
                        OutlinedButton(onClick = { confirmation = "discard" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("舍弃预览") }
                    }
                    if (task.status.retainsSourceImages) OutlinedButton(onClick = {
                        if (task.status == ImageTaskStatus.QUEUED) viewModel.cancel(taskId) else confirmation = "cancel"
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消任务") }
                    if (task.canRetry) {
                        Text("重新发起会载入当前服饰图片，请核对并再次确认费用。原任务记录保留。", style = MaterialTheme.typography.bodySmall)
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
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when (action) { "append" -> "添加到裙子图片？"; "discard" -> "舍弃此预览？"; "cancel" -> "停止等待？"; else -> "删除任务记录？" }) },
            text = { Text(when (action) {
                "append" -> "此图片将追加到原裙子图片末尾，原图片顺序保持不变。"
                "discard" -> "未保存的预览将被清理。"
                "cancel" -> "停止本地等待可能仍会计费，应用不会自动重试。"
                else -> if (task?.status == ImageTaskStatus.AWAITING_CONFIRMATION) "将删除任务记录并清理未保存预览。" else "删除记录后，已保存的试衣历史和裙子图片仍保留。"
            }) },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (action) { "append" -> viewModel.confirm(taskId); "discard" -> viewModel.discard(taskId); "cancel" -> viewModel.cancel(taskId); else -> viewModel.delete(taskId) }
            }, enabled = !busy) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("返回核对") } })
    }
}
