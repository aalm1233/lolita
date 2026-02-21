package com.lolita.app.ui.screen.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.lolita.app.ui.theme.skin.animation.skinItemAppear
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.ui.screen.common.EmptyState
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.screen.common.SwipeToDeleteContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

data class WishlistUiState(
    val allItems: List<Item> = emptyList(),
    val filteredItems: List<Item> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class WishlistViewModel(
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            itemRepository.getWishlistByPriority().collect { items ->
                val query = _uiState.value.searchQuery
                _uiState.update {
                    it.copy(
                        allItems = items,
                        filteredItems = applySearch(items, query),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val items = _uiState.value.allItems
            _uiState.update { it.copy(filteredItems = applySearch(items, query)) }
        }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "删除失败"
            }
        }
    }

    private fun applySearch(items: List<Item>, query: String): List<Item> {
        if (query.isBlank()) return items
        return items.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long?) -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val deleteError by viewModel.errorMessage.collectAsState()
    var itemToDelete by remember { mutableStateOf<Item?>(null) }

    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("确定") }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要从愿望单删除 \"${itemToDelete?.name}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteItem(it) }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    SkinIcon(IconKey.Delete, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("愿望单") },
                compact = true
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                SkinIcon(IconKey.Add, tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.allItems.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("搜索愿望单") },
                    leadingIcon = { SkinIcon(IconKey.Search) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.allItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Favorite,
                    title = "愿望单为空",
                    subtitle = "添加心仪的服饰到愿望单",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.filteredItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "无搜索结果",
                    subtitle = "试试其他关键词",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        val index = uiState.filteredItems.indexOf(item)
                        SwipeToDeleteContainer(
                            onDelete = { itemToDelete = item }
                        ) {
                            WishlistItemCard(
                                item = item,
                                onClick = { onNavigateToDetail(item.id) },
                                modifier = Modifier
                                    .skinItemAppear(index)
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistItemCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (item.priority) {
        ItemPriority.HIGH -> Color(0xFFFF6B6B)
        ItemPriority.MEDIUM -> Color(0xFFFFD93D)
        ItemPriority.LOW -> Color(0xFF6BCF7F)
    }

    LolitaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )

            // Thumbnail
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(0.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(72.dp),
                    color = borderColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = borderColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when (item.priority) {
                                ItemPriority.HIGH -> "高"
                                ItemPriority.MEDIUM -> "中"
                                ItemPriority.LOW -> "低"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = borderColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (item.description.isNotEmpty()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when (item.status) {
                        ItemStatus.OWNED -> "已拥有"
                        ItemStatus.WISHED -> "愿望单"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
