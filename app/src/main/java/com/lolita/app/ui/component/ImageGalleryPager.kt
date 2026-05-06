package com.lolita.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.screen.common.heroSharedElement

@Composable
fun ImageGalleryPager(
    imageUrls: List<String>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    sharedTransitionKey: String? = null
) {
    if (imageUrls.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageModifier = if (page == 0 && sharedTransitionKey != null) {
                Modifier.fillMaxSize()
                    .heroSharedElement(sharedTransitionKey)
                    .clickable { onImageClick(page) }
            } else {
                Modifier.fillMaxSize()
                    .clickable { onImageClick(page) }
            }

            LolitaShimmerImage(
                model = imageUrls[page],
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = pageModifier,
                circularRevealEnabled = false
            )
        }

        if (imageUrls.size > 1) {
            PageIndicatorDots(
                pageCount = imageUrls.size,
                currentPage = pagerState.currentPage,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
internal fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isActive) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}
