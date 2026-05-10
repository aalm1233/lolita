package com.lolita.app.ui.component.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Refined categorical palette: muted, distinct hues for data visualization.
// Avoids the pink-heavy overload — each slice is visually distinguishable.
val ChartPalette = listOf(
    Color(0xFFC97B84), // Dusty Rose
    Color(0xFF6CA0A8), // Muted Teal
    Color(0xFFCC9E6E), // Warm Gold
    Color(0xFF8D7BA0), // Soft Violet
    Color(0xFF7DA87B), // Sage Green
    Color(0xFFC48B8B), // Dusty Rose Light
    Color(0xFF6B8AAD), // Steel Blue
    Color(0xFFD0AA7A), // Sand
    Color(0xFF9B8BA8), // Lavender Gray
    Color(0xFFB8987A), // Warm Taupe
)

// Skin-aware palette: first two slices pick up the active skin's identity.
@Composable
fun chartPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    Color(0xFFCC9E6E), // Warm Gold
    Color(0xFF8D7BA0), // Soft Violet
    Color(0xFF7DA87B), // Sage Green
    Color(0xFFC48B8B), // Dusty Rose Light
    Color(0xFF6B8AAD), // Steel Blue
    Color(0xFFD0AA7A), // Sand
    Color(0xFF9B8BA8), // Lavender Gray
    Color(0xFFB8987A), // Warm Taupe
)
