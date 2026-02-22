package com.lolita.app.ui.screen.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.skinCardGlow

@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    val cardShape = LolitaSkin.current.cardShape
    val glowModifier = modifier.skinCardGlow(isScrolling = isScrolling)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = glowModifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = glowModifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    }
}
