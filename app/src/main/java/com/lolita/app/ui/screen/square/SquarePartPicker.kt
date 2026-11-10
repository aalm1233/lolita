package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.square.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquarePartPicker(c: SquareCatalog, e: SquareEvent, vm: SquareViewModel, selected: SquareBrowsePart) {
    val groups = remember(c, e) { SquarePresentation.groups(c, e) }
    val selectedIndex = groups.indexOfFirst { group -> group.members.any { it.key == selected.key } }.coerceAtLeast(0)
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(groups[selectedIndex].key) { rowState.animateScrollToItem(selectedIndex) }
    var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
    var query by rememberSaveable(e.id) { mutableStateOf("") }
    fun choose(group: SquareDisplayGroup) {
        vm.selectBrowse(group.members.firstOrNull { it.key == selected.key }?.key ?: group.key)
        expanded = false
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f).testTag("square-part-list"), state = rowState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { it.key }) { group ->
                FilterChip(group.members.any { it.key == selected.key }, { choose(group) },
                    modifier = Modifier.testTag("square-part-${group.members.first().partKey}"), label = { Text(group.label, style = MaterialTheme.typography.labelLarge) })
            }
        }
        if (groups.size > 2) TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("square-all-parts")) { Text("全部单品") }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 16.dp).imePadding()) {
            SectionHeader("全部单品")
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索单品") }, singleLine = true,
                shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth().testTag("square-part-search"))
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups.filter { it.label.contains(query, true) || it.members.any { p -> p.name.contains(query, true) } }, key = { it.key }) { group ->
                    val part = group.members.first()
                    val selection = remember(c, part) { SquareBrowse.select(c, part, SquareBrowseChoice(part.key, SquareBrowse.colors(c, part).firstOrNull().orEmpty())) }
                    LolitaCard(variant = CardVariant.COMPACT, containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().testTag("square-part-option-${group.key}").clickable { choose(group) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val cover = selection.photos.firstOrNull()
                            if (cover != null) LolitaShimmerImage(vm.image(cover), group.label, Modifier.fillMaxWidth().height(110.dp), ContentScale.Fit, circularRevealEnabled = false)
                            Text(group.label, style = MaterialTheme.typography.titleSmall)
                            val colors = group.members.flatMap { SquareBrowse.colors(c, it) }.distinct()
                            if (colors.isNotEmpty()) Text("${colors.size} 个颜色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { expanded = false }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}
