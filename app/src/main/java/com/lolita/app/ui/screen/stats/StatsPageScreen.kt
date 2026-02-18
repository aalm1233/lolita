package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.screen.calendar.PaymentCalendarContent
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.launch

@Composable
fun StatsPageScreen() {
    val tabs = listOf("总览", "消费分布", "消费趋势", "愿望单", "付款日历")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        GradientTopAppBar(title = { Text("统计") })

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            contentColor = Pink400,
            divider = { HorizontalDivider(color = Pink100) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selectedContentColor = Pink400,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> StatsContent()
                1 -> SpendingDistributionContent()
                2 -> SpendingTrendContent()
                3 -> WishlistAnalysisContent()
                4 -> PaymentCalendarContent()
            }
        }
    }
}
