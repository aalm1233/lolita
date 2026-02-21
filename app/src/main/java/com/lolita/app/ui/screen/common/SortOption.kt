package com.lolita.app.ui.screen.common

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

enum class SortOption(val label: String) {
    DEFAULT("默认排序"),
    DATE_DESC("日期 — 最新优先"),
    DATE_ASC("日期 — 最早优先"),
    PRICE_DESC("价格 — 最贵优先"),
    PRICE_ASC("价格 — 最便宜优先")
}

@Composable
fun SortMenuButton(
    currentSort: SortOption,
    showPriceOptions: Boolean,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = "排序",
            tint = if (currentSort != SortOption.DEFAULT)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortOption.entries.forEach { option ->
            if (option == SortOption.PRICE_DESC || option == SortOption.PRICE_ASC) {
                if (!showPriceOptions) return@forEach
            }
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSortSelected(option); expanded = false },
                trailingIcon = if (currentSort == option) {
                    { SkinIcon(IconKey.Save, tint = MaterialTheme.colorScheme.primary) }
                } else null
            )
        }
    }
}
