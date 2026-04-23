package com.lolita.app.ui.theme.skin.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val CountryNavCream = Color(0xFFF8EEDB)

@Composable
fun CountryBottomNavIcon(
    key: IconKey,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(24.dp).then(modifier)) {
        when (key) {
            IconKey.Home -> drawCountryHomeCharm(tint)
            IconKey.Wishlist -> drawCountryWishJar(tint)
            IconKey.Outfit -> drawCountryDressCharm(tint)
            IconKey.Stats -> drawCountryRosette(tint)
            IconKey.Settings -> drawCountryBonnet(tint)
            else -> {}
        }
    }
}

private fun DrawScope.drawCountryBow(center: Offset, scale: Float, tint: Color) {
    val leftLoop = Path().apply {
        moveTo(center.x - scale * 0.1f, center.y)
        cubicTo(
            center.x - scale * 0.42f, center.y - scale * 0.32f,
            center.x - scale * 0.5f, center.y + scale * 0.02f,
            center.x - scale * 0.2f, center.y + scale * 0.18f
        )
        close()
    }
    val rightLoop = Path().apply {
        moveTo(center.x + scale * 0.1f, center.y)
        cubicTo(
            center.x + scale * 0.42f, center.y - scale * 0.32f,
            center.x + scale * 0.5f, center.y + scale * 0.02f,
            center.x + scale * 0.2f, center.y + scale * 0.18f
        )
        close()
    }
    val leftTail = Path().apply {
        moveTo(center.x - scale * 0.08f, center.y + scale * 0.1f)
        lineTo(center.x - scale * 0.22f, center.y + scale * 0.4f)
        lineTo(center.x - scale * 0.02f, center.y + scale * 0.28f)
        close()
    }
    val rightTail = Path().apply {
        moveTo(center.x + scale * 0.08f, center.y + scale * 0.1f)
        lineTo(center.x + scale * 0.22f, center.y + scale * 0.4f)
        lineTo(center.x + scale * 0.02f, center.y + scale * 0.28f)
        close()
    }
    drawPath(leftLoop, tint)
    drawPath(rightLoop, tint)
    drawPath(leftTail, tint)
    drawPath(rightTail, tint)
    drawRoundRect(
        color = CountryNavCream,
        topLeft = Offset(center.x - scale * 0.09f, center.y - scale * 0.05f),
        size = Size(scale * 0.18f, scale * 0.16f),
        cornerRadius = CornerRadius(scale * 0.04f, scale * 0.04f)
    )
}

private fun DrawScope.drawCountryHomeCharm(tint: Color) {
    val s = size.minDimension
    drawCountryBow(Offset(s * 0.5f, s * 0.18f), s * 0.24f, tint)
    val plaque = Path().apply {
        moveTo(s * 0.14f, s * 0.36f)
        quadraticTo(s * 0.14f, s * 0.28f, s * 0.24f, s * 0.3f)
        lineTo(s * 0.76f, s * 0.3f)
        quadraticTo(s * 0.86f, s * 0.28f, s * 0.86f, s * 0.36f)
        lineTo(s * 0.86f, s * 0.8f)
        quadraticTo(s * 0.86f, s * 0.9f, s * 0.72f, s * 0.88f)
        lineTo(s * 0.28f, s * 0.88f)
        quadraticTo(s * 0.14f, s * 0.9f, s * 0.14f, s * 0.8f)
        close()
    }
    drawPath(plaque, tint)
    drawRoundRect(
        color = CountryNavCream,
        topLeft = Offset(s * 0.26f, s * 0.66f),
        size = Size(s * 0.48f, s * 0.1f),
        cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
    )
    drawCircle(CountryNavCream, s * 0.05f, Offset(s * 0.34f, s * 0.5f))
    drawCircle(CountryNavCream, s * 0.05f, Offset(s * 0.5f, s * 0.5f))
    drawCircle(CountryNavCream, s * 0.05f, Offset(s * 0.66f, s * 0.5f))
}

private fun DrawScope.drawCountryWishJar(tint: Color) {
    val s = size.minDimension
    val jar = Path().apply {
        moveTo(s * 0.3f, s * 0.3f)
        lineTo(s * 0.7f, s * 0.3f)
        lineTo(s * 0.78f, s * 0.46f)
        lineTo(s * 0.78f, s * 0.78f)
        quadraticTo(s * 0.78f, s * 0.9f, s * 0.66f, s * 0.9f)
        lineTo(s * 0.34f, s * 0.9f)
        quadraticTo(s * 0.22f, s * 0.9f, s * 0.22f, s * 0.78f)
        lineTo(s * 0.22f, s * 0.46f)
        close()
    }
    val cloth = Path().apply {
        moveTo(s * 0.18f, s * 0.3f)
        quadraticTo(s * 0.3f, s * 0.2f, s * 0.42f, s * 0.3f)
        quadraticTo(s * 0.5f, s * 0.38f, s * 0.58f, s * 0.3f)
        quadraticTo(s * 0.7f, s * 0.2f, s * 0.82f, s * 0.3f)
        lineTo(s * 0.76f, s * 0.48f)
        quadraticTo(s * 0.5f, s * 0.62f, s * 0.24f, s * 0.48f)
        close()
    }
    drawPath(jar, tint)
    drawPath(cloth, CountryNavCream)
    drawCountryBow(Offset(s * 0.5f, s * 0.29f), s * 0.18f, tint)
    drawCircle(CountryNavCream, s * 0.1f, Offset(s * 0.5f, s * 0.68f))
    drawCircle(tint.copy(alpha = 0.2f), s * 0.04f, Offset(s * 0.5f, s * 0.68f))
}

