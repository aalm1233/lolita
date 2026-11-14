package com.lolita.app.ui.screen.square

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lolita.app.data.square.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.SearchModeBar
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.animation.SkinTabIndicator
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SquareBrowseFeed(c: SquareCatalog, vm: SquareViewModel, showAmounts: Boolean) {
    val query by vm.query.collectAsState(); val kind by vm.kind.collectAsState(); val shop by vm.shop.collectAsState()
    val color by vm.color.collectAsState(); val style by vm.style.collectAsState(); val form by vm.form.collectAsState()
    val imageEpoch by vm.imageEpoch.collectAsState()
    var filters by rememberSaveable { mutableStateOf(false) }; var shops by rememberSaveable { mutableStateOf(false) }
    var shopQuery by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        vm.set("query", "")
        searching = false
    }
    BackHandler(searching && !filters && !shops) { closeSearch() }
    val visibleEvents = remember(c) { SquareVisibility.events(c) }
    val filter = remember(query, kind, shop, color, style, form) { SquareBrowseFilter(query, kind, shop, color, style, form) }
    val events = remember(c, filter) { filter.events(c) }
    val activeFilterCount = listOf(shop, color, style, form).count { it.isNotEmpty() }
    val selectedShopName = c.shops.find { it.id == shop }?.name ?: shop
    val large = LocalConfiguration.current.fontScale >= 1.5f
    val state = rememberLazyGridState(); ObserveSkinScroll(state)
    LazyVerticalGrid(columns = GridCells.Fixed(if (large) 1 else 2), state = state,
        modifier = Modifier.fillMaxSize().testTag("square-feed"), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "controls") {
            Column {
                val kinds = listOf("上新", "再贩", "现货", "尾款")
                val selected = kinds.indexOf(kind).coerceAtLeast(0)
                val tabs: @Composable () -> Unit = {
                    kinds.forEachIndexed { index, label ->
                        Tab(selected == index, { vm.set("kind", label) },
                            modifier = Modifier.heightIn(min = 34.dp).testTag("square-kind-$label"),
                            selectedContentColor = LocalContentColor.current,
                            unselectedContentColor = LocalContentColor.current.copy(alpha = 0.7f)) {
                            Text(label, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp).width(IntrinsicSize.Max),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Medium,
                                textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("square-controls")
                    .then(if (searching) Modifier else Modifier.padding(vertical = 2.dp)), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        Box(Modifier.weight(1f)) {
                            SearchModeBar(query, { vm.set("query", it) }, ::closeSearch, searchFocus,
                                placeholder = "搜索商品或店铺", inputModifier = Modifier.testTag("square-search"))
                        }
                        LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    } else {
                        IconButton(onClick = { searching = true }, modifier = Modifier.size(32.dp).testTag("square-search-open").semantics { contentDescription = "搜索商品或店铺" }) {
                            SkinIcon(IconKey.Search)
                        }
                        if (large) ScrollableTabRow(selected, modifier = Modifier.weight(1f), edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            divider = {}, indicator = { SkinTabIndicator(it, selected) }, tabs = tabs)
                        else TabRow(selected, modifier = Modifier.weight(1f), containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            divider = {}, indicator = { SkinTabIndicator(it, selected) }, tabs = tabs)
                    }
                    BadgedBox(badge = {
                        if (activeFilterCount > 0) Badge { Text("$activeFilterCount") }
                    }) {
                        IconButton(onClick = { filters = true }, modifier = Modifier.size(32.dp)
                            .testTag("square-filter-button").semantics {
                                contentDescription = if (activeFilterCount > 0) "筛选，已选${activeFilterCount}项" else "筛选"
                            }) { SkinIcon(IconKey.FilterList) }
                    }
                }
                if (activeFilterCount > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shop.isNotEmpty()) InputChip(selected = true, onClick = { vm.set("shop", "") },
                        modifier = Modifier.testTag("square-shop-chip"), label = { Text(selectedShopName) },
                        trailingIcon = { SkinIcon(IconKey.Close, Modifier.size(16.dp)) })
                    listOf("color" to color, "style" to style, "form" to form).filter { it.second.isNotEmpty() }.forEach { (key, value) ->
                        InputChip(selected = true, onClick = { vm.set(key, "") }, label = { Text(value) }, trailingIcon = { SkinIcon(IconKey.Close, Modifier.size(16.dp)) })
                    }
                }
            }
        }
        if (events.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LolitaCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("暂无商品")
                    TextButton(enabled = color + style + form != "", onClick = { listOf("color", "style", "form").forEach { vm.set(it, "") } }) { Text("清除属性筛选") }
                    if (shop.isNotEmpty()) TextButton(onClick = { vm.set("shop", "") }) { Text("清除店铺筛选") }
                    if (query.isNotBlank()) TextButton(onClick = { vm.set("query", "") }) { Text("清除搜索") }
                }
            }
        }
        items(events, key = { it.id }) { event ->
            val opening = remember(c, event, filter) { filter.opening(c, event) }
            val part = remember(c, event, opening) { SquareBrowse.parts(c, event).find { it.key == opening?.key } }
            val selection = remember(c, part, opening) { if (part != null && opening != null) SquareBrowse.select(c, part, opening) else null }
            val matches = filter.rows(c, event)
            val product = matches.firstOrNull { it.first.pricePhase == "FULL" }?.first ?: matches.first().first
            val exactCover = selection?.photos?.firstOrNull()
            val cover = exactCover ?: product.images.filter { it.role == "main" }.minBy { it.order }.path
            val filtered = color.isNotEmpty() || style.isNotEmpty() || form.isNotEmpty()
            LolitaCard(variant = CardVariant.GALLERY, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth().testTag("square-event-${event.id}").clickable { vm.open(event.id) }) {
                Column {
                    Box {
                        LolitaShimmerImage(vm.thumbnail(cover,imageEpoch), event.title, Modifier.fillMaxWidth().aspectRatio(0.8f), ContentScale.Fit, circularRevealEnabled = false)
                        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .93f), shape = LolitaSkin.current.cardShape) {
                            Text(SquareEventPresentation.badge(event), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(c.shops.first { it.id == event.shopId }.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (filtered && part != null) Text(SquarePresentation.partName(event, part) + if (exactCover == null) " · 系列参考图" else "", style = MaterialTheme.typography.bodySmall)
                        Text(SquareEventPresentation.time(event, c.capturedAt).summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showAmounts) Text(SquarePresentation.price(matches),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).imePadding().testTag("square-filter-sheet")) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionHeader("筛选") }
                item { Text(kind, style = MaterialTheme.typography.bodySmall) }
                item {
                    TextButton(onClick = { shops = true }, modifier = Modifier.fillMaxWidth().testTag("square-filter-shop")) {
                        Text("店铺", color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text(if (shop.isEmpty()) "全部店铺" else selectedShopName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        SkinIcon(IconKey.ArrowDropDown, Modifier.size(18.dp))
                    }
                }
                item { SquareSpecChoices("内容", filter.candidates(c, "form"), form, { vm.set("form", it) }, "square-filter-form", unsetLabel = "不限") }
                item { SquareFacetChoices("颜色", filter.candidates(c, "color"), color, { vm.set("color", it) }, "square-filter-color") }
                item { SquareFacetChoices("风格", filter.candidates(c, "style"), style, { vm.set("style", it) }, "square-filter-style") }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { listOf("color", "style", "form").forEach { vm.set(it, "") } }, shape = LolitaSkin.current.cardShape) { Text("清除属性") }
                Button(onClick = { filters = false }, shape = LolitaSkin.current.cardShape, modifier = Modifier.weight(1f).testTag("square-filter-done")) { Text("完成（${events.size}）") }
            }
        }
    }
    if (shops) AlertDialog(onDismissRequest = { shops = false }, title = { Text("店铺") }, text = {
        Column {
            OutlinedTextField(shopQuery, { shopQuery = it }, placeholder = { Text("搜索店铺") }, singleLine = true, modifier = Modifier.testTag("square-shop-search"))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { TextButton(onClick = { vm.set("shop", ""); shops = false }, modifier = Modifier.testTag("square-shop-all")) { Text("全部店铺") } }
                items(c.shops.filter { s -> visibleEvents.any { it.shopId == s.id } && s.name.contains(shopQuery, true) }) { s -> TextButton(onClick = { vm.set("shop", s.id); shops = false }, modifier = Modifier.testTag("square-shop-${s.id}")) { Text(s.name) } }
            }
        }
    }, confirmButton = { TextButton(onClick = { shops = false }) { Text("关闭") } })
}

@Composable
private fun SquareFacetChoices(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, tag: String) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val matches = options.filter { it.contains(query, true) }
    Column {
        if (options.size > 8) OutlinedTextField(query, { query = it }, placeholder = { Text("搜索$label") }, singleLine = true,
            shape = LolitaSkin.current.cardShape, modifier = Modifier.fillMaxWidth().testTag("$tag-search"))
        val shown = if (expanded || query.isNotBlank()) matches else matches.take(12)
        SquareSpecChoices(label, (listOf(selected).filter { it.isNotEmpty() } + shown).distinct(), selected, onSelect, tag, unsetLabel = "不限")
        if (matches.size > 12 && query.isBlank()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "更多$label（${matches.size}）") }
        if (matches.isEmpty()) Text("暂无匹配$label", style = MaterialTheme.typography.bodySmall)
    }
}
