package com.lolita.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
    val cardBorderStrokeDark: BorderStroke? = null,
    val imageFrameElevation: Dp,
    val imageFrameStroke: BorderStroke?,
    val imageFrameStrokeDark: BorderStroke? = null,
    val imageFramePadding: Dp,
    val sectionAccentColor: Color,
    val sectionAccentColorDark: Color,
    val sectionAccentWidth: Dp,
    val sectionDividerColor: Color,
    val sectionDividerColorDark: Color,
    val sectionDividerHeight: Dp,

    // Spacing system (8pt grid inspired)
    val spacingSmall: Dp = 4.dp,
    val spacingMedium: Dp = 8.dp,
    val spacingLarge: Dp = 16.dp,
    val spacingExtraLarge: Dp = 24.dp,

    // Unified corner radius system (3 levels)
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 16.dp,
    val cornerRadiusLarge: Dp = 24.dp,

    // Motion tokens (aligned with M3 Expressive MotionScheme)
    val spatialSpring: SpringSpec<Float>,
    val effectsSpring: SpringSpec<Float>,

    // Shadow refinement
    val cardShadowAmbientAlpha: Float = 0.08f,
    val cardShadowSpotAlpha: Float = 0.12f,

    // Card surface color (replaces runtime alpha blending)
    val cardContainerColor: Color,
    val cardContainerColorDark: Color,

    // Card inner layout
    val cardInnerPadding: Dp = 16.dp,
    val cardGap: Dp = 8.dp,

    // Dark mode accent desaturation factor (0.0 = no change, 0.3 = 30% desaturation)
    val accentDesaturationDark: Float = 0.0f,

    // Blur / glass effect tokens
    val topBarBlurEnabled: Boolean = true,
    val topBarBlurAlpha: Float = 0.7f,
    val topBarBlurTint: Color,
    val topBarBlurTintDark: Color,
    val navBarBlurEnabled: Boolean = true,
    val navBarBlurAlpha: Float = 0.7f,
    val navBarBlurTint: Color,
    val navBarBlurTintDark: Color,
    val dialogBlurEnabled: Boolean = true,
    val dialogBlurAlpha: Float = 0.6f,
    val heroTransitionEnabled: Boolean = true,

    // Card variant tokens
    val galleryCardElevation: Dp = 0.dp,
    val galleryCardBorderStroke: BorderStroke? = null,
    val galleryCardBorderStrokeDark: BorderStroke? = null,
    val galleryCardInnerPadding: Dp = 0.dp,
    val featuredCardElevation: Dp = 2.dp,
    val featuredCardBorderStroke: BorderStroke? = null,
    val featuredCardBorderStrokeDark: BorderStroke? = null,
    val featuredCardInnerPadding: Dp = 20.dp,
    val compactCardElevation: Dp = 0.5.dp,
    val compactCardBorderStroke: BorderStroke? = null,
    val compactCardBorderStrokeDark: BorderStroke? = null,
    val compactCardInnerPadding: Dp = 8.dp,

    val icons: SkinIconProvider,
    val animations: SkinAnimationProvider,
)
