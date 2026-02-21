package com.lolita.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
}

private fun buildTypography(fontFamily: FontFamily): Typography {
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
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
    )
}
