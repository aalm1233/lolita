package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.lolita.app.domain.model.ImageGenerationProvider

@Composable
fun ImageServiceSelector(provider: ImageGenerationProvider, enabled: Boolean, onSelect: (ImageGenerationProvider) -> Unit) {
    Column {
        ImageGenerationProvider.entries.forEach { option ->
            FilterChip(selected = provider == option, onClick = { onSelect(option) }, enabled = enabled,
                label = { Text(option.label) }, modifier = Modifier.fillMaxWidth().testTag("imageService-${option.name}"))
        }
    }
}
