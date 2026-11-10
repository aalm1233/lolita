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
internal fun SquareBrowseDetail(c: SquareCatalog, e: SquareEvent, vm: SquareViewModel, showAmounts: Boolean, snackbar: SnackbarHostState) {
    val key by vm.browseKey.collectAsState(); val choices by vm.browseChoices.collectAsState()
    val selection = remember(c, e, key, choices) { vm.currentBrowseSelection() } ?: return
    val part = selection.part; val choice = selection.choice
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val state = rememberLazyListState(); ObserveSkinScroll(state)
    val height = (LocalConfiguration.current.screenHeightDp * 0.37f).dp.coerceIn(220.dp, 330.dp)
    fun view(paths: List<String>, index: Int = 0) { if (paths.isNotEmpty()) viewer = paths.map(vm::image) to index }
    LazyColumn(Modifier.fillMaxSize().testTag("square-product"), state = state,
        contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "hero") {
            key(part.key, choice.color) {
                val reference = selection.photos.isEmpty() && selection.seriesPhotos.isNotEmpty()
                val hero = selection.photos.ifEmpty { selection.seriesPhotos }
                Column {
                    ImageFrame(Modifier.fillMaxWidth().height(if(hero.isEmpty())96.dp else height).testTag("square-product-images")) {
                        if (hero.isNotEmpty()) Box(Modifier.fillMaxSize()) {
                            ImageGalleryPager(hero.map(vm::image), { view(hero, it) }, Modifier.fillMaxSize(), part.name, contentScale = ContentScale.Fit)
                            if (reference) Surface(Modifier.align(Alignment.TopStart).padding(8.dp).testTag("square-series-reference"),
                                color = MaterialTheme.colorScheme.surface, shape = com.lolita.app.ui.theme.LolitaSkin.current.cardShape) {
                                Text("系列参考图", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkinIcon(IconKey.Image, Modifier.size(36.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("暂无单品图", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (reference) Text("未提供对应单品图，参考图不作为心愿封面。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }
        item(key = "parts") {
            SquarePartPicker(c, e, vm, part)
        }

        item(key = "choices") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${c.shops.single { it.id == e.shopId }.name} · ${e.title.substringBefore(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SquarePresentation.partName(e, part), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("square-product-title"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.kind} · ${SquarePresentation.date(e.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoTip("动态详情", listOfNotNull(e.state, e.date, e.priceNote).joinToString("\n").ifBlank { "状态与日期未知" })
                }
                if (showAmounts) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selection.onlyInCombination) "组合报价见说明" else if (selection.quote != null) squarePrice(selection.quote) else "报价未确认", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(SquarePresentation.phase(selection.phase), style = MaterialTheme.typography.bodySmall)
                        selection.priceType?.let { InfoTip("报价说明", it) }
                    }
                }
                if (selection.onlyInCombination) Text("仅组合售卖", style = MaterialTheme.typography.bodySmall)
                val group = SquarePresentation.groups(c, e).first { g -> g.members.any { it.key == part.key } }
                val colors = group.members.flatMap { SquareBrowse.colors(c, it) }.distinct()
                SquareSpecChoices("颜色", colors, if (group.members.size > 1) choice.color.ifEmpty { SquareBrowse.colors(c, part).single() } else choice.color, { color ->
                    val member = group.members.firstOrNull { color in SquareBrowse.colors(c, it) } ?: part
                    vm.selectBrowseColor(member.key, color)
                }, "square-color", allowUnset = group.members.size == 1)
                SquareSpecChoices("尺码", SquareBrowse.sizes(c, part, choice.color), choice.size, { vm.chooseBrowse(size = it) }, "square-size",
                    action = if (selection.documents.isNotEmpty()) ({ TextButton(onClick = { view(selection.documents) }) { Text(if(selection.materials.any { it.sizeTable != null }) "尺码表" else "资料图") } }) else null)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    InfoTip("快照信息", "价格与库存为采集快照。\n${e.captured?.takeIf(String::isNotBlank) ?: c.capturedAt}")
                    SquareTaobaoButton(selection.rows.map { it.first }.distinctBy { it.id }, snackbar, compact = true)
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
                        selection.materials.firstNotNullOfOrNull { it.sizeTable }?.let { SquareSizeTableView(it) }
                        val facts = selection.materials.map { it.facts }
                        squareImportantFacts(facts).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                        if (squareReadableFactLines(facts).isNotEmpty()) ExpandableInfo("完整参数与售卖说明") { SquareMaterialFacts(facts) }
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
                        Text(e.title, style = MaterialTheme.typography.bodyMedium)
                        Text(part.name, style = MaterialTheme.typography.bodyMedium)
                        listOfNotNull(e.state, e.date, e.priceNote, e.note, e.material, e.detail).distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (showAmounts) e.price?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
    tag: String, action: (@Composable () -> Unit)? = null, allowUnset: Boolean = true, unsetLabel: String = "暂不选") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            action?.invoke()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ((if (allowUnset) listOf("") else emptyList()) + options).forEach { value ->
                FilterChip(selected == value, { onSelect(value) }, modifier = Modifier.testTag("$tag:$value"), label = { Text(value.ifEmpty { unsetLabel }) })
            }
        }
    }
}

internal fun squareReadableFactLines(facts: List<String>) = facts.flatMap { it.split(Regex("\\n|[；;](?=[^；;：:]{1,10}[：:])")) }.filter { it.isNotBlank() && !it.startsWith("资料图说明") }.distinct()

internal fun squareImportantFacts(facts: List<String>) = facts.flatMap { it.split(Regex("[。；;\\n]")) }
    .map(String::trim).filter { line -> line.isNotBlank() && listOf("仅", "不含", "不附", "尾款", "另付", "另计", "易剥落", "勾丝", "未知", "未提供").any(line::contains) }.distinct()

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
