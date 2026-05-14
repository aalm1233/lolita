package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.screen.calendar.PaymentCalendarContent
import com.lolita.app.ui.navigation.LocalHazeState
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.SkinTabIndicator
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

@Composable
fun StatsPageScreen(
    onNavigateToFilteredList: (filterType: String, filterValue: String, title: String) -> Unit = { _, _, _ -> },
    onNavigateToItemDetail: (Long) -> Unit = {}
) {
    val tabs = listOf("总览", "付款年历", "消费分布", "消费趋势", "愿望单")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val skin = LolitaSkin.current
    val hazeState = LocalHazeState.current
    val topBarBlurEnabled = skin.topBarBlurEnabled && hazeState != null
    val topBarGradient = if (isSystemInDarkTheme()) {
        Brush.horizontalGradient(skin.gradientColorsDark)
    } else {
        Brush.horizontalGradient(skin.gradientColors)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (topBarBlurEnabled) {
                        Modifier.hazeEffect(
                            state = hazeState!!,
                            style = HazeStyle(
                                backgroundColor = if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint,
                                tint = HazeTint(
                                    (if (isSystemInDarkTheme()) skin.topBarBlurTintDark else skin.topBarBlurTint)
                                        .copy(alpha = skin.topBarBlurAlpha)
                                ),
                                blurRadius = 25.dp
                            )
                        )
                    } else {
                        Modifier
                    }
                ),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (topBarBlurEnabled) Modifier else Modifier.background(topBarGradient))
                    .statusBarsPadding()
            ) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 4.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer) },
                    indicator = { tabPositions ->
                        SkinTabIndicator(
                            tabPositions = tabPositions,
                            selectedTabIndex = pagerState.currentPage
                        )
                    }
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
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> StatsContent(
                    onNavigateToFilteredList = onNavigateToFilteredList,
                    onNavigateToItemDetail = onNavigateToItemDetail
                )
                1 -> PaymentCalendarContent()
                2 -> SpendingDistributionContent(
                    onNavigateToFilteredList = onNavigateToFilteredList
                )
                3 -> SpendingTrendContent(
                    onNavigateToFilteredList = onNavigateToFilteredList
                )
                4 -> WishlistAnalysisContent(
                    onNavigateToFilteredList = onNavigateToFilteredList
                )
            }
        }
    }
}
