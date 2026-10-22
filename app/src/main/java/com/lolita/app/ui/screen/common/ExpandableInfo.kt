package com.lolita.app.ui.screen.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

/** Optional explanations only. Warnings, fees and required confirmations stay outside. */
@Composable
fun ExpandableInfo(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics {
            stateDescription = if (expanded) "已展开" else "已收起"
        }) {
            Text(if (expanded) "收起$title" else title)
            SkinIcon(if (expanded) IconKey.ExpandLess else IconKey.ExpandMore)
        }
        if (expanded) content()
    }
}
