package com.lolita.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/** One scale for all skins. Decorative faces belong to headings, reading faces to controls and data. */
internal fun lolitaTypography(headingFamily: FontFamily): Typography {
    fun type(size: Int, leading: Int, weight: FontWeight, heading: Boolean = false) = TextStyle(
        fontFamily = if (heading) headingFamily else FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = leading.sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
    return Typography(
        displayLarge = type(48, 56, FontWeight.SemiBold, true),
        displayMedium = type(40, 48, FontWeight.SemiBold, true),
        displaySmall = type(36, 44, FontWeight.SemiBold, true),
        headlineLarge = type(32, 40, FontWeight.SemiBold, true),
        headlineMedium = type(28, 36, FontWeight.SemiBold, true),
        headlineSmall = type(24, 32, FontWeight.SemiBold, true),
        titleLarge = type(22, 30, FontWeight.SemiBold, true),
        titleMedium = type(18, 26, FontWeight.SemiBold, true),
        titleSmall = type(16, 24, FontWeight.Medium),
        bodyLarge = type(16, 24, FontWeight.Normal),
        bodyMedium = type(14, 22, FontWeight.Normal),
        bodySmall = type(13, 20, FontWeight.Normal),
        labelLarge = type(15, 22, FontWeight.Medium),
        labelMedium = type(12, 18, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium)
    )
}

/** App bars sit between feature and section headings without borrowing the row style. */
val Typography.appBarTitle: TextStyle
    get() = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp)

/** Compact factual labels, e.g. a grid brand/status; not explanatory paragraphs. */
val Typography.metadata: TextStyle
    get() = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)

/** Currency and quantities retain a stable reading face in every skin. */
val TextStyle.numericReading: TextStyle
    get() = copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp)
