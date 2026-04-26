package com.lolita.app.ui.screen.common

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
    Surface(
        modifier = modifier,
        shape = skin.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = skin.imageFrameStroke,
        shadowElevation = skin.imageFrameElevation
    ) {
        Box(modifier = Modifier.padding(skin.imageFramePadding)) {
            content()
        }
    }
}
