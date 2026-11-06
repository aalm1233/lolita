package com.lolita.app.ui.screen.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch

/** Optional context. A normal tap/focusable button works on touch and keyboard alike. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTip(label: String, text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    BackHandler(state.isVisible) { scope.launch { state.dismiss() } }
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = state, enableUserInput = false, tooltip = {
            PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 280.dp)) { Text(text, style = MaterialTheme.typography.bodySmall) }
        }) {
        IconButton(onClick = { scope.launch { if (state.isVisible) state.dismiss() else state.show() } },
            modifier = modifier.semantics { contentDescription = label }) {
            SkinIcon(IconKey.Info, Modifier.size(19.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
