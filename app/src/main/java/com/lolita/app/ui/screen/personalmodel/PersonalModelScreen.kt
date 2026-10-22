package com.lolita.app.ui.screen.personalmodel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.domain.model.PersonalModelMode
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun PersonalModelScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onAdjust: (Long) -> Unit,
    viewModel: PersonalModelViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val skin = LolitaSkin.current
    val scroll = rememberLazyListState()
    ObserveSkinScroll(scroll)
    var zoom by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<PersonalModel?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<PersonalModel?>(null) }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        GradientTopAppBar(title = { Text("我的模特") }, navigationIcon = {
            IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack, modifier = Modifier.semantics { contentDescription = "返回" }) }
        })
    }) { padding ->
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("personalModelLibrary"),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap)) {
            item("intro") {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    Text("试穿，像自己一点", style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (state.isLoading) item("loading") {
                LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    ShimmerRect(width = 160.dp, height = 240.dp, modifier = Modifier.fillMaxWidth())
                    ShimmerLine(widthFraction = .6f, height = 20.dp)
                } }
            }
            item("builtin") {
                LolitaSection(title = "内置模特") { row {
                    Row(Modifier.fillMaxWidth().padding(top = skin.spacingMedium), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                        SkinIcon(IconKey.Outfit, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (state.defaultId == null) "当前试衣模特" else "内置虚构模特", style = MaterialTheme.typography.titleMedium)
                            Text("使用内置虚拟模特，无需个人照片。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { viewModel.choose(null) }, enabled = !state.isLoading && !state.mutating && state.defaultId != null,
                        modifier = Modifier.fillMaxWidth()) { Text(if (state.defaultId == null) "正在使用内置模特" else "使用内置模特") }
                } }
            }
            item("create") { Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("创建模特") } }
            state.error?.let { error -> item("error") { Text(error, color = MaterialTheme.colorScheme.error) } }
            itemsIndexed(state.models, key = { _, model -> model.id }) { _, model ->
                LolitaSection(title = model.name) { row {
                    ImageFrame(Modifier.fillMaxWidth().padding(top = skin.spacingMedium)) {
                        LolitaShimmerImage(model = model.imageUrl, contentDescription = "放大${model.name}", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).aspectRatio(2f / 3f)
                                .skinClickable { zoom = model.imageUrl }, circularRevealEnabled = false)
                    }
                    val request = remember(model.requestJson) { runCatching { model.request() }.getOrNull() }
                    request?.let {
                        Text("${it.measurements.heightCm} cm · ${it.measurements.weightKg} kg · ${if (it.mode == PersonalModelMode.PHOTO) "照片生成" else "参数生成"}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = skin.spacingSmall))
                    }
                    Button(onClick = { viewModel.choose(model.id) }, enabled = !state.mutating && state.defaultId != model.id,
                        modifier = Modifier.fillMaxWidth()) { Text(if (state.defaultId == model.id) "当前试衣模特" else "设为试衣模特") }
                    // FlowRow keeps every action reachable at 320dp and large font sizes.
                    ModelActions(onAdjust = { onAdjust(model.id) }, onRename = { name = model.name; renaming = model },
                        onDelete = { deleting = model }, enabled = !state.mutating)
                } }
            }
            item("hint") { Text("选好的模特会用于衣橱「虚拟试衣」。调整参数会另存一位模特，历史试衣记录继续保留。", style = MaterialTheme.typography.bodySmall) }
        }
    }
    zoom?.let { path -> FullScreenImageViewer(listOf(path), onDismiss = { zoom = null }) }
    renaming?.let { model -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("重命名模特") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（1至60字）") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { viewModel.rename(model.id, name); renaming = null }, enabled = name.trim().isNotBlank() && name.trim().length <= 60) { Text("保存") } },
        dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }) }
    deleting?.let { model -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除「${model.name}」？") },
        text = { Text("从模特库移除后，历史试衣与已提交任务继续保留。") },
        confirmButton = { TextButton(onClick = { viewModel.delete(model.id); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelActions(onAdjust: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, enabled: Boolean) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LolitaSkin.current.spacingSmall)) {
        TextButton(onClick = onAdjust, enabled = enabled) { Text("调整参数") }
        TextButton(onClick = onRename, enabled = enabled) { Text("重命名") }
        TextButton(onClick = onDelete, enabled = enabled) { Text("删除") }
    }
}
