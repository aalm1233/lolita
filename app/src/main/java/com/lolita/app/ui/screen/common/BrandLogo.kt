package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Brand

/**
 * Reusable brand logo component.
 * Shows the brand's logo image in a circle, or a fallback with the brand's first character.
 */
@Composable
fun BrandLogo(
    brand: Brand?,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    if (brand?.logoUrl != null) {
        AsyncImage(
            model = brand.logoUrl,
            contentDescription = brand.name,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = brand?.name?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
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
    modifier: Modifier = Modifier
) {
    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = brandName,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
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