private fun DrawScope.drawCountryDressCharm(tint: Color) {
    val s = size.minDimension
    val dress = Path().apply {
        moveTo(s * 0.38f, s * 0.18f)
        quadraticTo(s * 0.5f, s * 0.1f, s * 0.62f, s * 0.18f)
        lineTo(s * 0.72f, s * 0.28f)
        quadraticTo(s * 0.86f, s * 0.34f, s * 0.82f, s * 0.52f)
        lineTo(s * 0.74f, s * 0.6f)
        lineTo(s * 0.88f, s * 0.82f)
        quadraticTo(s * 0.5f, s * 0.98f, s * 0.12f, s * 0.82f)
        lineTo(s * 0.26f, s * 0.6f)
        lineTo(s * 0.18f, s * 0.52f)
        quadraticTo(s * 0.14f, s * 0.34f, s * 0.28f, s * 0.28f)
        close()
    }
    val petticoat = Path().apply {
        moveTo(s * 0.22f, s * 0.76f)
        quadraticTo(s * 0.5f, s * 0.9f, s * 0.78f, s * 0.76f)
        lineTo(s * 0.72f, s * 0.64f)
        quadraticTo(s * 0.5f, s * 0.76f, s * 0.28f, s * 0.64f)
        close()
    }
    drawPath(dress, tint)
    drawCircle(CountryNavCream, s * 0.045f, Offset(s * 0.5f, s * 0.28f))
    drawPath(petticoat, CountryNavCream)
    drawCountryBow(Offset(s * 0.5f, s * 0.48f), s * 0.16f, CountryNavCream)
}

private fun DrawScope.drawCountryRosette(tint: Color) {
    val s = size.minDimension
    val center = Offset(s * 0.5f, s * 0.46f)
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawCircle(tint, s * 0.09f, Offset(center.x, center.y - s * 0.16f))
        }
    }
    val leftRibbon = Path().apply {
        moveTo(s * 0.4f, s * 0.66f)
        lineTo(s * 0.27f, s * 0.98f)
        lineTo(s * 0.47f, s * 0.82f)
        close()
    }
    val rightRibbon = Path().apply {
        moveTo(s * 0.6f, s * 0.66f)
        lineTo(s * 0.73f, s * 0.98f)
        lineTo(s * 0.53f, s * 0.82f)
        close()
    }
    drawPath(leftRibbon, tint)
    drawPath(rightRibbon, tint)
    drawCircle(tint, s * 0.18f, center)
    drawCircle(CountryNavCream, s * 0.1f, center)
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.41f, s * 0.44f),
        size = Size(s * 0.04f, s * 0.12f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.48f, s * 0.4f),
        size = Size(s * 0.04f, s * 0.16f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.24f),
        topLeft = Offset(s * 0.55f, s * 0.36f),
        size = Size(s * 0.04f, s * 0.2f),
        cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
    )
}

private fun DrawScope.drawCountryBonnet(tint: Color) {
    val s = size.minDimension
    val brim = Path().apply {
        moveTo(s * 0.12f, s * 0.52f)
        quadraticTo(s * 0.28f, s * 0.8f, s * 0.5f, s * 0.8f)
        quadraticTo(s * 0.72f, s * 0.8f, s * 0.88f, s * 0.52f)
        quadraticTo(s * 0.76f, s * 0.68f, s * 0.62f, s * 0.68f)
        lineTo(s * 0.38f, s * 0.68f)
        quadraticTo(s * 0.24f, s * 0.68f, s * 0.12f, s * 0.52f)
        close()
    }
    val crown = Path().apply {
        moveTo(s * 0.28f, s * 0.56f)
        quadraticTo(s * 0.3f, s * 0.24f, s * 0.5f, s * 0.24f)
        quadraticTo(s * 0.7f, s * 0.24f, s * 0.72f, s * 0.56f)
        close()
    }
    val leftTie = Path().apply {
        moveTo(s * 0.28f, s * 0.68f)
        lineTo(s * 0.22f, s * 0.96f)
        lineTo(s * 0.36f, s * 0.78f)
        close()
    }
    val rightTie = Path().apply {
        moveTo(s * 0.72f, s * 0.68f)
        lineTo(s * 0.78f, s * 0.96f)
        lineTo(s * 0.64f, s * 0.78f)
        close()
    }
    drawPath(brim, tint)
    drawPath(crown, tint)
    drawPath(leftTie, tint)
    drawPath(rightTie, tint)
    drawRoundRect(
        color = CountryNavCream,
        topLeft = Offset(s * 0.34f, s * 0.44f),
        size = Size(s * 0.32f, s * 0.08f),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
    )
    drawCountryBow(Offset(s * 0.5f, s * 0.66f), s * 0.16f, CountryNavCream)
}
