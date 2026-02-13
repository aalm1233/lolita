package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.Pink600

@Composable
fun GradientTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val gradient = if (isSystemInDarkTheme()) {
        Brush.horizontalGradient(listOf(Pink600, Pink400))
    } else {
        Brush.horizontalGradient(listOf(Pink400, Pink300))
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
                                Text("✿", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                Spacer(modifier = Modifier.width(6.dp))
                                title()
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("✿", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
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
