package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.ItemSourceRecord
import com.lolita.app.data.square.SquareWishSnapshot
import com.lolita.app.data.square.squarePrice
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*

/** The source survives catalogue replacement and ZIP path remapping. */
@Composable
internal fun SquareItemSources(records: List<ItemSourceRecord>, moneyVisible: Boolean) {
    var viewer by remember { mutableStateOf<List<String>?>(null) }
    records.filter { it.kind == "SQUARE_WISH" }.forEach { source ->
        val snapshot = remember(source.snapshotJson, source.imagePaths) { runCatching { SquareWishSnapshot.decode(source.snapshotJson, source.imagePaths.size) }.getOrNull() }
        if (snapshot != null) {
            LolitaSection(if (snapshot.grouped) "组合内容" else "商品参数", modifier = Modifier.testTag("square-item-members")) {
                snapshot.members.forEach { member -> row {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (snapshot.grouped) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            member.photos.firstOrNull()?.let { index ->
                                LolitaShimmerImage(source.imagePaths[index], member.name, contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(68.dp, 90.dp).clickable { viewer = member.photos.map { source.imagePaths[it] } }, circularRevealEnabled = false)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(member.name, style = MaterialTheme.typography.titleSmall)
                                Text("${member.color.ifEmpty { "颜色未选" }} · ${member.size.ifEmpty { "尺码未选" }}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        SquareMaterialFacts(member.facts)
                        if(squareReadableFactLines(member.facts).isEmpty() && member.sizeTable == null)Text("参数未提供",style=MaterialTheme.typography.bodyMedium)
                        member.sizeTable?.let { SquareSizeTableView(it) }
                    }
                } }
            }
            if (moneyVisible && snapshot.members.any { m -> m.origins.any { it.quote != null && it.quoteAppliesToMember } }) {
                LolitaSection("来源报价") {
                    snapshot.members.forEach { member ->
                        member.origins.filter { it.quote != null && it.quoteAppliesToMember }.forEach { origin -> row {
                            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(member.name, style = MaterialTheme.typography.titleSmall)
                                Text("${squarePrice(origin.quote)} · ${origin.priceType}", style = MaterialTheme.typography.bodyMedium)
                                Text(origin.capturedAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } }
                    }
                }
            }
        }
    }
    viewer?.let { paths -> FullScreenImageViewer(paths) { viewer = null } }
}
