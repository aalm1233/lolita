package com.lolita.app.ui.screen.square

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lolita.app.data.square.SquareProduct
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.launch
import java.net.URI

/** Only public marketplace HTTPS links may leave the local catalog through this action. */
internal fun squareTaobaoUri(url: String): Uri? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
    if (listOf("taobao.com", "tmall.com").none { host == it || host.endsWith(".$it") }) return null
    return Uri.parse(url)
}

/** Attempt the official app first, then let Android choose an HTTPS handler. No network here. */
internal fun openSquareTaobao(context: Context, url: String): Boolean {
    val uri = squareTaobaoUri(url) ?: return false
    for (target in listOf("com.taobao.taobao", null)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (_: ActivityNotFoundException) {
            // The device may not have Taobao or an enabled browser.
        } catch (_: SecurityException) {
            // A blocked app must still allow the system-handler fallback.
        }
    }
    return false
}

@Composable
internal fun SquareTaobaoButton(products: List<SquareProduct>, snackbar: SnackbarHostState) {
    val links = remember(products) { products.filter { squareTaobaoUri(it.url) != null }.distinctBy { it.url } }
    var choosing by remember(links) { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    fun open(product: SquareProduct) {
        choosing = false
        if (!openSquareTaobao(context, product.url)) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("无法打开淘宝，请复制链接后重试", "复制链接", withDismissAction = true,
                    duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                clipboard.setText(AnnotatedString(product.url))
            }
        }
    }
    OutlinedButton(
        onClick = { if (links.size == 1) open(links.single()) else choosing = true },
        enabled = links.isNotEmpty(), shape = LolitaSkin.current.cardShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("square-open-taobao")
    ) {
        Text(if (links.isEmpty()) "暂无淘宝链接" else "淘宝查看")
        Spacer(Modifier.width(8.dp))
        SkinIcon(IconKey.OpenInNew, Modifier.size(20.dp))
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("选择商品链接") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { product ->
                    TextButton(onClick = { open(product) }, shape = LolitaSkin.current.cardShape,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().testTag("square-taobao-source:${product.id}")) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(product.title, style = MaterialTheme.typography.bodyLarge)
                            Text(product.priceType, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        SkinIcon(IconKey.OpenInNew, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("取消") } }
    )
}
