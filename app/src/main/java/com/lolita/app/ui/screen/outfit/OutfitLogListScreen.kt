package com.lolita.app.ui.screen.outfit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.di.AppModule
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OutfitLogListScreen(onNavigateToDetail: (Long) -> Unit) {
    val repository = AppModule.outfitLogRepository()
    val logs by repository.getAllOutfitLogs().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "穿搭日记",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无穿搭记录")
            }
        } else {
            logs.forEach { log ->
                OutfitLogCard(
                    log = log,
                    onClick = { onNavigateToDetail(log.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun OutfitLogCard(log: OutfitLog, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dateFormat.format(Date(log.date)),
                style = MaterialTheme.typography.titleMedium
            )
            if (log.note.isNotEmpty()) {
                Text(
                    text = log.note,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}
