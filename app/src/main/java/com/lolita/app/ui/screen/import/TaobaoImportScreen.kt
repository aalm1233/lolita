package com.lolita.app.ui.screen.`import`

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaobaoImportScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (itemId: Long) -> Unit,
    onNavigateToProduct: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    onSupplement: (Long) -> Unit = {},
    viewModel: TaobaoImportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 本地图片选择器
    var pendingImageIndex by remember { mutableIntStateOf(-1) }
    val imagePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri
            ->
            viewModel.onLocalImageSelected(pendingImageIndex, uri)
        }

    when (uiState.currentStep) {
        ImportStep.ENTRY,
        ImportStep.FILES,
        ImportStep.SELECT,
        ImportStep.CONFLICT,
        ImportStep.PREPARE,
        ImportStep.REVIEW,
        ImportStep.RESULT,
        ImportStep.ADDED,
        ImportStep.STORAGE ->
            OrderImportExperience(
                uiState,
                viewModel,
                onBack,
                onNavigateToDetail,
                onNavigateToProduct,
                onNavigateToGuide,
                onSupplement,
            )
        ImportStep.DETAIL ->
            ImportDetailContent(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { viewModel.goBackToSelect() },
                onPickLocalImage = { index ->
                    pendingImageIndex = index
                    imagePickerLauncher.launch(arrayOf("image/*"))
                },
                onExecuteImport = { viewModel.reviewImport() },
                onViewOriginal = onNavigateToDetail,
                onNavigateToGuide = onNavigateToGuide,
            )
        ImportStep.IMPORTING -> ImportingContent()
    }
}

@Composable
private fun ImportingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("正在导入...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
