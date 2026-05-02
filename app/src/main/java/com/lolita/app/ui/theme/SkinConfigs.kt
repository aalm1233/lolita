package com.lolita.app.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.R
import com.lolita.app.ui.theme.skin.animation.*
import com.lolita.app.ui.theme.skin.icon.*

val LocalLolitaSkin = compositionLocalOf { defaultSkinConfig() }

object LolitaSkin {
    val current: LolitaSkinConfig
        @androidx.compose.runtime.Composable
        get() = LocalLolitaSkin.current
}

fun getSkinConfig(skinType: SkinType): LolitaSkinConfig = when (skinType) {
    SkinType.DEFAULT -> defaultSkinConfig()
    SkinType.GOTHIC -> gothicSkinConfig()
    SkinType.CHINESE -> chineseSkinConfig()
    SkinType.CLASSIC -> classicSkinConfig()
    SkinType.NAVY -> navySkinConfig()
    SkinType.COUNTRY -> countrySkinConfig()
    SkinType.VICTORIAN -> victorianSkinConfig()
}

private fun buildTypography(fontFamily: FontFamily): Typography {
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.5).sp),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.25).sp),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily, letterSpacing = (-0.25).sp),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.25).sp),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.15).sp),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily, letterSpacing = (-0.1).sp),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.1).sp),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily, letterSpacing = 0.05.sp),
        titleSmall = default.titleSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily),
    )
}

