package com.lolita.app.ui.screen.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun GradientTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val skin = LolitaSkin.current
    val gradient = if (isSystemInDarkTheme()) {
        Brush.horizontalGradient(skin.gradientColorsDark)
    } else {
        Brush.horizontalGradient(skin.gradientColors)
    }

    if (compact) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .background(gradient),
            color = Color.Transparent
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    modifier = Modifier
                        .background(gradient)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationIcon()
                    ProvideTextStyle(
                        MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedDecoration(skin)
                                Spacer(modifier = Modifier.width(6.dp))
                                title()
                                Spacer(modifier = Modifier.width(6.dp))
                                AnimatedDecoration(skin)
                            }
                        }
                    }
                    Row(content = actions)
                }
            }
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .background(gradient),
            color = Color.Transparent
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    modifier = Modifier
                        .background(gradient)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationIcon()
                    ProvideTextStyle(
                        MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            title()
                        }
                    }
                    Row(content = actions)
                }
            }
        }
    }
}

@Composable
private fun AnimatedDecoration(skin: com.lolita.app.ui.theme.LolitaSkinConfig) {
    val animateDecorations = skin.animations.ambientAnimation.topBarDecorationAnimated

    if (!animateDecorations) {
        Text(
            skin.topBarDecoration,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = skin.topBarDecorationAlpha)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "topBarDeco")

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Text(
        skin.topBarDecoration,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = skin.topBarDecorationAlpha * glowAlpha),
        modifier = Modifier.graphicsLayer {
            scaleX = breathScale
            scaleY = breathScale
        }
    )
}
