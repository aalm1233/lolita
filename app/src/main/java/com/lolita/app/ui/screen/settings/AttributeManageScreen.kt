package com.lolita.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributeManageScreen(
    onBack: () -> Unit,
    onNavigateToBrand: () -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToStyle: () -> Unit,
    onNavigateToSeason: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToSource: () -> Unit
) {
    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("属性管理") },
                compact = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AttributeMenuItem(
                title = "品牌管理",
                description = "管理预置和自定义品牌",
                icon = Icons.Default.Favorite,
                iconTint = Color(0xFFFF69B4),
                onClick = onNavigateToBrand
            )
            AttributeMenuItem(
                title = "类型管理",
                description = "管理服饰类型",
                icon = Icons.AutoMirrored.Filled.List,
                iconTint = Color(0xFF6BCF7F),
                onClick = onNavigateToCategory
            )
            AttributeMenuItem(
                title = "风格管理",
                description = "管理服饰风格",
                icon = Icons.Default.Star,
                iconTint = Color(0xFFBA68C8),
                onClick = onNavigateToStyle
            )
            AttributeMenuItem(
                title = "季节管理",
                description = "管理适用季节",
                icon = Icons.Default.DateRange,
                iconTint = Color(0xFF4FC3F7),
                onClick = onNavigateToSeason
            )
            AttributeMenuItem(
                title = "位置管理",
                description = "管理服饰存放位置",
                icon = Icons.Default.LocationOn,
                iconTint = Color(0xFF81C784),
                onClick = onNavigateToLocation
            )
            AttributeMenuItem(
                title = "来源管理",
                description = "管理服饰来源",
                icon = Icons.Default.ShoppingCart,
                iconTint = Color(0xFFFF8A65),
                onClick = onNavigateToSource
            )
        }
    }
}

@Composable
private fun AttributeMenuItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
