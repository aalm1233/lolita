package com.lolita.app.ui.screen.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.LolitaSkinConfig

/**
 * Skin-aware card with one surface and one layer of content padding.
 * [contentPadding] replaces the variant padding; null keeps the skin default.
 */
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.DEFAULT,
    containerColor: Color? = null,
    shape: CornerBasedShape? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val surfaceTint = containerColor
        ?: if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    // Resolve translucent tints once, so every part of the rounded surface has
    // the same color. Explicitly transparent image cards remain transparent.
    val resolvedContainerColor = if (surfaceTint.alpha > 0f && surfaceTint.alpha < 1f) {
        surfaceTint.compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        surfaceTint
    }
    val resolvedShape = shape ?: skin.cardShape

    val (elevation, border, innerPadding) = resolveVariantTokens(skin, variant, isDark)
    val resolvedContentPadding = contentPadding ?: PaddingValues(innerPadding)
    val layoutDirection = LocalLayoutDirection.current
    val hasContentPadding = resolvedContentPadding.calculateLeftPadding(layoutDirection) > 0.dp ||
        resolvedContentPadding.calculateRightPadding(layoutDirection) > 0.dp ||
        resolvedContentPadding.calculateTopPadding() > 0.dp ||
        resolvedContentPadding.calculateBottomPadding() > 0.dp

    val cardColors = CardDefaults.cardColors(containerColor = resolvedContainerColor)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = resolvedShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border
        ) {
            if (hasContentPadding) {
                Box(modifier = Modifier.padding(resolvedContentPadding)) { content() }
            } else {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape = resolvedShape,
            colors = cardColors,
            elevation = cardElevation,
            border = border
        ) {
            if (hasContentPadding) {
                Box(modifier = Modifier.padding(resolvedContentPadding)) { content() }
            } else {
                content()
            }
        }
    }
}

private data class VariantTokens(
    val elevation: Dp,
    val border: androidx.compose.foundation.BorderStroke?,
    val innerPadding: Dp
)

private fun resolveVariantTokens(skin: LolitaSkinConfig, variant: CardVariant, isDark: Boolean): VariantTokens = when (variant) {
    CardVariant.DEFAULT -> VariantTokens(
        elevation = skin.cardElevation,
        border = if (isDark) skin.cardBorderStrokeDark ?: skin.cardBorderStroke else skin.cardBorderStroke,
        innerPadding = skin.cardInnerPadding
    )
    CardVariant.GALLERY -> VariantTokens(
        elevation = skin.galleryCardElevation,
        border = if (isDark) skin.galleryCardBorderStrokeDark ?: skin.galleryCardBorderStroke else skin.galleryCardBorderStroke,
        innerPadding = skin.galleryCardInnerPadding
    )
    CardVariant.FEATURED -> VariantTokens(
        elevation = skin.featuredCardElevation,
        border = if (isDark) skin.featuredCardBorderStrokeDark ?: skin.featuredCardBorderStroke else skin.featuredCardBorderStroke,
        innerPadding = skin.featuredCardInnerPadding
    )
    CardVariant.COMPACT -> VariantTokens(
        elevation = skin.compactCardElevation,
        border = if (isDark) skin.compactCardBorderStrokeDark ?: skin.compactCardBorderStroke else skin.compactCardBorderStroke,
        innerPadding = skin.compactCardInnerPadding
    )
}
