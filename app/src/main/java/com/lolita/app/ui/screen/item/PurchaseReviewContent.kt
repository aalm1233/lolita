package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*

internal fun ItemPurchaseReview.issueGroups(): Set<String> = buildSet {
    if (needsOrderSelection) add("缺订单金额")
    if (needsRecordVerification)
        add(
            if (
                purchaseRecordStatus ==
                    com.lolita.app.data.local.entity.PurchaseRecordStatus.HISTORICAL_OWNED
            )
                "实付待核对"
            else "购买关系待核对"
        )
    if (hasUnknownPaymentDates) add("日期未知")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PurchaseReviewContent(
    reviews: List<ItemPurchaseReview>,
    loading: Boolean,
    onBack: () -> Unit,
    onDetail: (Long) -> Unit,
    onReview: (ItemPurchaseReview) -> Unit,
) {
    val skin = LolitaSkin.current
    val list = rememberLazyListState()
    ObserveSkinScroll(list)
    var filter by rememberSaveable { mutableStateOf("全部") }
    var skipped by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val groups =
        reviews.groupBy { it.priceId?.let { id -> "price:$id" } ?: "item:${it.orderItemId}" }
    val visible =
        groups.filter { (_, rows) -> filter == "全部" || rows.any { filter in it.issueGroups() } }
    val next = visible.entries.firstOrNull { it.key !in skipped }?.value?.firstOrNull()
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GradientTopAppBar(
                title = { Text("购买资料核对") },
                navigationIcon = { IconButton(onClick = onBack) { SkinIcon(IconKey.ArrowBack) } },
            )
        },
        bottomBar = {
            if (reviews.isNotEmpty())
                EditorSaveBar(if (next == null) "重新查看本轮跳过的资料" else "下一条待核对", false, !loading) {
                    if (next != null) onReview(next) else skipped = emptyList()
                }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(skin.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spacingLarge),
        ) {
            item {
                Text(
                    "${reviews.map { it.item.id }.distinct().size} 件物品待核对",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("按问题补齐资料，共用订单只处理一次。原因可能重叠，不可相加。", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    (listOf("全部", "缺订单金额", "实付待核对", "日期未知", "购买关系待核对")).forEach { reason ->
                        val count =
                            if (reason == "全部") reviews.size
                            else reviews.count { reason in it.issueGroups() }
                        FilterChip(
                            filter == reason,
                            { filter = reason },
                            label = { Text("$reason $count") },
                        )
                    }
                }
            }
            if (loading) items(3) { LolitaCard { ShimmerLine(widthFraction = .8f) } }
            else if (visible.isEmpty())
                item { SkinEmptyState(IconKey.CheckCircle, "暂无待核对购买资料", "") }
            items(visible.entries.toList(), key = { it.key }) { (key, rows) ->
                LolitaSection(
                    if (rows.size > 1) "共用订单 · ${rows.size} 件物品" else rows.first().item.name
                ) {
                    row {
                        Text(
                            rows.flatMap { it.issueGroups() }.distinct().joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(rows.map { it.reasonText }.distinct().joinToString("；"))
                        TextButton(
                            onClick = { onReview(rows.first()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    rows.any { it.needsOrderSelection } -> "设置并核对当前订单"
                                    rows.any { it.needsRecordVerification } -> "核对这笔订单"
                                    else -> "补充实际付款日期"
                                }
                            )
                        }
                    }
                    rows.forEach { review ->
                        row {
                            TextButton(onClick = { onDetail(review.item.id) }) {
                                Text("查看物品：${review.item.name}")
                            }
                        }
                    }
                    row {
                        TextButton(onClick = { skipped = (skipped + key).distinct() }) {
                            Text(if (key in skipped) "本轮已跳过" else "暂时跳过，不修改资料")
                        }
                    }
                }
            }
        }
    }
}
