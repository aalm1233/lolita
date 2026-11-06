package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.square.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.component.ImageGalleryPager
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
internal fun SquareBrowseDetail(c: SquareCatalog, e: SquareEvent, vm: SquareViewModel, showAmounts: Boolean) {
    val key by vm.browseKey.collectAsState(); val choices by vm.browseChoices.collectAsState()
    val selection = remember(c, e, key, choices) { vm.currentBrowseSelection() } ?: return
    val parts = remember(c, e) { SquareBrowse.parts(c, e) }
    val part = selection.part; val choice = selection.choice
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val state = rememberLazyListState(); ObserveSkinScroll(state)
    val height = (LocalConfiguration.current.screenHeightDp * 0.37f).dp.coerceIn(220.dp, 330.dp)
    fun view(paths: List<String>, index: Int = 0) { if (paths.isNotEmpty()) viewer = paths.map(vm::image) to index }
    LazyColumn(Modifier.fillMaxSize().testTag("square-product"), state = state,
        contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "hero") {
            key(part.key, choice.color) {
                ImageFrame(Modifier.fillMaxWidth().height(if(selection.photos.isEmpty())96.dp else height).testTag("square-product-images")) {
                    if (selection.photos.isNotEmpty()) ImageGalleryPager(selection.photos.map(vm::image), { view(selection.photos, it) },
                        Modifier.fillMaxSize(), part.name, contentScale = ContentScale.Fit)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkinIcon(IconKey.Image, Modifier.size(36.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("暂无单品图", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item(key = "parts") {
            LazyRow(modifier = Modifier.testTag("square-part-list"), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parts, key = { it.key }) { option ->
                    val optionChoice = if (option.key == choice.key) choice else SquareBrowseChoice(option.key, SquareBrowse.colors(c, option).firstOrNull().orEmpty())
                    val cover = remember(c, option, optionChoice) { SquareBrowse.select(c, option, optionChoice).photos.firstOrNull() }
                    FilterChip(selected = part.key == option.key, onClick = { vm.selectBrowse(option.key) },
                        modifier = Modifier.testTag("square-part-${option.partKey}"),
                        leadingIcon = cover?.let { path -> { LolitaShimmerImage(vm.image(path), option.name, Modifier.size(36.dp, 46.dp), contentScale = ContentScale.Fit, circularRevealEnabled = false) } },
                        label = { Text(option.name, style = MaterialTheme.typography.labelLarge) })
                }
            }
        }
        item(key = "choices") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(c.shops.single { it.id == e.shopId }.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${e.title} · ${part.name}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("square-product-title"))
                Text("${e.kind} · ${e.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (showAmounts) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selection.quote != null) squarePrice(selection.quote) else "报价未确认", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        selection.priceType?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
                    }
                }
                if (selection.onlyInCombination) Text("仅组合售卖", style = MaterialTheme.typography.bodySmall)
                SquareSpecChoices("颜色", SquareBrowse.colors(c, part), choice.color, { vm.chooseBrowse(color = it) }, "square-color")
                SquareSpecChoices("尺码", SquareBrowse.sizes(c, part, choice.color), choice.size, { vm.chooseBrowse(size = it) }, "square-size",
                    action = if (selection.documents.isNotEmpty()) ({ TextButton(onClick = { view(selection.documents) }) { Text(if(selection.materials.any { it.sizeTable != null }) "尺码表" else "资料图") } }) else null)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.date} · ${e.captured} 快照", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    InfoTip("快照信息", "价格与库存为采集快照。")
                }
            }
        }
        item(key = "parameters") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                LolitaSection("参数", modifier = Modifier.testTag("square-parameters"), action = {
                    if (selection.documents.isNotEmpty()) TextButton(onClick = { view(selection.documents) }) { Text("原图") }
                }) {
                    row {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("内容", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
                            Text(part.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (selection.onlyInCombination) InfoTip("购买内容", "当前收藏的是本部件；商家仅提供组合售卖。组合报价不作为本件全款。")
                        }
                        SquareMaterialFacts(selection.materials.map { it.facts })
                        selection.materials.firstNotNullOfOrNull { it.sizeTable }?.let { SquareSizeTableView(it) }
                        if (squareReadableFactLines(selection.materials.map { it.facts }).isEmpty() && selection.materials.none { it.sizeTable != null }) Text("参数未提供", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        if (selection.seriesPhotos.isNotEmpty()) item(key = "series") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                LolitaSection("系列图", action = { InfoTip("系列图内容", "系列图包含其他搭配物品。") }) {
                    row {
                        ImageGalleryPager(selection.seriesPhotos.map(vm::image), { view(selection.seriesPhotos, it) },
                            Modifier.fillMaxWidth().height(320.dp), "${e.title}系列图", contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        if (selection.documents.isNotEmpty()) item(key = "documents") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                LolitaSection("资料原图") {
                    selection.documents.forEachIndexed { index, path -> row {
                        val asset = c.assets.first { it.path == path }
                        LolitaShimmerImage(vm.image(path), "${part.name}资料${index + 1}", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height((360f * asset.height / asset.width).dp.coerceIn(180.dp, 520.dp)).clickable { view(selection.documents, index) }, circularRevealEnabled = false)
                    } }
                }
            }
        }
        item(key = "source") {
            Box(Modifier.padding(horizontal = 14.dp)) {
                LolitaSection("来源") { row {
                    ExpandableInfo("${c.shops.single { it.id == e.shopId }.name} · ${selection.rows.map { it.first.capturedAt }.distinct().joinToString(" / ")}") {
                        selection.rows.map { it.first }.distinctBy { it.id }.forEach { p ->
                            Text(p.url, style = MaterialTheme.typography.bodySmall)
                            Text(p.priceType, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } }
            }
        }
    }
    viewer?.let { (paths, index) -> FullScreenImageViewer(paths, index) { viewer = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SquareSpecChoices(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit,
    tag: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            action?.invoke()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("") + options).forEach { value ->
                FilterChip(selected == value, { onSelect(value) }, modifier = Modifier.testTag("$tag:$value"), label = { Text(value.ifEmpty { "暂不选" }) })
            }
        }
    }
}

internal fun squareReadableFactLines(facts: List<String>) = facts.flatMap { it.lines() }.filter { it.isNotBlank() && !it.startsWith("资料图说明") }.distinct()

@Composable
internal fun SquareMaterialFacts(facts: List<String>) {
    squareReadableFactLines(facts).forEach { line ->
        val label = line.substringBefore('：', "")
        val value = if (label.isNotEmpty()) line.substringAfter('：') else line
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (label.isNotEmpty()) Text(when (label) { "材质" -> "面料"; "版型与细节" -> "款式"; else -> label }, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun SquareSizeTableView(table: SquareSizeTable) {
    val large = LocalConfiguration.current.fontScale >= 1.5f
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (table.note.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { InfoTip("尺码表口径", table.note) }
        if (large) table.rows.forEach { row ->
            Text(row.size, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            row.values.forEachIndexed { index, value -> Text("${table.columns[index]}　$value", style = MaterialTheme.typography.bodyLarge) }
            HorizontalDivider(Modifier.padding(top = 10.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("尺码", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                table.columns.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
            }
            table.rows.forEach { row ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.size, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    row.values.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
