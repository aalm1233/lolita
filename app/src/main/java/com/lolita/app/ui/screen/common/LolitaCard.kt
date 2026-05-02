package com.lolita.app.ui.screen.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
    val cardColors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation)
    val border = skin.cardBorderStroke
    val innerPadding = skin.cardInnerPadding

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = skin.cardShape,
            colors = cardColors,
            elevation = elevation,
            border = border
        ) {
            Box(modifier = Modifier.padding(innerPadding)) { content() }
        }
    } else {
        Card(
            modifier = modifier,
            shape = skin.cardShape,
            colors = cardColors,
            elevation = elevation,
            border = border
        ) {
            Box(modifier = Modifier.padding(innerPadding)) { content() }
        }
    }
}
