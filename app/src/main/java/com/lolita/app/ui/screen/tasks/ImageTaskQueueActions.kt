package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.LolitaSection
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import androidx.compose.ui.unit.dp

/** Shared by the entire task center and a single batch. Confirmations freeze their scope. */
@Composable
fun ImageTaskQueueActions(
    tasks: List<ImageTask>, busy: Boolean,
    onStopQueued: (List<String>) -> Unit,
    onDiscardPending: (List<String>) -> Unit
) {
    val queued = tasks.filter { it.status == ImageTaskStatus.QUEUED && !it.requestSent }
    val pending = tasks.filter { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }
    val active = tasks.count { it.status.isActive }
    var stopIds by remember { mutableStateOf<List<String>?>(null) }
    var candidates by remember { mutableStateOf<List<ImageTask>?>(null) }
    val skin = LolitaSkin.current
    if (queued.isNotEmpty() || pending.isNotEmpty()) {
        LolitaSection(title = "队列管理") { row {
            Text("${queued.size}个排队 · ${active}个执行中 · ${pending.size}个待确认", style = MaterialTheme.typography.bodyMedium)
            if (queued.isNotEmpty()) OutlinedButton(
                onClick = { stopIds = queued.map { it.id } }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("stopQueued")
            ) { Text("停止后续${queued.size}个排队任务") }
            if (pending.isNotEmpty()) OutlinedButton(
                onClick = { candidates = pending }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("discardPending")
            ) { Text("批量舍弃待确认结果") }
            Text("正在处理的任务继续；原图与已保存结果保留。", style = MaterialTheme.typography.bodySmall)
        } }
    }
    stopIds?.let { ids -> AlertDialog(
        onDismissRequest = { stopIds = null },
        title = { Text("停止后续${ids.size}个排队任务？") },
        text = { Text("仅停止本次选中的排队任务，当前正在生成的任务继续处理。确认时已开始的任务会跳过；已发送的请求可能仍会计费。停止后不会自动重发。") },
        confirmButton = { TextButton(onClick = { stopIds = null; onStopQueued(ids) }, enabled = !busy) { Text("确认停止后续") } },
        dismissButton = { TextButton(onClick = { stopIds = null }) { Text("继续等待") } }
    ) }
    candidates?.let { snapshot ->
        var selected by remember(snapshot) { mutableStateOf(emptySet<String>()) }
        var zoom by remember(snapshot) { mutableStateOf<String?>(null) }
        val scroll = rememberLazyListState()
        ObserveSkinScroll(scroll)
        AlertDialog(
            modifier = Modifier.testTag("discardPendingDialog"),
            onDismissRequest = { candidates = null },
            title = { Text("批量舍弃") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text("已选${selected.size}/${snapshot.size}项")
                TextButton(onClick = { selected = if (selected.size == snapshot.size) emptySet() else snapshot.map { it.id }.toSet() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("selectAllPending")) {
                    Text(if (selected.size == snapshot.size) "清空选择" else "全选待确认项")
                }
                LazyColumn(state = scroll, modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).testTag("pendingChoices"),
                    verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    item("discard-boundary") {
                        Text("仅移除未保存预览，保留原图、已保存资料和任务记录；状态变化项跳过。", style = MaterialTheme.typography.bodySmall)
                    }
                    items(snapshot, key = { it.id }) { task ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().toggleable(task.id in selected, enabled = !busy, role = Role.Checkbox) {
                                selected = if (it) selected + task.id else selected - task.id
                            }.testTag("pendingChoice${task.id}"), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(task.id in selected, onCheckedChange = null, enabled = !busy)
                                Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                            task.resultImagePath?.let { path ->
                                LolitaShimmerImage(model = path, contentDescription = "放大待确认结果：${task.title}", contentScale = ContentScale.Fit,
                                    circularRevealEnabled = false, modifier = Modifier.fillMaxWidth().height(110.dp).clickable { zoom = path })
                            }
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { val ids = snapshot.filter { it.id in selected }.map { it.id }; candidates = null; onDiscardPending(ids) },
                enabled = !busy && selected.isNotEmpty(), modifier = Modifier.testTag("confirmDiscardPending")) { Text("舍弃已选${selected.size}项") } },
            dismissButton = { TextButton(onClick = { candidates = null }) { Text("返回核对") } }
        )
        zoom?.let { FullScreenImageViewer(listOf(it), 0, { zoom = null }) }
    }
}