fun defaultSkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily.Default
    return LolitaSkinConfig(
        skinType = SkinType.DEFAULT,
        name = "甜美粉",
        lightColorScheme = lightColorScheme(
            primary = Pink400, onPrimary = White,
            primaryContainer = Pink100, onPrimaryContainer = Gray800,
            secondary = Lavender, onSecondary = Gray800,
            secondaryContainer = Cream, onSecondaryContainer = Gray800,
            tertiary = Pink300, onTertiary = White,
            background = Pink30, onBackground = Gray800,
            surface = White, onSurface = Gray800,
            surfaceVariant = Pink50,
            error = Color(0xFFD32F2F), onError = White,
            outline = Gray400, outlineVariant = Pink200
        ),
        darkColorScheme = darkColorScheme(
            primary = Pink400, onPrimary = White,
            primaryContainer = Pink600, onPrimaryContainer = Pink100,
            secondary = Lavender, onSecondary = Gray800,
            secondaryContainer = Gray800,
            tertiary = Pink300, onTertiary = White,
            background = Gray900, onBackground = Gray100,
            surface = Gray800, onSurface = Gray100,
            surfaceVariant = Color(0xFF3A3A3A),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(Pink400, Pink300),
        gradientColorsDark = listOf(Pink600, Pink400),
        accentColor = Pink400, accentColorDark = Pink400,
        cardColor = White, cardColorDark = Gray800,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(16.dp),
        buttonShape = RoundedCornerShape(16.dp),
        topBarDecoration = "✿", topBarDecorationAlpha = 0.7f,
        icons = SweetIconProvider(),
        animations = SweetAnimationProvider(),
        cardElevation = 1.dp,
        cardBorderStroke = null,
        imageFrameElevation = 2.dp,
        imageFrameStroke = null,
        imageFramePadding = 0.dp,
        sectionAccentColor = Pink400,
        sectionAccentColorDark = Pink400,
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Pink200,
        sectionDividerColorDark = Color(0xFF3A3A3A),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 16.dp, spacingExtraLarge = 24.dp,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 16.dp, cornerRadiusLarge = 24.dp,
        spatialSpring = spring(dampingRatio = 0.75f, stiffness = 400f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1600f),
        cardShadowAmbientAlpha = 0.08f, cardShadowSpotAlpha = 0.12f,
        cardContainerColor = White.copy(alpha = 0.75f), cardContainerColorDark = Gray800.copy(alpha = 0.75f),
        cardInnerPadding = 16.dp, cardGap = 8.dp,
        accentDesaturationDark = 0.0f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.72f,
        topBarBlurTint = Pink50, topBarBlurTintDark = Color(0xFF880E4F).copy(alpha = 0.3f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.75f,
        navBarBlurTint = White, navBarBlurTintDark = Gray800.copy(alpha = 0.3f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.65f,
    )
}

fun gothicSkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.cinzel_regular))
    val purple = Color(0xFF4A0E4E)
    val brightPurple = Color(0xFF9B59B6)
    val bloodRed = Color(0xFF8B0000)
    val darkRed = Color(0xFFC0392B)
    val darkBg = Color(0xFF1A1A2E)
    val darkSurface = Color(0xFF2D2D44)
    return LolitaSkinConfig(
        skinType = SkinType.GOTHIC,
        name = "哥特暗黑",
        lightColorScheme = lightColorScheme(
            primary = purple, onPrimary = White,
            primaryContainer = Color(0xFFE8D5E9), onPrimaryContainer = purple,
            secondary = bloodRed, onSecondary = White,
            secondaryContainer = Color(0xFFFFD6D6), onSecondaryContainer = bloodRed,
            tertiary = brightPurple, onTertiary = White,
            background = Color(0xFFF5F0F5), onBackground = Color(0xFF1A1A1A),
            surface = White, onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFEDE0ED),
            error = darkRed, onError = White,
            outline = Color(0xFF8E8E8E), outlineVariant = Color(0xFFD0C0D0)
        ),
        darkColorScheme = darkColorScheme(
            primary = brightPurple, onPrimary = White,
            primaryContainer = purple, onPrimaryContainer = Color(0xFFE8D5E9),
            secondary = darkRed, onSecondary = White,
            secondaryContainer = darkSurface,
            tertiary = brightPurple, onTertiary = White,
            background = darkBg, onBackground = Color(0xFFE0E0E0),
            surface = darkSurface, onSurface = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF3A3A50),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(purple, brightPurple),
        gradientColorsDark = listOf(Color(0xFF2D0A30), purple),
        accentColor = purple, accentColorDark = brightPurple,
        cardColor = White, cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(8.dp),
        buttonShape = RoundedCornerShape(8.dp),
        topBarDecoration = "✝", topBarDecorationAlpha = 0.5f,
        icons = GothicIconProvider(),
        animations = GothicAnimationProvider(),
        cardElevation = 2.dp,
        cardBorderStroke = BorderStroke(0.5.dp, Color(0xFF4A0E4E)),
        imageFrameElevation = 3.dp,
        imageFrameStroke = BorderStroke(0.5.dp, Color(0xFF9B59B6).copy(alpha = 0.4f)),
        imageFramePadding = 2.dp,
        sectionAccentColor = purple,
        sectionAccentColorDark = brightPurple,
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFD0C0D0),
        sectionDividerColorDark = Color(0xFF3A3A50),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 16.dp, spacingExtraLarge = 24.dp,
        cornerRadiusSmall = 4.dp, cornerRadiusMedium = 8.dp, cornerRadiusLarge = 12.dp,
        spatialSpring = spring(dampingRatio = 0.85f, stiffness = 300f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1200f),
        cardShadowAmbientAlpha = 0.12f, cardShadowSpotAlpha = 0.18f,
        cardContainerColor = White.copy(alpha = 0.75f), cardContainerColorDark = darkSurface.copy(alpha = 0.8f),
        cardInnerPadding = 16.dp, cardGap = 8.dp,
        accentDesaturationDark = 0.15f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.80f,
        topBarBlurTint = Color(0xFFF3E5F5), topBarBlurTintDark = darkBg.copy(alpha = 0.6f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.82f,
        navBarBlurTint = White, navBarBlurTintDark = darkSurface.copy(alpha = 0.5f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.70f,
    )
}

