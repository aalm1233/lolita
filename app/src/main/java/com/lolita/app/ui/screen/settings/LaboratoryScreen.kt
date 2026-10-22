package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun LaboratoryEntry(onClick: () -> Unit) {
    SettingsMenuItem(
        title = "实验室",
        description = "展示图、试衣与识图",
        iconKey = IconKey.Laboratory,
        iconTint = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        modifier = Modifier.testTag("laboratoryEntry"),
        variant = CardVariant.FEATURED
    )
}

@Composable
fun LaboratoryScreen(onBack: () -> Unit, onBatch: () -> Unit, onTryOn: () -> Unit,
    onModels: () -> Unit, onWigHead: (Long) -> Unit, onRecognize: () -> Unit,
    onTasks: () -> Unit, onConfigure: () -> Unit, viewModel: LaboratoryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    var wigs by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0,0,0,0), topBar = {
        GradientTopAppBar(title = { Text("实验室") }, navigationIcon = {
            IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack, Modifier.semantics { contentDescription = "返回" }) }
        })
    }) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("laboratory"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item("batch") { LolitaCard(variant = CardVariant.FEATURED) {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    Text("批量生成展示图", style = MaterialTheme.typography.titleLarge)
                    Text("一次选择最多 30 件，确认后可作封面或用于试衣。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onBatch, modifier = Modifier.fillMaxWidth().testTag("laboratoryBatch")) { Text("选择物品") }
                }
            } }
            item("images") { LolitaSection("展示与试衣") {
                row { LaboratoryAction("虚拟试衣", "预览搭配与查看历史", IconKey.Outfit, onTryOn) }
                row { LaboratoryAction("我的模特", "用照片或身体参数创建", IconKey.Outfit, onModels) }
                row { LaboratoryAction("假发头模图", "选择假发与头模，生成佩戴预览", IconKey.Image, { wigs = true }) }
            } }
            item("services") { LolitaSection("录入与服务") {
                row { LaboratoryAction("识图录入", "从图片提取资料，核对后填入", IconKey.Camera, onRecognize) }
                row { LaboratoryAction("任务中心", "查看进度和待确认结果", IconKey.ViewAgenda, onTasks) }
                row { LaboratoryAction("API 配置", "管理生图、识图服务与密钥", IconKey.Settings, onConfigure) }
            } }
        }
    }
    if (wigs) {
        val scroll = rememberLazyListState()
        ObserveSkinScroll(scroll)
        val choices = state.wigs.filter { it.name.contains(query.trim(), true) }
        AlertDialog(onDismissRequest = { wigs = false }, title = { Text("选择假发") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索假发") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("laboratoryWigSearch"))
                LazyColumn(state = scroll, modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp).testTag("laboratoryWigs")) {
                    if (state.loading) item { ShimmerLine(widthFraction = .85f, height = 24.dp) }
                    state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                    if (!state.loading && state.error == null && choices.isEmpty()) item {
                        Text(if (state.wigs.isEmpty()) "请先录入全头假发、核对类型并添加参考图片。发片继续按单品整理。" else "没有匹配的假发，请调整搜索。")
                    }
                    items(choices, key = { it.id }) { item -> TextButton(onClick = { wigs = false; onWigHead(item.id) }, modifier = Modifier.fillMaxWidth()) { Text(item.name) } }
                }
            }
        }, confirmButton = { TextButton(onClick = { wigs = false }) { Text("返回") } })
    }
}

@Composable
private fun LaboratoryAction(title: String, description: String, icon: IconKey, onClick: () -> Unit) {
    val skin = LolitaSkin.current
    Row(Modifier.fillMaxWidth().skinClickable(onClick = onClick).padding(vertical = skin.spacingMedium),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
        SkinIcon(icon, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SkinIcon(IconKey.KeyboardArrowRight, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
