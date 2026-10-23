package com.lolita.app.ui.screen.price

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.di.AppModule

// Tests can supply independent preferences without touching the user's settings.
val LocalPricingPreferences = staticCompositionLocalOf<AppPreferences?> { null }
@Composable
fun pricingMoneyVisible(): Boolean {
    val preferences = LocalPricingPreferences.current ?: AppModule.appPreferences()
    return preferences.showTotalPrice.collectAsState(initial = false).value
}
@Composable
internal fun EditingMoneyNotice() {
    if (!pricingMoneyVisible()) Text("本页为金额编辑，会临时显示金额；返回后仍按隐私设置隐藏。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable
internal fun PrivateMoneyActionNotice() {
    Text("编辑或核对时将显示金额。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
