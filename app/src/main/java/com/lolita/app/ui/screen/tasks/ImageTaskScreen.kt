package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.layout.*
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
        })
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("imageTasks"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item("new-material-batch"){LolitaSection(title="批量整理试衣素材"){row{
                Text("一次选多件服饰和小物，统一确认费用，在同一批次查看主视图、分层和保存结果。",style=MaterialTheme.typography.bodySmall)
                Button(onClick=onNewMaterialBatch,modifier=Modifier.fillMaxWidth()){Text("新建素材批次")}
            }}}
            item("filters") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    ImageTaskFilter.entries.forEach { filter ->
                        FilterChip(selected = state.filter == filter, onClick = { viewModel.filter(filter) }, label = { Text(filter.label) })
                    }
                }
            }
            item("background-advice") { ImageBackgroundAdvice() }
            item("description") { Text("离开页面后继续处理。人台图与个人模特需核对后保存；重启中断的任务不会自动重新计费。", style = MaterialTheme.typography.bodySmall) }
            if (state.isLoading) item("loading") { LolitaCard { Column { ShimmerLine(0.7f, 18.dp); Spacer(Modifier.height(skin.spacingMedium)); ShimmerLine(0.9f, 14.dp) } } }
            state.error?.let { error -> item("error") { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.isLoading && state.visibleTasks.none{it.batchId==null} && state.visibleMaterialBatches.isEmpty()) item("empty") {
                LolitaSection(title = "暂无${if (state.filter == ImageTaskFilter.ALL) "" else state.filter.label}任务") { row {
                    Text("从虚拟试衣、个人模特或物品详情的人台图、小物展示图提交生成后，可在这里查看进度和结果。", Modifier.padding(top = skin.spacingMedium))
                } }
            }
            items(state.visibleMaterialBatches,key={requireNotNull(it.first().batchId)}){batch->
                LolitaSection(title=requireNotNull(batch.first().batchName)){row{
                    val count=batch.map{it.garmentId}.distinct().size
                    Text("${count}件物品 · ${batch.count{it.status.retainsSourceImages}}个排队或执行 · ${batch.count{it.status==com.lolita.app.domain.model.ImageTaskStatus.AWAITING_CONFIRMATION}}个待核对")
                    val latest=batch.groupBy{it.batchPosition}.values.map{rows->rows.maxWith(compareBy<ImageTask>{it.createdAt}.thenBy{it.id})}
                    Text("${latest.count{it.materialId!=null}}份已确认 · ${latest.count{it.status in setOf(com.lolita.app.domain.model.ImageTaskStatus.FAILED,com.lolita.app.domain.model.ImageTaskStatus.INTERRUPTED,com.lolita.app.domain.model.ImageTaskStatus.CANCELLED)}}件失败或中断",style=MaterialTheme.typography.bodySmall)
                    Text("主视图、透明层与确认素材集中处理，失败项单独重新确认。",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={onOpenMaterialBatch(requireNotNull(batch.first().batchId))},modifier=Modifier.fillMaxWidth()){Text("打开本批任务流")}
                }}
            }
            items(state.visibleTasks.filter{it.batchId==null}, key = { it.id }) { task ->
                LolitaSection(title = task.title, modifier = Modifier.skinClickable { onOpenTask(task.id) }) { row {
                    Spacer(Modifier.height(skin.spacingMedium))
                    Text("${task.displayTypeLabel} · ${task.provider.label}", style = MaterialTheme.typography.bodySmall)
                    TaskStage(task)
                    Text(formatTaskDate(task.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (if (task.sourceResourcesMissing) ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE else task.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { onOpenTask(task.id) }, modifier = Modifier.fillMaxWidth()) { Text("查看任务") }
                } }
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
            Text("已等待 ${((now - task.createdAt).coerceAtLeast(0) / 1_000)} 秒", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LatestImageTask(task: ImageTask?, onOpen: () -> Unit) {
    val skin = LolitaSkin.current
    LolitaSection(title = "最近提交的任务") { row {
        Text("已加入任务，可离开页面", modifier = Modifier.padding(top = skin.spacingMedium), style = MaterialTheme.typography.bodyMedium)
        task?.let { TaskStage(it) }
        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看任务") }
    } }
}
