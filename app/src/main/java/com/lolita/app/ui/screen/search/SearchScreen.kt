package com.lolita.app.ui.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink300
import com.lolita.app.ui.theme.Pink400
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class SearchViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Item>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) flowOf(emptyList())
            else itemRepository.searchItemsByName(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(query: String) {
        _searchQuery.value = query
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("搜索") },
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
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateQuery(it) },
                label = { Text("搜索服饰") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "输入关键词搜索",
                    subtitle = "搜索服饰名称",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (results.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "未找到相关服饰",
                    subtitle = "试试其他关键词",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "找到 ${results.size} 个结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { item ->
                        SearchResultItem(
                            item = item,
                            onClick = { onNavigateToItem(item.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Pink300.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Pink400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = when (item.status) {
                            ItemStatus.OWNED -> MaterialTheme.colorScheme.primaryContainer
                            ItemStatus.WISHED -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when (item.status) {
                                ItemStatus.OWNED -> "已拥有"
                                ItemStatus.WISHED -> "愿望单"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