fun chineseSkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.noto_serif_sc_regular))
    val vermillion = Color(0xFFC41E3A)
    val darkVermillion = Color(0xFF8B2500)
    val gold = Color(0xFFDAA520)
    val darkGold = Color(0xFFB8860B)
    val darkBg = Color(0xFF1A1410)
    val darkSurface = Color(0xFF2D2520)
    return LolitaSkinConfig(
        skinType = SkinType.CHINESE,
        name = "中华风韵",
        lightColorScheme = lightColorScheme(
            primary = vermillion, onPrimary = White,
            primaryContainer = Color(0xFFFFE0E0), onPrimaryContainer = darkVermillion,
            secondary = gold, onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFFFFF8E1), onSecondaryContainer = darkGold,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = Color(0xFFFFF8F0), onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFDF5), onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFFFF0E0),
            error = Color(0xFFD32F2F), onError = White,
            outline = Color(0xFFBFA880), outlineVariant = Color(0xFFE0D0B0)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFE85050), onPrimary = White,
            primaryContainer = darkVermillion, onPrimaryContainer = Color(0xFFFFE0E0),
            secondary = darkGold, onSecondary = White,
            secondaryContainer = darkSurface,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = darkBg, onBackground = Color(0xFFE0D8D0),
            surface = darkSurface, onSurface = Color(0xFFE0D8D0),
            surfaceVariant = Color(0xFF3A3028),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(vermillion, Color(0xFFE85050)),
        gradientColorsDark = listOf(darkVermillion, vermillion),
        accentColor = vermillion, accentColorDark = Color(0xFFE85050),
        cardColor = Color(0xFFFFFDF5), cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(4.dp),
        buttonShape = RoundedCornerShape(4.dp),
        topBarDecoration = "☁", topBarDecorationAlpha = 0.6f,
        icons = ChineseIconProvider(),
        animations = ChineseAnimationProvider(),
        cardElevation = 1.dp,
        cardBorderStroke = BorderStroke(0.5.dp, vermillion),
        imageFrameElevation = 2.dp,
        imageFrameStroke = BorderStroke(0.5.dp, vermillion.copy(alpha = 0.6f)),
        imageFramePadding = 2.dp,
        sectionAccentColor = vermillion,
        sectionAccentColorDark = Color(0xFFE85050),
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFE0D0B0),
        sectionDividerColorDark = Color(0xFF3A3028),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 14.dp, spacingExtraLarge = 20.dp,
        cornerRadiusSmall = 2.dp, cornerRadiusMedium = 4.dp, cornerRadiusLarge = 8.dp,
        spatialSpring = spring(dampingRatio = 0.8f, stiffness = 350f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1400f),
        cardShadowAmbientAlpha = 0.06f, cardShadowSpotAlpha = 0.10f,
        cardContainerColor = Color(0xFFFFFDF5).copy(alpha = 0.75f), cardContainerColorDark = darkSurface.copy(alpha = 0.75f),
        cardInnerPadding = 14.dp, cardGap = 6.dp,
        accentDesaturationDark = 0.1f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.70f,
        topBarBlurTint = Color(0xFFFFF8F0), topBarBlurTintDark = darkSurface.copy(alpha = 0.4f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.72f,
        navBarBlurTint = Color(0xFFFFF8F0), navBarBlurTintDark = darkSurface.copy(alpha = 0.3f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.60f,
    )
}

fun classicSkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.playfair_display_regular))
    val wine = Color(0xFF722F37)
    val darkWine = Color(0xFF5B2333)
    val brown = Color(0xFF8B4513)
    val darkBrown = Color(0xFF6B3410)
    val darkBg = Color(0xFF1A1515)
    val darkSurface = Color(0xFF2D2525)
    return LolitaSkinConfig(
        skinType = SkinType.CLASSIC,
        name = "经典优雅",
        lightColorScheme = lightColorScheme(
            primary = wine, onPrimary = White,
            primaryContainer = Color(0xFFF0D8D8), onPrimaryContainer = darkWine,
            secondary = brown, onSecondary = White,
            secondaryContainer = Color(0xFFF0E0D0), onSecondaryContainer = darkBrown,
            tertiary = brown, onTertiary = White,
            background = Color(0xFFFAF5F0), onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFF8F5), onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF0E8E0),
            error = Color(0xFFD32F2F), onError = White,
            outline = Color(0xFFA09080), outlineVariant = Color(0xFFD0C0B0)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFA05060), onPrimary = White,
            primaryContainer = darkWine, onPrimaryContainer = Color(0xFFF0D8D8),
            secondary = darkBrown, onSecondary = White,
            secondaryContainer = darkSurface,
            tertiary = Color(0xFFA07050), onTertiary = White,
            background = darkBg, onBackground = Color(0xFFE0D8D0),
            surface = darkSurface, onSurface = Color(0xFFE0D8D0),
            surfaceVariant = Color(0xFF3A3030),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(wine, Color(0xFFA05060)),
        gradientColorsDark = listOf(darkWine, wine),
        accentColor = wine, accentColorDark = Color(0xFFA05060),
        cardColor = Color(0xFFFFF8F5), cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(12.dp),
        buttonShape = RoundedCornerShape(12.dp),
        topBarDecoration = "♠", topBarDecorationAlpha = 0.5f,
        icons = ClassicIconProvider(),
        animations = ClassicAnimationProvider(),
        cardElevation = 2.dp,
        cardBorderStroke = BorderStroke(0.5.dp, Color(0xFFB8860B)),
        imageFrameElevation = 3.dp,
        imageFrameStroke = BorderStroke(0.5.dp, Color(0xFFDAA520).copy(alpha = 0.5f)),
        imageFramePadding = 2.dp,
        sectionAccentColor = wine,
        sectionAccentColorDark = Color(0xFFA05060),
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFD0C0B0),
        sectionDividerColorDark = Color(0xFF3A3030),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 16.dp, spacingExtraLarge = 24.dp,
        cornerRadiusSmall = 6.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 16.dp,
        spatialSpring = spring(dampingRatio = 0.82f, stiffness = 350f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1400f),
        cardShadowAmbientAlpha = 0.10f, cardShadowSpotAlpha = 0.14f,
        cardContainerColor = Color(0xFFFFF8F5).copy(alpha = 0.75f), cardContainerColorDark = darkSurface.copy(alpha = 0.8f),
        cardInnerPadding = 16.dp, cardGap = 8.dp,
        accentDesaturationDark = 0.1f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.75f,
        topBarBlurTint = Color(0xFFFFF5F0), topBarBlurTintDark = darkSurface.copy(alpha = 0.4f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.78f,
        navBarBlurTint = Color(0xFFFFF5F0), navBarBlurTintDark = darkSurface.copy(alpha = 0.35f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.65f,
    )
}

