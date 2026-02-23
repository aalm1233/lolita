package com.lolita.app.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

data class PredefinedColor(val name: String, val hex: Long)

val PREDEFINED_COLORS = listOf(
    PredefinedColor("白色", 0xFFFFFFFF),
    PredefinedColor("黑色", 0xFF000000),
    PredefinedColor("粉色", 0xFFFFB6C1),
    PredefinedColor("红色", 0xFFFF0000),
    PredefinedColor("蓝色", 0xFF4169E1),
    PredefinedColor("紫色", 0xFF8A2BE2),
    PredefinedColor("绿色", 0xFF228B22),
    PredefinedColor("黄色", 0xFFFFD700),
    PredefinedColor("米色", 0xFFF5F5DC),
    PredefinedColor("棕色", 0xFF8B4513),
    PredefinedColor("灰色", 0xFF808080),
    PredefinedColor("酒红", 0xFF722F37),
    PredefinedColor("藏蓝", 0xFF003153),
    PredefinedColor("生成色", 0xFFFBEDCD),
    PredefinedColor("绀色", 0xFF1B294B),
    PredefinedColor("金色", 0xFFDAA520),
    PredefinedColor("银色", 0xFFC0C0C0),
    PredefinedColor("橘色", 0xFFFF8C00),
    PredefinedColor("水色", 0xFF6CA6CD),
    PredefinedColor("薰衣草", 0xFFB39DDB),
    PredefinedColor("墨绿", 0xFF2E5A3C),
    PredefinedColor("驼色", 0xFFC19A6B),
)

fun findColorHex(name: String): Long? = PREDEFINED_COLORS.find { it.name == name }?.hex

/**
 * Safely parse a colors JSON string into a list of color names.
 * Handles: JSON arrays like ["粉色","白色"], plain strings like "粉色",
 * corrupted JSON with backslashes like [\"粉色\"], and comma-separated like "粉色,白色".
 */
fun parseColorsJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    val trimmed = json.trim()
    // Try JSON array first
    if (trimmed.startsWith("[")) {
        try {
            val result = com.google.gson.Gson().fromJson(trimmed, Array<String>::class.java)
            if (result != null) return result.toList().filter { it.isNotBlank() }
        } catch (_: Exception) { }
        // Try fixing corrupted JSON with backslashes
        try {
            val fixed = trimmed.replace("\\\"", "\"").replace("\\", "")
            val result = com.google.gson.Gson().fromJson(fixed, Array<String>::class.java)
            if (result != null) return result.toList().filter { it.isNotBlank() }
        } catch (_: Exception) { }
    }
    // Fallback: treat as plain string, split by common separators
    return trimmed.split(",", "、", "/").map { it.trim() }.filter { it.isNotBlank() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSelector(
    selectedColors: List<String>,
    onColorsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val predefinedNames = remember { PREDEFINED_COLORS.map { it.name }.toSet() }

    Column(modifier = modifier) {
        Text(
            text = "颜色 (可选)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Predefined color chips
            PREDEFINED_COLORS.forEach { predefined ->
                val isSelected = predefined.name in selectedColors
                ColorChip(
                    name = predefined.name,
                    color = Color(predefined.hex),
                    isSelected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedColors - predefined.name
                        } else {
                            selectedColors + predefined.name
                        }
                        onColorsChanged(updated)
                    }
                )
            }
            // Custom colors already selected
            selectedColors.filter { it !in predefinedNames }.forEach { custom ->
                ColorChip(
                    name = custom,
                    color = Color.Gray,
                    isSelected = true,
                    onClick = {
                        onColorsChanged(selectedColors - custom)
                    }
                )
            }
            // Add custom color button
            SuggestionChip(
                onClick = { showCustomDialog = true },
                label = { Text("+") }
            )
        }
    }

    if (showCustomDialog) {
        AddCustomColorDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { name ->
                if (name.isNotBlank() && name !in selectedColors) {
                    onColorsChanged(selectedColors + name)
                }
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun ColorChip(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    SkinIcon(
                        IconKey.CheckCircle,
                        modifier = Modifier.size(10.dp),
                        tint = if (color == Color(0xFF000000) || color == Color(0xFF003153)
                            || color == Color(0xFF1B294B) || color == Color(0xFF2E5A3C))
                            Color.White else Color.Black
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun AddCustomColorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var colorName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义颜色") },
        text = {
            OutlinedTextField(
                value = colorName,
                onValueChange = { colorName = it },
                label = { Text("颜色名称") },
                placeholder = { Text("例如：薄荷绿") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(colorName.trim()) },
                enabled = colorName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
