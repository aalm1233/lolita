package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.ui.theme.LolitaSkin

/**
 * Reusable brand logo component.
 * Fits the complete logo on a light backing, or shows a themed initial when no logo exists.
 */
@Composable
fun BrandLogo(
    brand: Brand?,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    circularRevealEnabled: Boolean = true
) {
    BrandLogo(
        logoUrl = brand?.logoUrl,
        brandName = brand?.name,
        size = size,
        modifier = modifier,
        circularRevealEnabled = circularRevealEnabled
    )
}

/**
 * Overload that takes logoUrl and name directly, for use in list cards
 * where full Brand object is not available.
 */
@Composable
fun BrandLogo(
    logoUrl: String?,
    brandName: String?,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    circularRevealEnabled: Boolean = true
) {
    if (!logoUrl.isNullOrBlank()) {
        val cornerRadius = LolitaSkin.current.cornerRadiusSmall.coerceAtMost(size / 4)
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                // Keep transparent, untinted official artwork legible in dark skins too.
                .background(Color.White)
                .padding(size / 12),
            contentAlignment = Alignment.Center
        ) {
            LolitaShimmerImage(
                model = logoUrl,
                contentDescription = brandName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholderInitial = brandName?.firstOrNull()?.toString(),
                circularRevealEnabled = circularRevealEnabled
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = brandName?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}