fun navySkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.pacifico_regular))
    val skyBlue = Color(0xFF4A90D9)
    val lightSkyBlue = Color(0xFF5BA0E9)
    val gold = Color(0xFFDAA520)
    val darkGold = Color(0xFFB8860B)
    val darkBg = Color(0xFF0D1B2A)
    val darkSurface = Color(0xFF1B2D44)
    return LolitaSkinConfig(
        skinType = SkinType.NAVY,
        name = "清风水手",
        lightColorScheme = lightColorScheme(
            primary = skyBlue, onPrimary = White,
            primaryContainer = Color(0xFFD6EAFF), onPrimaryContainer = Color(0xFF1B3A5C),
            secondary = gold, onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFFFFF8E1), onSecondaryContainer = darkGold,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = Color(0xFFF0F8FF), onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFE8F4FD),
            error = Color(0xFFD32F2F), onError = White,
            outline = Color(0xFF8EAEC0), outlineVariant = Color(0xFFBDD8EA)
        ),
        darkColorScheme = darkColorScheme(
            primary = lightSkyBlue, onPrimary = White,
            primaryContainer = Color(0xFF2E6EB5), onPrimaryContainer = Color(0xFFD6EAFF),
            secondary = darkGold, onSecondary = White,
            secondaryContainer = darkSurface,
            tertiary = gold, onTertiary = Color(0xFF1A1A1A),
            background = darkBg, onBackground = Color(0xFFD0E0F0),
            surface = darkSurface, onSurface = Color(0xFFD0E0F0),
            surfaceVariant = Color(0xFF253A50),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(skyBlue, Color(0xFF87CEEB)),
        gradientColorsDark = listOf(Color(0xFF1B3A5C), Color(0xFF2E6EB5)),
        accentColor = skyBlue, accentColorDark = lightSkyBlue,
        cardColor = Color(0xFFF5FAFF), cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(14.dp),
        buttonShape = RoundedCornerShape(14.dp),
        topBarDecoration = "⚓", topBarDecorationAlpha = 0.6f,
        icons = NavyIconProvider(),
        animations = NavyAnimationProvider(),
        cardElevation = 1.dp,
        cardBorderStroke = null,
        imageFrameElevation = 2.dp,
        imageFrameStroke = null,
        imageFramePadding = 0.dp,
        sectionAccentColor = skyBlue,
        sectionAccentColorDark = lightSkyBlue,
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFBDD8EA),
        sectionDividerColorDark = Color(0xFF253A50),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 16.dp, spacingExtraLarge = 24.dp,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 14.dp, cornerRadiusLarge = 20.dp,
        spatialSpring = spring(dampingRatio = 0.7f, stiffness = 450f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1600f),
        cardShadowAmbientAlpha = 0.06f, cardShadowSpotAlpha = 0.10f,
        cardContainerColor = Color(0xFFF5FAFF).copy(alpha = 0.75f), cardContainerColorDark = darkSurface.copy(alpha = 0.75f),
        cardInnerPadding = 16.dp, cardGap = 8.dp,
        accentDesaturationDark = 0.05f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.70f,
        topBarBlurTint = Color(0xFFF0F8FF), topBarBlurTintDark = darkSurface.copy(alpha = 0.4f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.75f,
        navBarBlurTint = Color(0xFFF0F8FF), navBarBlurTintDark = darkSurface.copy(alpha = 0.35f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.60f,
    )
}

