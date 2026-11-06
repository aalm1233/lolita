package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.square.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SquareWishSheet(c: SquareCatalog, vm: SquareViewModel) {
    val json by vm.multiChoices.collectAsState(); val grouped by vm.multiGrouped.collectAsState(); val busy by vm.busy.collectAsState()
    val choices = remember(json) { vm.selectedWishes() }
    val parts = remember(c, vm.eventId.value) { vm.browseParts() }
    var editing by rememberSaveable { mutableStateOf(choices.firstOrNull()?.key) }
    val selected = choices.firstOrNull { it.key == editing } ?: choices.firstOrNull()
    val part = parts.firstOrNull { it.key == selected?.key }
    ModalBottomSheet(onDismissRequest = vm::closeMulti, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = if (busy == null) ({ BottomSheetDefaults.DragHandle() }) else null) {
        Column(Modifier.fillMaxWidth().testTag("square-wish-sheet")) {
            LazyColumn(Modifier.testTag("square-wish-options").weight(1f, fill = false).heightIn(max = (LocalConfiguration.current.screenHeightDp * .65f).dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionHeader("加入心愿") }
                items(parts, key = { it.key }) { option ->
                    val choice = choices.firstOrNull { it.key == option.key }
                    val cover = SquareBrowse.select(c, option, choice ?: SquareBrowseChoice(option.key, SquareBrowse.colors(c, option).firstOrNull().orEmpty())).photos.firstOrNull()
                    Row(Modifier.fillMaxWidth().testTag("square-wish-part-${option.partKey}").clickable(enabled = busy == null) { vm.toggleWish(option.key) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(choice != null, { vm.toggleWish(option.key) }, enabled = busy == null)
                        cover?.let { LolitaShimmerImage(vm.image(it), option.name, Modifier.size(46.dp, 58.dp), ContentScale.Fit, circularRevealEnabled = false) }
                        Text(option.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    }
                }
                if (choices.size > 1) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(!grouped, { vm.groupWishes(false) }, enabled = busy == null, label = { Text("分别加入") })
                        FilterChip(grouped, { vm.groupWishes(true) }, enabled = busy == null, label = { Text("合为一件") })
                        InfoTip("组合方式", "个人组合，不代表商家套餐；各件规格独立，不生成组合价。")
                    }
                }
                if (part != null && selected != null) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (choices.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { choice -> FilterChip(selected.key == choice.key, { editing = choice.key }, label = { Text(parts.first { it.key == choice.key }.name) }) }
                        }
                        SquareSpecChoices("${part.name} · 颜色", SquareBrowse.colors(c, part), selected.color,
                            { vm.changeWish(part.key, color = it) }, "square-wish-color")
                        SquareSpecChoices("${part.name} · 尺码", SquareBrowse.sizes(c, part, selected.color), selected.size,
                            { vm.changeWish(part.key, size = it) }, "square-wish-size")
                    }
                }
            }
            Button(onClick = { vm.saveWishes(multiple = true) }, enabled = choices.isNotEmpty() && busy == null,
                shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp).testTag("square-save-wishes")) {
                Text(if (grouped && choices.size > 1) "合为 1 件加入心愿" else if (choices.size > 1) "加入心愿（${choices.size} 件）" else "加入心愿")
            }
        }
    }
}

@Composable
internal fun SquareWishDock(vm: SquareViewModel, onOpenItem: (Long) -> Unit, enabled: Boolean) {
    val wardrobe by vm.wardrobe.collectAsState(); val choices by vm.browseChoices.collectAsState(); val key by vm.browseKey.collectAsState()
    val match = remember(wardrobe, choices, key, vm.eventId.value) { vm.currentWishMatch() }
    val exact = wardrobe.items.firstOrNull { it.id in match.exact }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::openMulti, enabled = enabled, shape = LolitaSkin.current.cardShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) { Text("选多件") }
            Button(onClick = { if (exact != null) onOpenItem(exact.id) else vm.saveWishes() }, enabled = enabled,
                shape = LolitaSkin.current.cardShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("square-add-wish").semantics { liveRegion=LiveRegionMode.Polite }) {
                Text(if (exact != null) if (exact.status == ItemStatus.WISHED) "已在心愿 · 查看" else "已登记 · 查看" else if(match.related.isNotEmpty())"已有相关记录 · 查看" else "加入心愿")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquareWishResultSheet(vm: SquareViewModel, onOpenItem: (Long) -> Unit, onEditDraft: (String) -> Unit, onOpenWishlist: () -> Unit) {
    val result by vm.wishResult.collectAsState(); val wardrobe by vm.wardrobe.collectAsState()
    val r = result ?: return
    if (r.related || r.draftIds.isNotEmpty()) AlertDialog(onDismissRequest = vm::clearWishResult,
        title = { Text(if (r.draftIds.isNotEmpty()) "已有整理草稿" else "已有相关记录") }, text = {
            Column {
                r.itemIds.forEach { id -> TextButton(onClick = { vm.clearWishResult(); onOpenItem(id) }) { Text(wardrobe.items.firstOrNull { it.id == id }?.name ?: "查看物品") } }
                r.draftIds.forEach { id -> TextButton(onClick = { vm.clearWishResult(); onEditDraft(id) }) { Text(wardrobe.drafts.firstOrNull { it.id == id }?.let { runCatching { it.data().name }.getOrNull() } ?: "继续整理") } }
            }
        }, confirmButton = { if (r.related) TextButton(onClick = { vm.clearWishResult(); vm.saveWishes(confirmRelated = true) }) { Text("另存心愿") } },
        dismissButton = { TextButton(onClick = vm::clearWishResult) { Text("取消") } })
    else if (vm.wishWasMultiple()) ModalBottomSheet(onDismissRequest = vm::clearWishResult, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(if (r.added > 0) "已加入心愿" else if(wardrobe.items.filter { it.id in r.itemIds }.all { it.status==ItemStatus.WISHED })"已在心愿" else "已登记")
            LazyColumn(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .45f).dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(wardrobe.items.filter { it.id in r.itemIds }, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().clickable { vm.clearWishResult(); onOpenItem(item.id) }, verticalAlignment = Alignment.CenterVertically) {
                        item.imageUrls.firstOrNull()?.let { LolitaShimmerImage(it, item.name, Modifier.size(64.dp, 86.dp), ContentScale.Fit, circularRevealEnabled = false) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            val source = wardrobe.sources.firstOrNull { it.itemId == item.id }
                            val snapshot = source?.let { runCatching { SquareWishSnapshot.decode(it.snapshotJson, it.imagePaths.size) }.getOrNull() }
                            if (snapshot?.grouped == true) snapshot.members.forEach { member ->
                                Text("${member.name} · ${member.color.ifEmpty { "颜色未选" }} · ${member.size.ifEmpty { "尺码未选" }}", style = MaterialTheme.typography.bodySmall)
                            } else Text("${item.colors.joinToString().ifEmpty { "颜色未选" }} · ${item.size ?: "尺码未选"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = vm::clearWishResult, shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth()) { Text("继续浏览") }
            OutlinedButton(onClick = { vm.clearWishResult(); onOpenWishlist() }, shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth()) { Text("查看心愿") }
        }
    }
}
