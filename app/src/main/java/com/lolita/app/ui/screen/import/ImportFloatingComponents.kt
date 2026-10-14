package com.lolita.app.ui.screen.`import`

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.lolita.app.ui.screen.common.CardVariant
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.LolitaSkin

/** Only decorative edges are drawn behind the current editor; no hidden editors or input targets. */
@Composable
internal fun ImportStackCard(
    remaining: Int,
    showDecoration: Boolean,
    progress: () -> Float,
    direction: Int,
    content: @Composable () -> Unit
) {
    val skin = LolitaSkin.current
    val hasDeck = showDecoration && remaining > 0
    Box(Modifier.fillMaxWidth().padding(top = if (hasDeck) skin.spacingMedium * 2 else skin.spacingSmall)) {
        if (hasDeck) {
            Box(Modifier.fillMaxWidth().testTag("importStackBack")) {
                if (remaining > 1) {
                    LolitaCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spacingExtraLarge)
                            .height(skin.spacingExtraLarge * 3).offset(y = -skin.spacingMedium * 2),
                        variant = CardVariant.COMPACT,
                        contentPadding = PaddingValues(skin.spacingSmall)
                    ) {}
                }
                LolitaCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spacingMedium)
                        .height(skin.spacingExtraLarge * 3).offset(y = -skin.spacingMedium),
                    variant = CardVariant.COMPACT,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(skin.spacingSmall)
                ) {}
            }
        }
        Box(Modifier.fillMaxWidth().testTag("importSingleCard").graphicsLayer {
            val value = progress()
            translationX = direction * skin.spacingMedium.toPx() * (1f - value)
            scaleX = 0.985f + 0.015f * value
            scaleY = scaleX
        }) { content() }
    }
}

@Composable
internal fun ImportSingleProgress(index: Int, total: Int, next: ImportItemState?) {
    val skin = LolitaSkin.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        Text("第 ${index + 1} / $total 条资料", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        LinearProgressIndicator(progress = { (index + 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(skin.spacingSmall).clearAndSetSemantics {})
        if (next != null) {
            val role = when (next.paymentRole) { PaymentRole.DEPOSIT -> "定金"; PaymentRole.BALANCE -> "尾款"; null -> "全款" }
            Text("下一件：#${index + 2} ${next.name} · $role", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ImportActionDock(floating: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val skin = LolitaSkin.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
        .padding(horizontal = skin.spacingLarge, vertical = skin.spacingMedium)) {
        LolitaCard(
            modifier = Modifier.fillMaxWidth().then(if (floating) Modifier.testTag("importFloatingDock") else Modifier),
            variant = if (floating) CardVariant.FEATURED else CardVariant.COMPACT,
            shape = RoundedCornerShape(skin.cornerRadiusLarge),
            contentPadding = PaddingValues(skin.spacingMedium)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(skin.spacingSmall), content = content)
        }
    }
}
