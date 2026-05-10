package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.theme.LolitaSkin

/**
 * iOS Settings-style grouped section container.
 * Wraps a [SectionHeader] + content rows inside a [LolitaCard],
 * with embedded dividers between rows.
 *
 * Usage:
 * ```
 * LolitaSection(title = "基本信息") {
 *     row { Text("名称: ${item.name}") }
 *     row { Text("品牌: ${item.brand}") }
 * }
 * ```
 */
@Composable
fun LolitaSection(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: LolitaSectionScope.() -> Unit
) {
    val skin = LolitaSkin.current
    val scope = LolitaSectionScopeImpl().apply(content)

    LolitaCard(modifier = modifier.fillMaxWidth()) {
        Column {
            SectionHeader(title = title, action = action)
            scope.rows.forEachIndexed { index, rowContent ->
                if (index > 0) {
                    HorizontalDivider(
                        color = skin.sectionDividerColor,
                        thickness = skin.sectionDividerHeight
                    )
                }
                rowContent()
            }
        }
    }
}

/**
 * Scope for defining rows inside a [LolitaSection].
 */
interface LolitaSectionScope {
    fun row(content: @Composable ColumnScope.() -> Unit)
}

private class LolitaSectionScopeImpl : LolitaSectionScope {
    val rows = mutableListOf<@Composable ColumnScope.() -> Unit>()

    override fun row(content: @Composable ColumnScope.() -> Unit) {
        rows.add(content)
    }
}
