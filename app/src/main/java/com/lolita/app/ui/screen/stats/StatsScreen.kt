package com.lolita.app.ui.screen.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.di.AppModule

@Composable
fun StatsScreen() {
    val itemRepository = AppModule.itemRepository()
    val outfitRepository = AppModule.outfitLogRepository()

    val ownedItems by itemRepository.getItemsByStatus(ItemStatus.OWNED)
        .collectAsState(initial = emptyList())
    val wishedItems by itemRepository.getItemsByStatus(ItemStatus.WISHED)
        .collectAsState(initial = emptyList())
    val outfitLogs by outfitRepository.getAllOutfitLogs()
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "数据统计",
            style = MaterialTheme.typography.headlineMedium
        )

        StatCard(title = "已拥有服饰", value = ownedItems.size.toString())
        StatCard(title = "愿望单", value = wishedItems.size.toString())
        StatCard(title = "穿搭记录", value = outfitLogs.size.toString())

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}
