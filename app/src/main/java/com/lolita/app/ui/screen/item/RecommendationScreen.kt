package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import com.lolita.app.domain.usecase.MatchScore
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import java.io.File
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    itemId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    viewModel: RecommendationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.loadRecommendations(itemId)
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("推荐搭配") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                },
                compact = true
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(uiState.error ?: "未知错误")
                }
            }
            uiState.recommendations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Checkroom,
                        title = "暂无推荐搭配",
                        subtitle = "添加更多服饰以获取搭配推荐"
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.recommendations.forEach { (categoryName, scores) ->
                        item {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(scores) { matchScore ->
                            RecommendationItemCard(
                                matchScore = matchScore,
                                onClick = { onNavigateToItem(matchScore.item.id) }
                            )
                        }
                        item { HorizontalDivider(color = MaterialTheme.colorScheme.primaryContainer, thickness = 1.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationItemCard(matchScore: MatchScore, onClick: () -> Unit) {
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            if (matchScore.item.imageUrl != null) {
                AsyncImage(
                    model = File(matchScore.item.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("?", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = matchScore.item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchScore.item.style?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    matchScore.item.color?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Score badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${(matchScore.score * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
