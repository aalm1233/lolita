package com.lolita.app.ui.screen.look

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.rememberSkinFlingBehavior
import com.lolita.app.ui.theme.skin.icon.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookBuilderScreen(itemId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit, onItem: (Long) -> Unit,
    viewModel: LookBuilderViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    var picker by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var name by rememberSaveable(itemId) { mutableStateOf("") }
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { GradientTopAppBar(title = { Text("整套搭配") }, navigationIcon = {
            IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) }
        }) },
        bottomBar = {
            if (!state.loading && state.target != null) {
                Surface { Row(Modifier.fillMaxWidth().padding(skin.spacingLarge), horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    OutlinedButton(onClick = viewModel::shuffle, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("换一套") }
                    Button(onClick = { saving = true }, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                        Text(if (state.saving) "保存中…" else "保存为套装")
                    }
                } }
            }
        }
    ) { padding ->
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.cardGap),
            flingBehavior = rememberSkinFlingBehavior()) {
            if (state.loading) {
                items(4) { LolitaCard { Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    ShimmerLine(widthFraction = 0.5f, height = 20.dp)
                    ShimmerRect(width = 80.dp, height = 80.dp)
                } } }
            } else {
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.load(itemId, force = true) }, enabled = !state.saving) { Text("刷新搭配") }
                } }
                state.target?.let { target -> item { LolitaSection(title = "固定单品") { row { LookItem(target) { onItem(target.id) } } } } }
                item { Text("锁定喜欢的单品，再换一套；不需要的类型可以跳过。", style = MaterialTheme.typography.bodyMedium) }
                if (state.groups.isEmpty() && state.target != null) item { Text("暂时没有可补齐的服饰，也可以把主件保存为套装。") }
                state.groups.forEach { (slot, options) ->
                    val selected = options.firstOrNull { it.item.id == state.selection.selected[slot] }
                    item(key = slot) { LolitaSection(title = slot, action = {
                        TextButton(onClick = { picker = slot }, enabled = !state.saving) { Text("选择") }
                    }) { row {
                        if (selected == null) Text("已跳过") else {
                            LookItem(selected.item) { onItem(selected.item.id) }
                            Text(selected.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = slot in state.selection.locked, onCheckedChange = { viewModel.lock(slot) }, enabled = !state.saving)
                                Text("锁定这件")
                                TextButton(onClick = { viewModel.choose(slot, null) }, enabled = !state.saving) { Text("跳过") }
                            }
                        }
                    } } }
                }
            }
        }
    }
    picker?.let { slot ->
        var query by remember(slot) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picker = null }, title = { Text("选择$slot") }, text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索名称") }, singleLine = true)
                val pickerList = rememberLazyListState()
                ObserveSkinScroll(pickerList)
                LazyColumn(state = pickerList, modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    item { TextButton(onClick = { viewModel.choose(slot, null); picker = null }) { Text("跳过这个分类") } }
                    state.groups[slot].orEmpty().filter { it.item.name.contains(query.trim(), true) }.forEach { option ->
                        item(key = option.item.id) { LookItem(option.item) { viewModel.choose(slot, option.item.id); picker = null } }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = null }) { Text("关闭") } })
    }
    if (saving) AlertDialog(onDismissRequest = { if (!state.saving) saving = false }, title = { Text("保存为套装") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("套装名称") }, singleLine = true, enabled = !state.saving)
            val moved = state.items.filter { it.coordinateId != null }
            if (moved.isNotEmpty()) Text("以下服饰已属于其他套装，保存会将它们移入新套装：${moved.joinToString("、") { it.name }}。原套装会保留。")
            else Text("保存 ${state.items.size} 件服饰为新套装。")
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { saving = false; viewModel.load(itemId, force = true) }, enabled = !state.saving) { Text("刷新后重新选择") }
            }
        }
    }, confirmButton = { TextButton(onClick = { viewModel.save(name) }, enabled = name.isNotBlank() && !state.saving) { Text("确认保存") } },
        dismissButton = { TextButton(onClick = { saving = false }, enabled = !state.saving) { Text("取消") } })
}

@Composable
private fun LookItem(item: Item, onClick: () -> Unit) {
    val skin = LolitaSkin.current
    LolitaCard(onClick = onClick, variant = CardVariant.COMPACT, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            LolitaShimmerImage(model = item.imageUrls.firstOrNull(), contentDescription = item.name,
                placeholderInitial = item.name.firstOrNull()?.toString(), contentScale = ContentScale.Fit,
                modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(skin.cornerRadiusSmall)))
            Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
