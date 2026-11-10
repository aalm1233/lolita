package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.square.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.component.ImageGalleryPager
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll

@Composable
internal fun SquareTailDetail(c: SquareCatalog, e: SquareEvent, vm: SquareViewModel, amounts: Boolean, snackbar: SnackbarHostState) {
    val p = SquareVisibility.products(c, e).first()
    val photos = p.images.filter { it.role == "main" }.sortedBy { it.order }.map { it.path }
    val documents = p.images.filter { it.role == "detail" }.sortedBy { it.order }.map { it.path }.distinct()
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val state = rememberLazyListState(); ObserveSkinScroll(state)
    LazyColumn(Modifier.fillMaxSize().testTag("square-product"), state = state, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ImageFrame(Modifier.fillMaxWidth().height(310.dp)) {
            ImageGalleryPager(photos.map(vm::image), { viewer = photos.map(vm::image) to it }, Modifier.fillMaxSize(), e.title, contentScale = ContentScale.Fit)
        } }
        item {
            Text(c.shops.single { it.id == e.shopId }.name, style = MaterialTheme.typography.bodySmall)
            Text(e.title, style = MaterialTheme.typography.headlineSmall)
            val time = SquareEventPresentation.time(e, c.capturedAt)
            Text(if (time.label.startsWith("尾款")) time.summary else "尾款 · ${time.summary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            InfoTip("时间与售卖依据", listOf(SquareEventPresentation.time(e, c.capturedAt).detail, e.state.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"))
            if (amounts) { Text(e.price?.takeIf(String::isNotBlank) ?: "价格未知", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(p.priceType, style = MaterialTheme.typography.bodySmall) }
        }
        item { SquareTaobaoButton(listOf(p), snackbar) }
        item { LolitaSection("本批资料") { row {
            if (p.materials.orEmpty().isEmpty()) { Text("材质　未提供"); Text("尺码表　未提供") }
            else { SquareMaterialFacts(p.materials.orEmpty().map { it.facts }); p.materials.orEmpty().forEach { it.sizeTable?.let { table -> SquareSizeTableView(table) } } }
        } } }
        documents.forEachIndexed { index, path -> item {
            LolitaShimmerImage(vm.image(path), "${e.title}本批资料${index + 1}", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(420.dp).clickable { viewer = documents.map(vm::image) to index }, circularRevealEnabled = false)
        } }
        item { LolitaSection("来源") { row {
            Text("${e.captured?.takeIf(String::isNotBlank) ?: c.capturedAt} 快照", style = MaterialTheme.typography.bodySmall)
            Text(p.url, style = MaterialTheme.typography.bodySmall)
        } } }
    }
    viewer?.let { (paths, index) -> FullScreenImageViewer(paths, index) { viewer = null } }
}
