package com.lolita.app.ui.screen.share

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.share.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.usecase.ShareCardContent
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
fun ShareCardDialog(title: String, images: List<String>, items: List<Item>, brandNames: Map<Long, String>? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val skin = LolitaSkin.current
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors, skin) { SharePalette(colors.background.toArgb(), colors.surface.toArgb(),
        colors.onSurface.toArgb(), colors.primary.toArgb(), skin.cornerRadiusMedium.value * 3,
        skin.name, skin.skinType in setOf(SkinType.CLASSIC, SkinType.VICTORIAN, SkinType.GOTHIC)) }
    var showBrands by remember { mutableStateOf(true) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(title, images, items, showBrands, palette, retry, brandNames) {
        loading = true; files = emptyList(); error = null
        try {
            val brands = brandNames ?: AppModule.brandRepository().getAllBrands().first().associate { it.id to it.name }
            val content = ShareCardContent.create(title, images, items, brands, showBrands)
            files = ShareCardRenderer.render(context, content, palette)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "生成分享图片失败"
        } finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分享预览") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
            Row {
                Checkbox(checked = showBrands, onCheckedChange = { loading = true; files = emptyList(); showBrands = it })
                Text("显示品牌", modifier = Modifier.padding(top = skin.spacingMedium))
            }
            Text("图片包含照片与服饰名称，不包含金额、备注、渠道或收纳位置。", style = MaterialTheme.typography.bodySmall)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("重新生成") }
            }
            val scroll = rememberLazyListState()
            ObserveSkinScroll(scroll)
            LazyColumn(state = scroll, modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                items(files, key = { it.name }) { file ->
                    LolitaShimmerImage(model = file, contentDescription = "分享图片预览", circularRevealEnabled = false,
                        modifier = Modifier.fillMaxWidth().height(320.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = !loading && files.isNotEmpty(), onClick = {
            try {
                val intent = ShareImageIntents.create(context, files)
                context.startActivity(Intent.createChooser(intent, "分享穿搭"))
            } catch (e: Exception) { error = e.message ?: "打开分享失败" }
        }) { Text(if (files.size > 1) "分享 ${files.size} 张图片" else "分享图片") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