fun countrySkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.cormorant_garamond))
    val sage = Color(0xFF7C9A69)
    val deepSage = Color(0xFF5D7751)
    val berry = Color(0xFFC56759)
    val butter = Color(0xFFE9CC89)
    val cream = Color(0xFFF9F4E8)
    val warmWhite = Color(0xFFFFFCF7)
    val soil = Color(0xFF8B6747)
    val darkBg = Color(0xFF1F261C)
    val darkSurface = Color(0xFF2D3529)
    return LolitaSkinConfig(
        skinType = SkinType.COUNTRY,
        name = "牧歌田园",
        lightColorScheme = lightColorScheme(
            primary = sage, onPrimary = White,
            primaryContainer = Color(0xFFE3EDD8), onPrimaryContainer = deepSage,
            secondary = berry, onSecondary = White,
            secondaryContainer = Color(0xFFF6DDD7), onSecondaryContainer = Color(0xFF7A3F36),
            tertiary = butter, onTertiary = Color(0xFF3B2F1F),
            background = cream, onBackground = Color(0xFF31281F),
            surface = warmWhite, onSurface = Color(0xFF31281F),
            surfaceVariant = Color(0xFFF1E7D8),
            error = Color(0xFFD45745), onError = White,
            outline = Color(0xFFB9AA94), outlineVariant = Color(0xFFE6D7BF)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF9DB88B), onPrimary = Color(0xFF17311B),
            primaryContainer = deepSage, onPrimaryContainer = Color(0xFFE3EDD8),
            secondary = Color(0xFFD78678), onSecondary = Color(0xFF2C1411),
            secondaryContainer = Color(0xFF503530), onSecondaryContainer = Color(0xFFF7DDD6),
            tertiary = butter, onTertiary = Color(0xFF3B2F1F),
            background = darkBg, onBackground = Color(0xFFE5DFD3),
            surface = darkSurface, onSurface = Color(0xFFE5DFD3),
            surfaceVariant = Color(0xFF394235),
            error = Color(0xFFFFB4A8), onError = Color(0xFF690005)
        ),
        gradientColors = listOf(sage, Color(0xFFF0D8A7)),
        gradientColorsDark = listOf(Color(0xFF37442F), deepSage),
        accentColor = berry, accentColorDark = Color(0xFFD78678),
        cardColor = warmWhite, cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(20.dp),
        buttonShape = RoundedCornerShape(18.dp),
        topBarDecoration = "✿", topBarDecorationAlpha = 0.7f,
        icons = CountryIconProvider(),
        animations = CountryAnimationProvider(),
        cardElevation = 1.dp,
        cardBorderStroke = null,
        imageFrameElevation = 2.dp,
        imageFrameStroke = null,
        imageFramePadding = 0.dp,
        sectionAccentColor = berry,
        sectionAccentColorDark = Color(0xFFD78678),
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFE6D7BF),
        sectionDividerColorDark = Color(0xFF394235),
        sectionDividerHeight = 1.dp,
        spacingSmall = 6.dp, spacingMedium = 10.dp, spacingLarge = 18.dp, spacingExtraLarge = 26.dp,
        cornerRadiusSmall = 10.dp, cornerRadiusMedium = 20.dp, cornerRadiusLarge = 28.dp,
        spatialSpring = spring(dampingRatio = 0.65f, stiffness = 400f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1600f),
        cardShadowAmbientAlpha = 0.06f, cardShadowSpotAlpha = 0.10f,
        cardContainerColor = warmWhite.copy(alpha = 0.75f), cardContainerColorDark = darkSurface.copy(alpha = 0.75f),
        cardInnerPadding = 18.dp, cardGap = 10.dp,
        accentDesaturationDark = 0.1f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.68f,
        topBarBlurTint = cream, topBarBlurTintDark = darkSurface.copy(alpha = 0.35f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.70f,
        navBarBlurTint = cream, navBarBlurTintDark = darkSurface.copy(alpha = 0.3f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.58f,
    )
}

