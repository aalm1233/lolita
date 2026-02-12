package com.lolita.app.ui.screen.item

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Item
import com.lolita.app.di.AppModule

@Composable
fun WishlistScreen(onNavigateToDetail: (Long) -> Unit) {
    val repository = AppModule.itemRepository()
    val items by repository.getWishlistByPriority().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "愿望单",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("愿望单为空")
            }
        } else {
            items.forEach { item ->
                WishlistItemCard(item = item, onClick = { onNavigateToDetail(item.id) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WishlistItemCard(item: Item, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "优先级: ${item.priority}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
