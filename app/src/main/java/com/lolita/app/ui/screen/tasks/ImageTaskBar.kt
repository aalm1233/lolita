package com.lolita.app.ui.screen.tasks

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.*
import kotlinx.coroutines.flow.*

/** Root-scoped observation starts with the application UI, independent of visiting task pages. */
class ImageTaskBarViewModel(tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks) : ViewModel() {
    val tasks = tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ImageTaskBar(tasks: List<ImageTask>, onOpenTasks: () -> Unit, modifier: Modifier = Modifier) {
    val active = tasks.firstOrNull { it.status.isActive }
    val queued = tasks.count { it.status == ImageTaskStatus.QUEUED }
    val pending = tasks.count { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }
    if (active == null && queued == 0 && pending == 0) return
    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor
    val summary = listOfNotNull(active?.status?.label,
        if (queued > 0) "排队 $queued 项" else null,
        if (pending > 0) "待确认 $pending 项" else null).joinToString(" · ")
    LolitaCard(onClick = onOpenTasks, variant = CardVariant.COMPACT,
        modifier = modifier.fillMaxWidth().padding(horizontal = skin.spacingMedium, vertical = skin.spacingSmall)
            .testTag("globalImageTaskBar").semantics(mergeDescendants = true) { role = Role.Button }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            SkinIcon(IconKey.Refresh, modifier = Modifier.size(24.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = accent)
                Text("任务中心", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SkinIcon(IconKey.KeyboardArrowRight, modifier = Modifier.size(24.dp), tint = accent)
        }
    }
}
