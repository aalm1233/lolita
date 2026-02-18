package com.lolita.app.ui.screen.outfit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickOutfitLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuickOutfitLogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onNavigateBack()
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (uiState.existingLogId != null) "编辑今日穿搭" else "记录今日穿搭") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = uiState.selectedItemIds.isNotEmpty() && !uiState.isSaving
                    ) {
                        Text("保存", color = if (uiState.selectedItemIds.isNotEmpty()) Pink400
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                compact = true
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today's date (read-only)
            Surface(
                color = Pink400.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = dateFormat.format(Date()),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Pink400,
                    fontWeight = FontWeight.Medium
                )
            }

            // Collapsible note
            Row(
                modifier = Modifier.clickable { viewModel.toggleShowNote() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("备注", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (uiState.showNote) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.showNote) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = { viewModel.updateNote(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("今天的穿搭心得...") },
                    maxLines = 3
                )
            }

            // Selected count
            Text(
                text = "已选 ${uiState.selectedItemIds.size} 件",
                style = MaterialTheme.typography.labelMedium,
                color = Pink400
            )

            // Error
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Item grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.items) { item ->
                    QuickItemCard(
                        item = item,
                        isSelected = item.id in uiState.selectedItemIds,
                        onClick = { viewModel.toggleItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickItemCard(item: Item, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Pink400 else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = File(item.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Pink100
                ) {}
            }
            if (isSelected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Pink400
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(2.dp))
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
