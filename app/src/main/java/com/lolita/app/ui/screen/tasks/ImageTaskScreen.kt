package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.lolita.app.domain.model.ImageTaskStatus
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageTaskScreen(onNavigateBack: () -> Unit, onOpenTask: (String) -> Unit, viewModel: ImageTaskViewModel = viewModel(),
    onNewMaterialBatch:()->Unit={},onOpenMaterialBatch:(String)->Unit={}) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("任务中心") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
        }, actions = { TextButton(onClick = onNewMaterialBatch,
            modifier = Modifier.semantics { contentDescription = "新建展示图批次" }) { Text("新建") } })
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("imageTasks"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item("filters") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    ImageTaskFilter.entries.forEach { filter ->
                        FilterChip(selected = state.filter == filter, onClick = { viewModel.filter(filter) }, label = { Text(filter.label) }, modifier = Modifier.semantics { contentDescription = "任务筛选：${filter.label}" })
                    }
                }
            }
            item("queue-actions") {
                ImageTaskQueueActions(state.tasks, state.isLoading || state.bulkBusy || state.mutating.isNotEmpty(), viewModel::stopQueued, viewModel::discardPending)
            }
            state.message?.let { message -> item("operation-message") { Text(message, style = MaterialTheme.typography.bodyMedium) } }
            if (state.isLoading) item("loading") { LolitaCard { Column { ShimmerLine(0.7f, 18.dp); Spacer(Modifier.height(skin.spacingMedium)); ShimmerLine(0.9f, 14.dp) } } }
            state.error?.let { error -> item("error") { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.isLoading && state.visibleTasks.none{it.batchId==null} && state.visibleMaterialBatches.isEmpty()) item("empty") {
                LolitaSection(title = "暂无${if (state.filter == ImageTaskFilter.ALL) "" else state.filter.label}任务") { row {
                    Text("从展示图、虚拟试衣或我的模特提交后，可在这里查看进度和结果。", Modifier.padding(top = skin.spacingMedium))
                } }
            }
            items(state.visibleMaterialBatches,key={requireNotNull(it.first().batchId)}){batch->
                LolitaSection(title=requireNotNull(batch.first().batchName)){row{
                    val count=batch.map{it.garmentId}.distinct().size
                    Text("${count}件物品 · ${batch.count{it.status.retainsSourceImages}}个排队或执行 · ${batch.count{it.status==com.lolita.app.domain.model.ImageTaskStatus.AWAITING_CONFIRMATION}}个待核对")
                    val latest=batch.groupBy{it.batchPosition}.values.map{rows->rows.maxWith(compareBy<ImageTask>{it.createdAt}.thenBy{it.id})}
                    Text("${latest.count{it.materialId!=null || it.kind==com.lolita.app.domain.model.ImageTaskKind.MANNEQUIN && it.status==com.lolita.app.domain.model.ImageTaskStatus.COMPLETED}}份已确认 · ${latest.count{it.status in setOf(com.lolita.app.domain.model.ImageTaskStatus.FAILED,com.lolita.app.domain.model.ImageTaskStatus.INTERRUPTED,com.lolita.app.domain.model.ImageTaskStatus.CANCELLED)}}件失败或中断",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={onOpenMaterialBatch(requireNotNull(batch.first().batchId))},modifier=Modifier.fillMaxWidth()){Text("查看批次")}
                }}
            }
            items(state.visibleTasks.filter{it.batchId==null}, key = { it.id }) { task ->
                ImageTaskResultCard(task, state.resultPath(task), onOpen = { onOpenTask(task.id) })
            }
            item("background-advice") { ImageBackgroundAdvice() }

        }
    }
}

@Composable
internal fun ImageTaskResultCard(task: ImageTask, resultPath: String?, onOpen: () -> Unit) {
    val skin = LolitaSkin.current
    val preview = resultPath?.takeIf { File(it).isFile }
    val action = when {
        task.status == ImageTaskStatus.AWAITING_CONFIRMATION -> "核对结果"
        task.status == ImageTaskStatus.COMPLETED && preview != null -> "查看结果"
        task.status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED) -> "查看原因"
        else -> "查看任务"
    }
    LolitaCard(Modifier.fillMaxWidth().skinClickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            ImageFrame(Modifier.width(72.dp)) {
                if (preview != null) LolitaShimmerImage(model = preview, contentDescription = "${task.title}的结果预览",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(88.dp), circularRevealEnabled = false)
                else Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                    SkinIcon(if (task.kind == ImageTaskKind.PERSONAL_MODEL) IconKey.Outfit else IconKey.Image,
                        Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                Text("${task.displayTypeLabel} · ${task.provider.label}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TaskStage(task)
                if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) Text("尚未确认保存", style = MaterialTheme.typography.bodySmall)
                if (resultPath != null && preview == null) Text("结果图片不可用，请进入任务核对", style = MaterialTheme.typography.bodySmall)
                Text(formatTaskDate(task.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (if (task.sourceResourcesMissing) ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE else task.error)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) Button(onClick = onOpen) { Text(action) }
                else TextButton(onClick = onOpen) { Text(action) }
            }
        }
    }
}

internal fun formatTaskDate(timestamp: Long): String = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

@Composable
internal fun TaskStage(task: ImageTask) {
    val skin = LolitaSkin.current
    var now by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(task.status, task.id) {
        while (task.status.retainsSourceImages) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = skin.spacingSmall), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        Text(task.status.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (task.status.retainsSourceImages) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("已提交 ${((now - task.createdAt).coerceAtLeast(0) / 1_000)} 秒", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LatestImageTask(task: ImageTask?, onOpen: () -> Unit) {
    val skin = LolitaSkin.current
    LolitaSection(title = "最近提交的任务") { row {
        task?.let { TaskStage(it) }
        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看任务") }
    } }
}
