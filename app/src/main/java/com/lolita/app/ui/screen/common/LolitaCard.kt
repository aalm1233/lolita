package com.lolita.app.ui.screen.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardShape = LolitaSkin.current.cardShape
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    }
}
