package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.PathParser

/** Seven storefront silhouettes; the existing navigation shapes remain unchanged. */
private val squarePaths = mapOf(
    SkinIconStyle.SWEET to "M3 11Q1 8 5 7L7 3H17L19 7Q23 8 21 11M4 11V21H20V11M9 21V14H15V21M3 10Q5 14 8 10Q12 14 16 10Q19 14 21 10M7 3Q12 9 17 3",
    SkinIconStyle.GOTHIC to "M3 10L6 4L10 9L12 4L14 9L18 4L21 10M4 10V21H20V10M9 21V16L12 13L15 16V21M4 11L8 13L12 10L16 13L20 11",
    SkinIconStyle.CHINESE to "M2 8Q6 9 7 4L12 6L17 4Q18 9 22 8M4 9H20M5 10V21H19V10M9 21V14H15V21M2 13Q6 12 7 10M17 10Q18 12 22 13",
    SkinIconStyle.CLASSIC to "M3 8L6 4H18L21 8M3 9H21M5 9V21H19V9M8 9V21M16 9V21M10 21V16Q12 12 14 16V21M9 5H15",
    SkinIconStyle.NAVY to "M4 10L6 5H18L20 10M3 10H21M5 11V21H19V11M9 21V15H15V21M10 2H14V5M7 12H8M16 12H17M9 8H15",
    SkinIconStyle.COUNTRY to "M3 11L7 3H17L21 11M3 11Q5 14 7 11Q10 15 12 11Q15 15 17 11Q19 14 21 11M5 13V21H19V13M9 21V15H15V21M6 6H18",
    SkinIconStyle.VICTORIAN to "M3 10V7Q5 2 9 5Q12 0 15 5Q19 2 21 7V10M4 10H20M5 10V21H19V10M9 21V15Q12 10 15 15V21M6 7H8M16 7H18"
).mapValues { PathParser().parsePathString(it.value).toPath() }

internal fun DrawScope.drawSquareIcon(style: SkinIconStyle, tint: Color) {
    drawPath(squarePaths.getValue(style), tint, style = style.stroke)
}
