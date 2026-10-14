package com.lolita.app.ui.theme.skin.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.theme.LolitaSkin

/** A single, static window decoration. It owns no scroll state, animation or image files. */
@Composable
fun SkinArtBackground(modifier: Modifier = Modifier) {
    val skin = LolitaSkin.current
    val resource = skin.artBackgroundRes ?: return
    val isDark = isSystemInDarkTheme()
    val opacity = if (isDark) skin.artBackgroundAlphaDark else skin.artBackgroundAlpha
    val tint = if (isDark) skin.artBackgroundTintDark else null
    val filter = remember(tint) {
        tint?.let { ink ->
            // Retain the original folds and engraved shading instead of flattening the alpha
            // silhouette. Source luminance maps to 35–100% of each skin's muted light ink.
            val luminance = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
            val matrix = FloatArray(20)
            listOf(ink.red, ink.green, ink.blue).forEachIndexed { row, channel ->
                for (column in 0..2) matrix[row * 5 + column] = channel * 0.65f * luminance[column]
                matrix[row * 5 + 4] = channel * 0.35f * 255f
            }
            matrix[18] = 1f // Preserve transparent source pixels exactly.
            ColorFilter.colorMatrix(ColorMatrix(matrix))
        }
    }
    LolitaShimmerImage(
        model = resource,
        contentDescription = null,
        modifier = modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f)).clearAndSetSemantics {},
        // Preserve the illustration's proportions; window edges may naturally crop the art.
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        shimmerEnabled = false,
        circularRevealEnabled = false,
        colorFilter = filter,
        showFailurePlaceholder = false,
    )
}
