package com.lolita.app.ui.screen.coordinate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Coordinate
import com.lolita.app.di.AppModule

@Composable
fun CoordinateListScreen(onNavigateToDetail: (Long) -> Unit) {
    val repository = AppModule.coordinateRepository()
    val coordinates by repository.getAllCoordinates().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "套装管理",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (coordinates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无套装")
            }
        } else {
            coordinates.forEach { coordinate ->
                CoordinateCard(
                    coordinate = coordinate,
                    onClick = { onNavigateToDetail(coordinate.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CoordinateCard(coordinate: Coordinate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(coordinate.name, style = MaterialTheme.typography.titleMedium)
            if (coordinate.description.isNotEmpty()) {
                Text(
                    text = coordinate.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
