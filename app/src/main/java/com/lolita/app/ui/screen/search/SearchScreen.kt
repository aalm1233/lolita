package com.lolita.app.ui.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Item
import com.lolita.app.di.AppModule

@Composable
fun SearchScreen(onNavigateToItem: (Long) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val repository = AppModule.itemRepository()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("搜索服饰") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("输入关键词搜索")
            }
        } else {
            SearchResultList(
                query = searchQuery,
                repository = repository,
                onItemClick = { onNavigateToItem(it.id) }
            )
        }
    }
}

@Composable
private fun SearchResultList(
    query: String,
    repository: com.lolita.app.data.repository.ItemRepository,
    onItemClick: (Item) -> Unit
) {
    val results by repository.searchItemsByName(query)
        .collectAsState(initial = emptyList())

    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("未找到相关服饰")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { item ->
                SearchResultItem(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun SearchResultItem(item: Item, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}
