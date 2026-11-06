package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import com.lolita.app.data.square.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.SkinTabIndicator
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SquareBrowseFeed(c: SquareCatalog, vm: SquareViewModel, showAmounts: Boolean) {
    val query by vm.query.collectAsState(); val kind by vm.kind.collectAsState(); val shop by vm.shop.collectAsState()
    val color by vm.color.collectAsState(); val style by vm.style.collectAsState(); val form by vm.form.collectAsState()
    val imageEpoch by vm.imageEpoch.collectAsState()
    var filters by rememberSaveable { mutableStateOf(false) }; var shops by rememberSaveable { mutableStateOf(false) }
    var shopQuery by rememberSaveable { mutableStateOf("") }
    val visibleEvents = remember(c) { SquareVisibility.events(c) }
    val products = remember(c) { visibleEvents.flatMap { SquareVisibility.products(c, it) }.distinctBy { it.id }.associateBy { it.id } }
    val skus = remember(c) { products.values.flatMap { it.skus } }
    val events = remember(c, query, kind, shop, color, style, form) {
        visibleEvents.filter { e -> (kind.isEmpty() || e.kind == kind) && (shop.isEmpty() || e.shopId == shop) &&
            (query.isBlank() || (e.title + e.subtitle + c.shops.find { it.id == e.shopId }?.name + e.productIds.mapNotNull(products::get).joinToString { it.title }).contains(query, true)) &&
            e.productIds.any { id -> products[id]?.skus?.any { it.matches(color, style, form) } == true } }
    }
    val large = LocalConfiguration.current.fontScale >= 1.5f
    val state = rememberLazyGridState(); ObserveSkinScroll(state)
    LazyVerticalGrid(columns = GridCells.Fixed(if (large) 1 else 2), state = state,
        modifier = Modifier.fillMaxSize().testTag("square-feed"), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "controls") {
            Column {
                OutlinedTextField(query, { vm.set("query", it) }, singleLine = true, placeholder = { Text("搜索商品或店铺") },
                    leadingIcon = { SkinIcon(IconKey.Search, Modifier.size(20.dp)) },
                    shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth().testTag("square-search"))
                val kinds = listOf("全部", "上新", "再贩", "现货", "尾款")
                val selected = kinds.indexOf(kind.ifEmpty { "全部" }).coerceAtLeast(0)
                val tabs: @Composable () -> Unit = {
                    kinds.forEachIndexed { index, label -> Tab(selected == index, { vm.set("kind", if (index == 0) "" else label) }, text = { Text(label, style = MaterialTheme.typography.labelLarge) }) }
                }
                if (large) ScrollableTabRow(selected, edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    indicator = { SkinTabIndicator(it, selected) }, tabs = tabs)
                else TabRow(selected, containerColor = androidx.compose.ui.graphics.Color.Transparent, indicator = { SkinTabIndicator(it, selected) }, tabs = tabs)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { shops = true }) { Text(c.shops.find { it.id == shop }?.name ?: "全部店铺"); SkinIcon(IconKey.ArrowDropDown, Modifier.size(18.dp)) }
                    TextButton(onClick = { filters = true }) { Text("筛选" + if (color + style + form != "") " · 已选" else ""); SkinIcon(IconKey.FilterList, Modifier.size(18.dp)) }
                }
            }
        }
        if (events.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LolitaCard { Text("暂无商品"); TextButton(onClick = { listOf("query", "kind", "shop", "color", "style", "form").forEach { vm.set(it, "") } }) { Text("清除筛选") } }
        }
        items(events, key = { it.id }) { event ->
            val product = SquareVisibility.products(c, event).let { candidates -> candidates.firstOrNull { it.pricePhase == "FULL" } ?: candidates.first() }
            val cover = product.images.filter { it.role == "main" }.minBy { it.order }
            LolitaCard(variant = CardVariant.GALLERY, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth().testTag("square-event-${event.id}").clickable { vm.open(event.id) }) {
                Column {
                    Box {
                        LolitaShimmerImage(vm.thumbnail(cover.path,imageEpoch), event.title, Modifier.fillMaxWidth().aspectRatio(0.8f), ContentScale.Fit, circularRevealEnabled = false)
                        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .93f), shape = LolitaSkin.current.cardShape) {
                            Text(event.kind, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(c.shops.first { it.id == event.shopId }.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(event.title, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(event.state,event.date.takeUnless { it.contains("未提供") || it.contains("未知") }.orEmpty()).filter{it.isNotBlank()}.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showAmounts && SquareVisibility.products(c,event).size == event.productIds.size) { Text(event.price, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (event.priceNote.isNotBlank()) Text(event.priceNote, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .68f).dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("筛选") }
            item { SquareSpecChoices("颜色", skus.mapNotNull { it.color }.distinct(), color, { vm.set("color", it) }, "square-filter-color") }
            item { SquareSpecChoices("风格", skus.flatMap { it.styleFacts }.map { it.value }.distinct(), style, { vm.set("style", it) }, "square-filter-style") }
            item { SquareSpecChoices("内容", listOf("单品", "小物", "套装", "组合"), form, { vm.set("form", it) }, "square-filter-form") }
        }
        Button(onClick = { filters = false }, shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("完成") }
    }
    if (shops) AlertDialog(onDismissRequest = { shops = false }, title = { Text("店铺") }, text = {
        Column {
            OutlinedTextField(shopQuery, { shopQuery = it }, placeholder = { Text("搜索店铺") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { TextButton(onClick = { vm.set("shop", ""); shops = false }) { Text("全部店铺") } }
                items(c.shops.filter { s -> visibleEvents.any { it.shopId == s.id } && s.name.contains(shopQuery, true) }) { s -> TextButton(onClick = { vm.set("shop", s.id); shops = false }) { Text(s.name) } }
            }
        }
    }, confirmButton = { TextButton(onClick = { shops = false }) { Text("关闭") } })
}
