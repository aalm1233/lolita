package com.lolita.app.ui.screen.catalog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.component.ImageGalleryPager
import com.lolita.app.ui.screen.common.BrandLogo
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.findColorHex
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogDetailScreen(
    catalogEntryId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToItem: (Long) -> Unit,
    onAddToWishlist: (Long) -> Unit,
    onAddToOwned: (Long) -> Unit,
    viewModel: CatalogDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(catalogEntryId) {
        viewModel.loadCatalogEntry(catalogEntryId)
    }

    showError?.let { message ->
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("确定")
                }
            }
        )
    }

    if (showDeleteDialog && !uiState.isRemote) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后图鉴记录会移除，但已转化的衣橱条目不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            viewModel.deleteCatalogEntry()
                                .onSuccess { onBack() }
                                .onFailure { showError = it.message ?: "删除失败" }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("图鉴详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                actions = {
                    if (uiState.entry != null && !uiState.isRemote) {
                        IconButton(onClick = { onEdit(catalogEntryId) }) {
                            SkinIcon(IconKey.Edit)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            SkinIcon(IconKey.Delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.entry == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("图鉴记录不存在")
                }
            }

            else -> {
                val entry = requireNotNull(uiState.entry)
                var showFullScreen by remember { mutableStateOf(false) }
                var selectedPage by remember { mutableIntStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (entry.imageUrls.isNotEmpty()) {
                        ImageGalleryPager(
                            imageUrls = entry.imageUrls,
                            onImageClick = { index ->
                                selectedPage = index
                                showFullScreen = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            contentDescription = entry.name
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            entry.seriesName?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            CatalogDetailLinkedStatus(
                                linkedItemId = entry.linkedItemId,
                                linkedItemStatus = uiState.linkedItemStatus,
                                isRemote = uiState.isRemote
                            )
                        }

                        if (uiState.isRemote) {
                            CatalogRemoteReadOnlyNotice()
                        } else if (entry.linkedItemId == null || uiState.linkedItemStatus == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onAddToWishlist(entry.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("加入愿望单")
                                }
                                Button(
                                    onClick = { onAddToOwned(entry.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("加入衣橱")
                                }
                            }
                        } else {
                            Button(
                                onClick = { onNavigateToItem(entry.linkedItemId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("查看衣橱条目")
                            }
                        }

                        CatalogDetailSection(title = "基础信息") {
                            CatalogDetailRowWithBrand(
                                label = "品牌",
                                brandName = uiState.brandName,
                                brandLogoUrl = uiState.brandLogoUrl
                            )
                            CatalogDetailRow("分类", uiState.categoryName ?: "未设置")
                            entry.style?.takeIf { it.isNotBlank() }?.let { CatalogDetailRow("风格", it) }
                            entry.season?.takeIf { it.isNotBlank() }?.let { CatalogDetailRow("季节", it) }
                            entry.size?.takeIf { it.isNotBlank() }?.let { CatalogDetailRow("尺码", it) }
                            entry.source?.takeIf { it.isNotBlank() }?.let { CatalogDetailRow("来源", it) }
                        }

                        if (entry.colors.isNotEmpty()) {
                            CatalogDetailSection(title = "颜色") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    entry.colors.forEach { colorName ->
                                        val chipColor = findColorHex(colorName)?.let(::Color) ?: Color.Gray
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(chipColor, RoundedCornerShape(999.dp))
                                                )
                                                Text(
                                                    text = colorName,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.description.isNotBlank()) {
                            CatalogDetailSection(title = "描述") {
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        entry.referenceUrl?.takeIf { it.isNotBlank() }?.let { referenceUrl ->
                            CatalogDetailSection(title = "来源链接") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = referenceUrl,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(referenceUrl))
                                                    )
                                                }.onFailure {
                                                    showError = it.message ?: "无法打开链接"
                                                }
                                            }
                                        ) {
                                            SkinIcon(IconKey.OpenInNew, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("打开链接")
                                        }
                                    }
                                }
                            }
                        }

                        CatalogDetailSection(title = "时间") {
                            CatalogDetailRow("创建时间", formatCatalogTime(entry.createdAt))
                            CatalogDetailRow("更新时间", formatCatalogTime(entry.updatedAt))
                        }
                    }
                }

                if (showFullScreen) {
                    FullScreenImageViewer(
                        imageUrls = entry.imageUrls,
                        initialPage = selectedPage,
                        onDismiss = { showFullScreen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogDetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun CatalogDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CatalogDetailRowWithBrand(
    label: String,
    brandName: String?,
    brandLogoUrl: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (brandName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BrandLogo(logoUrl = brandLogoUrl, brandName = brandName, size = 18.dp)
                Text(
                    text = brandName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(
                text = "未设置",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CatalogDetailLinkedStatus(
    linkedItemId: Long?,
    linkedItemStatus: ItemStatus?,
    isRemote: Boolean
) {
    if (isRemote) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "Shared catalog · read only",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    if (linkedItemId == null || linkedItemStatus == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "尚未转化为衣橱条目",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        return
    }

    val (label, color) = when (linkedItemStatus) {
        ItemStatus.OWNED -> "已关联 · 已拥有" to MaterialTheme.colorScheme.tertiary
        ItemStatus.WISHED -> "已关联 · 愿望单" to MaterialTheme.colorScheme.primary
        ItemStatus.PENDING_BALANCE -> "已关联 · 待补尾款" to MaterialTheme.colorScheme.secondary
    }

    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CatalogRemoteReadOnlyNotice() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {
        Text(
            text = "This shared catalog entry is synced from the backend. Editing and direct conversion are disabled for now.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatCatalogTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
