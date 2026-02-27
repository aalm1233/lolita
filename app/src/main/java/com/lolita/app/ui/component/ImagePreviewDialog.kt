package com.lolita.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lolita.app.ui.screen.item.ItemCardData
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@Composable
fun GalleryPreviewDialog(
    items: List<ItemCardData>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    if (items.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, items.lastIndex),
            pageCount = { items.size }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { items[it].item.id }
            ) { page ->
                val data = items[page]
                val zoomState = rememberZoomableState()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = java.io.File(data.item.imageUrls.first()),
                        contentDescription = data.item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zoomable(zoomState)
                    )
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) {
                SkinIcon(key = IconKey.Close, modifier = Modifier.size(24.dp), tint = Color.White)
            }

            // Bottom info bar
            val currentData = items.getOrNull(pagerState.currentPage)
            if (currentData != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            onNavigateToDetail(currentData.item.id)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentData.item.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentData.brandName != null) {
                        Text(
                            text = currentData.brandName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "点击查看详情",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )

                    // Page indicator dots
                    if (items.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val totalPages = items.size
                            val currentPage = pagerState.currentPage
                            val maxDots = 7
                            val (startPage, endPage) = if (totalPages <= maxDots) {
                                0 to totalPages - 1
                            } else {
                                val start = (currentPage - maxDots / 2).coerceIn(0, totalPages - maxDots)
                                start to (start + maxDots - 1)
                            }
                            for (i in startPage..endPage) {
                                Box(
                                    modifier = Modifier
                                        .size(if (i == currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == currentPage) Color.White
                                            else Color.White.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}
