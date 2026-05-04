package com.lolita.app.ui.screen.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun ImageFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val border = if (isDark) skin.imageFrameStrokeDark ?: skin.imageFrameStroke else skin.imageFrameStroke
    Surface(
        modifier = modifier,
        shape = skin.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = border,
        shadowElevation = skin.imageFrameElevation
    ) {
        Box(modifier = Modifier.padding(skin.imageFramePadding)) {
            content()
        }
    }
}
