package com.lolita.app.ui.component.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Non-composable fallback for use in ViewModels
val ChartPalette = listOf(
    Color(0xFFFF69B4), // Pink400
    Color(0xFFFF91A4), // Pink300
    Color(0xFFFF1493), // Pink500
    Color(0xFFE91E8C), // Pink600
    Color(0xFFFFB6C1), // Pink200
    Color(0xFFFF007F), // Rose
    Color(0xFFE6E6FA), // Lavender
    Color(0xFFFFD93D), // Yellow
    Color(0xFF6BCF7F), // Green
    Color(0xFFFF6B6B), // Red
)

// Skin-aware palette for use in @Composable functions
@Composable
fun chartPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,    // was Pink400
    MaterialTheme.colorScheme.tertiary,   // was Pink300
    Color(0xFFFF1493), // Pink500
    Color(0xFFE91E8C), // Pink600
    Color(0xFFFFB6C1), // Pink200
    Color(0xFFFF007F), // Rose
    Color(0xFFE6E6FA), // Lavender
    Color(0xFFFFD93D), // Yellow
    Color(0xFF6BCF7F), // Green
    Color(0xFFFF6B6B), // Red
)
