package com.lolita.app.ui.component

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.domain.model.ImageTaskRequest
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.component.skinClickable
import com.lolita.app.ui.theme.skin.icon.*
import coil.request.ImageRequest
import coil.compose.SubcomposeAsyncImage
import java.io.File

/** Only the frozen product input: the first mannequin source is a template, not the product. */
data class DisplayImageComparison(val taskId: String, val input: String?, val result: String?, val original: String?) {
    companion object {
        fun from(task: ImageTask, availableResult: String?): DisplayImageComparison? {
            if (task.kind != ImageTaskKind.MANNEQUIN || task.batchId == null) return null
            val request = runCatching { ImageTaskRequest.decode(task.requestJson) }.getOrNull()
            return DisplayImageComparison(task.id, task.sourceImagePaths.lastOrNull()?.takeIf(String::isNotBlank),
                availableResult?.takeIf(String::isNotBlank), request?.productOriginalImagePath)
        }
    }
}

@Composable
fun DisplayComparisonPreview(comparison: DisplayImageComparison, onOpen: () -> Unit) {
    val skin = LolitaSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
            ComparisonPane(comparison.input, "本次原图", Modifier.weight(1f), onOpen = onOpen)
            ComparisonPane(comparison.result, "生成图", Modifier.weight(1f), onOpen = onOpen)
        }
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("compareOpen${comparison.taskId}")) { Text("放大对比") }
    }
}

@Composable
private fun ComparisonPane(path: String?, label: String, modifier: Modifier = Modifier, expanded: Boolean = false,
    scale: Float = 1f, panX: Float = 0f, panY: Float = 0f, onTransform: ((Float, Float, Float) -> Unit)? = null,
    onOpen: (() -> Unit)? = null) {
    val skin = LolitaSkin.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        ImageFrame(if (expanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()) {
            BoxWithConstraints((if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(0.75f))
                .clipToBounds().testTag("comparison$label"), contentAlignment = Alignment.Center) {
                val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val gesture = rememberTransformableState { zoom, pan, _ -> onTransform?.invoke(zoom, pan.x / width, pan.y / height) }
                val imageModifier = Modifier.fillMaxSize()
                    .then(if (onTransform != null) Modifier.transformable(gesture) else Modifier)
                    .then(if (onOpen != null) Modifier.skinClickable(onClick = onOpen) else Modifier)
                if (path != null && File(path).isFile) {
                    val context = LocalContext.current
                    // A narrow comparison pane must still decode enough detail for zooming.
                    val model = remember(path, expanded, context) {
                        if (expanded) ImageRequest.Builder(context).data(path).size(2048).build() else path
                    }
                    Box(imageModifier.semantics { contentDescription = "${label}完整预览" }) {
                        val transformed = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = panX * width; translationY = panY * height
                        }
                        if (expanded) SubcomposeAsyncImage(model, null, transformed, contentScale = ContentScale.Fit,
                            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
                            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图片无法读取", style = MaterialTheme.typography.bodySmall) } })
                        else LolitaShimmerImage(model, null, transformed, contentScale = ContentScale.Fit, circularRevealEnabled = false, placeholderInitial = "图片无法读取")
                    }
                } else Box(imageModifier, contentAlignment = Alignment.Center) {
                    Text(if (label == "本次原图") "本次原图不可用" else "暂无可用生成图", Modifier.padding(skin.spacingSmall), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayComparisonViewer(comparison: DisplayImageComparison, title: String, onDismiss: () -> Unit) {
    val skin = LolitaSkin.current
    var mode by rememberSaveable(comparison.taskId) { mutableIntStateOf(0) }
    var scale by rememberSaveable(comparison.taskId) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(comparison.taskId) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(comparison.taskId) { mutableFloatStateOf(0f) }
    var original by rememberSaveable(comparison.taskId) { mutableStateOf(false) }
    fun transform(zoom: Float, x: Float = 0f, y: Float = 0f) {
        scale = (scale * zoom).coerceIn(1f, 8f)
        val limit = (scale - 1f) / 2f
        panX = (panX + x).coerceIn(-limit, limit); panY = (panY + y).coerceIn(-limit, limit)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(Modifier.testTag("displayComparison"), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            GradientTopAppBar(title = { Text("对比展示图") }, navigationIcon = {
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "关闭对比" }) { SkinIcon(IconKey.ArrowBack) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(skin.spacingLarge),
                verticalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    listOf("并排", "原图", "生成图").forEachIndexed { index, label ->
                        FilterChip(mode == index, { mode = index }, label = { Text(label) }, modifier = Modifier.testTag("compareMode$index"))
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall)) {
                    if (mode != 2) ComparisonPane(comparison.input, "本次原图", Modifier.weight(1f).fillMaxHeight(), true, scale, panX, panY, ::transform)
                    if (mode != 1) ComparisonPane(comparison.result, "生成图", Modifier.weight(1f).fillMaxHeight(), true, scale, panX, panY, ::transform)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.spacingSmall), verticalArrangement = Arrangement.Center,
                    modifier = Modifier.semantics { stateDescription = "缩放${(scale * 100).toInt()}%，${if(panX == 0f && panY == 0f) "居中" else "已移动"}" }.testTag("comparisonTransform")) {
                    TextButton(onClick = { transform(1 / 1.5f) }, enabled = scale > 1f) { Text("缩小") }
                    TextButton(onClick = { transform(1.5f) }, enabled = scale < 8f) { Text("放大") }
                    TextButton(onClick = { scale = 1f; panX = 0f; panY = 0f }) { Text("还原") }
                }
                Text("双指缩放、拖动，两图同步查看", style = MaterialTheme.typography.bodySmall)
                if (comparison.original != null && comparison.original != comparison.input) TextButton(onClick = { original = true }, modifier = Modifier.fillMaxWidth()) { Text("查看完整原图") }
            }
        }
    }
    if (original) comparison.original?.let { FullScreenImageViewer(listOf(it), onDismiss = { original = false }) }
}
