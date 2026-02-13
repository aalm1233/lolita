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
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400
import com.lolita.app.ui.theme.Pink600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
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
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigationIcon()
                    ProvideTextStyle(
                        MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    ) {
                        Box(modifier = Modifier.weight(1f)) { title() }
                    }
                    Row(content = actions)
                }
            }
        }
    } else {
        TopAppBar(
            title = title,
            modifier = modifier.background(gradient),
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }
}