fun victorianSkinConfig(): LolitaSkinConfig {
    val fontFamily = FontFamily(Font(R.font.cormorant_garamond))
    val burgundy = Color(0xFF7B1E3A)
    val deepRose = Color(0xFF9C254D)
    val darkGold = Color(0xFFB8860B)
    val gold = Color(0xFFDAA520)
    val ivory = Color(0xFFFFF8F0)
    val cream = Color(0xFFFFF5E6)
    val darkBrown = Color(0xFF3E2723)
    val darkBg = Color(0xFF1A1210)
    val darkSurface = Color(0xFF2C1E18)
    val roseRed = Color(0xFFC4566A)
    val brightGold = Color(0xFFD4A843)
    val warmWhite = Color(0xFFF5E6D3)
    val brightRose = Color(0xFFE07088)
    return LolitaSkinConfig(
        skinType = SkinType.VICTORIAN,
        name = "维多利亚",
        lightColorScheme = lightColorScheme(
            primary = burgundy, onPrimary = White,
            primaryContainer = Color(0xFFF0D8E0), onPrimaryContainer = deepRose,
            secondary = darkGold, onSecondary = White,
            secondaryContainer = Color(0xFFFFF8E1), onSecondaryContainer = darkGold,
            tertiary = gold, onTertiary = darkBrown,
            background = ivory, onBackground = darkBrown,
            surface = cream, onSurface = darkBrown,
            surfaceVariant = Color(0xFFF5E8E0),
            error = Color(0xFFD32F2F), onError = White,
            outline = Color(0xFFA09080), outlineVariant = Color(0xFFD0C0B0)
        ),
        darkColorScheme = darkColorScheme(
            primary = roseRed, onPrimary = White,
            primaryContainer = burgundy, onPrimaryContainer = Color(0xFFF0D8E0),
            secondary = brightGold, onSecondary = darkBrown,
            secondaryContainer = darkSurface,
            tertiary = gold, onTertiary = darkBrown,
            background = darkBg, onBackground = warmWhite,
            surface = darkSurface, onSurface = warmWhite,
            surfaceVariant = Color(0xFF3A2820),
            error = Color(0xFFCF6679), onError = Black
        ),
        gradientColors = listOf(burgundy, darkGold),
        gradientColorsDark = listOf(darkBg, roseRed),
        accentColor = deepRose, accentColorDark = roseRed,
        cardColor = cream, cardColorDark = darkSurface,
        fontFamily = fontFamily, typography = buildTypography(fontFamily),
        cardShape = RoundedCornerShape(12.dp),
        buttonShape = RoundedCornerShape(8.dp),
        topBarDecoration = "⚜", topBarDecorationAlpha = 0.5f,
        icons = VictorianIconProvider(),
        animations = VictorianAnimationProvider(),
        cardElevation = 3.dp,
        cardBorderStroke = BorderStroke(1.dp, gold),
        imageFrameElevation = 4.dp,
        imageFrameStroke = BorderStroke(1.dp, gold.copy(alpha = 0.7f)),
        imageFramePadding = 3.dp,
        sectionAccentColor = deepRose,
        sectionAccentColorDark = roseRed,
        sectionAccentWidth = 3.dp,
        sectionDividerColor = Color(0xFFD0C0B0),
        sectionDividerColorDark = Color(0xFF3A2820),
        sectionDividerHeight = 1.dp,
        spacingSmall = 4.dp, spacingMedium = 8.dp, spacingLarge = 16.dp, spacingExtraLarge = 24.dp,
        cornerRadiusSmall = 6.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 16.dp,
        spatialSpring = spring(dampingRatio = 0.8f, stiffness = 300f),
        effectsSpring = spring(dampingRatio = 1.0f, stiffness = 1200f),
        cardShadowAmbientAlpha = 0.12f, cardShadowSpotAlpha = 0.18f,
        cardContainerColor = cream.copy(alpha = 0.8f), cardContainerColorDark = darkSurface.copy(alpha = 0.85f),
        cardInnerPadding = 20.dp, cardGap = 10.dp,
        accentDesaturationDark = 0.15f,
        topBarBlurEnabled = true, topBarBlurAlpha = 0.82f,
        topBarBlurTint = cream.copy(alpha = 0.9f), topBarBlurTintDark = darkSurface.copy(alpha = 0.5f),
        navBarBlurEnabled = true, navBarBlurAlpha = 0.85f,
        navBarBlurTint = cream, navBarBlurTintDark = darkSurface.copy(alpha = 0.45f),
        dialogBlurEnabled = true, dialogBlurAlpha = 0.72f,
    )
}
