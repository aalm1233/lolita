package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin

/** The surrounding Scaffold consumes the outer navigation insets before this IME bar. */
@Composable
fun EditorSaveBar(label: String, saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
    val skin = LolitaSkin.current
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = skin.cardElevation) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium)) {
            Button(onClick = onSave, enabled = enabled && !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(skin.spacingSmall))
                }
                Text(if (saving) "保存中…" else label)
            }
        }
    }
}
