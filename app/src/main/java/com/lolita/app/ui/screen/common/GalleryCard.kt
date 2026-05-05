package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.item.ItemCardData
import com.lolita.app.ui.theme.LolitaSkin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryCard(
    data: ItemCardData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = data.item
    val brandName = data.brandName
    val categoryName = data.categoryName
    val skin = LolitaSkin.current
    val cardShape = skin.cardShape
    val detailLine = remember(item.colors, item.size, categoryName) {
        listOfNotNull(
            categoryName?.takeIf { it.isNotBlank() },
            item.size?.takeIf { it.isNotBlank() },
            item.colors.firstOrNull()?.takeIf { it.isNotBlank() }
        )
            .distinct()
            .joinToString(" · ")
    }

    LolitaCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.GALLERY
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            if (item.imageUrls.isNotEmpty()) {
                LolitaShimmerImage(
                    model = java.io.File(item.imageUrls.first()),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heroSharedElement("itemImage-${item.id}")
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                    placeholderInitial = item.name.firstOrNull()?.toString()
                )
            } else {
                val initial = item.name.firstOrNull()?.toString() ?: "?"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(cardShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Gradient overlay with name and quick identifiers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (brandName != null) {
                        Text(
                            text = brandName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (detailLine.isNotEmpty()) {
                        Text(
                            text = detailLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
