package com.lolita.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@Composable
fun FullScreenImageViewer(
    imageUrls: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    if (imageUrls.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, imageUrls.lastIndex),
            pageCount = { imageUrls.size }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val zoomState = rememberZoomableState()
                LolitaShimmerImage(
                    model = imageUrls[page],
                    contentDescription = "图片预览 ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(zoomState),
                    circularRevealEnabled = false
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = "关闭图片预览" },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White
                )
            ) {
                SkinIcon(
                    key = IconKey.Close,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }

            if (imageUrls.size > 1) {
                PageIndicatorDots(
                    pageCount = imageUrls.size,
                    currentPage = pagerState.currentPage,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}
