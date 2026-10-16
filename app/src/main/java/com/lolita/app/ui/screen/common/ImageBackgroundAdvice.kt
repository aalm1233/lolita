package com.lolita.app.ui.screen.common

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*

internal data class ImageBackgroundStatus(val battery: String, val background: String, val network: String)

internal fun readImageBackgroundStatus(context: Context): ImageBackgroundStatus {
    val battery = runCatching {
        if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) "电池优化：已豁免"
        else "电池优化：未豁免，系统可能限制息屏网络"
    }.getOrDefault("电池优化：无法读取")
    val background = runCatching {
        if (Build.VERSION.SDK_INT < 28) "后台限制：请在系统设置中核对"
        else if (context.getSystemService(ActivityManager::class.java).isBackgroundRestricted) "后台限制：已限制"
        else "后台限制：系统未报告限制"
    }.getOrDefault("后台限制：无法读取")
    val network = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) "当前网络：已连接互联网"
        else "当前网络：未确认可访问互联网"
    }.getOrDefault("当前网络：无法读取")
    return ImageBackgroundStatus(battery, background, network)
}

internal fun imageBackgroundSettingsIntent(context: Context, battery: Boolean): Intent =
    if (battery) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

/** Shared, optional advice; settings open only after a user click. No setting is changed here. */
@Composable
fun ImageBackgroundAdvice() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val skin = LolitaSkin.current
    var show by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(readImageBackgroundStatus(context)) }
    var settingsError by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readImageBackgroundStatus(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    OutlinedButton(onClick = { status = readImageBackgroundStatus(context); show = true; settingsError = false }, modifier = Modifier.fillMaxWidth()) {
        SkinIcon(IconKey.Settings)
        Spacer(Modifier.width(skin.spacingSmall))
        Text("后台与息屏")
    }
    if (show) {
        val scroll = rememberScrollState()
        ObserveSkinScroll(scroll)
        AlertDialog(onDismissRequest = { show = false }, title = { Text("后台与息屏") }, text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                Text("离开页面后任务会继续处理，但息屏、系统省电或厂商后台管理仍可能暂停网络。前台通知和唤醒锁不能保证绕过这些限制。")
                Text(status.battery, style = MaterialTheme.typography.bodySmall)
                Text(status.background, style = MaterialTheme.typography.bodySmall)
                Text(status.network, style = MaterialTheme.typography.bodySmall)
                Text("vivo 手机：电池 → 后台耗电管理 → 允许高后台耗电。")
                Text("若息屏后反复失败，可先保持应用在前台、屏幕亮起并连接稳定网络，等待本次任务结束。网络状态只表示当前情况，不能保证息屏后保持。")
                Text("已发送的请求可能继续处理或计费；应用不会自动重试。请先核对任务结果及服务用量，再决定是否重新发起。")
                for ((label, battery) in listOf("电池设置" to true, "应用设置" to false)) {
                    OutlinedButton(onClick = {
                        settingsError = runCatching { context.startActivity(imageBackgroundSettingsIntent(context, battery)); false }.getOrDefault(true)
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                if (settingsError) Text("无法打开系统设置，请从手机设置中手动进入电池或应用管理。", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = { show = false }) { Text("关闭") } })
    }
}
