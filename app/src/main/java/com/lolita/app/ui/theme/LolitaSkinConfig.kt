package com.lolita.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import com.lolita.app.ui.theme.skin.icon.SkinIconProvider

data class LolitaSkinConfig(
    val skinType: SkinType,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val gradientColors: List<Color>,
    val gradientColorsDark: List<Color>,
    val accentColor: Color,
    val accentColorDark: Color,
    val cardColor: Color,
    val cardColorDark: Color,
    val fontFamily: FontFamily,
    val typography: Typography,
    val cardShape: Shape,
    val buttonShape: Shape,
    val topBarDecoration: String,
    val topBarDecorationAlpha: Float,
    val cardElevation: Dp,
    val cardBorderStroke: BorderStroke?,
    val imageFrameElevation: Dp,
    val imageFrameStroke: BorderStroke?,
    val imageFramePadding: Dp,
    val sectionAccentColor: Color,
    val sectionAccentColorDark: Color,
    val sectionAccentWidth: Dp,
    val sectionDividerColor: Color,
    val sectionDividerColorDark: Color,
    val sectionDividerHeight: Dp,
    val icons: SkinIconProvider,
    val animations: SkinAnimationProvider,
)
