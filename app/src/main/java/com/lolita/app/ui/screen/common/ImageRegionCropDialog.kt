package com.lolita.app.ui.screen.common

import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.data.file.*
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*

/** The caller owns the source and receives ownership of exactly one confirmed derived JPEG. */
@Composable
fun ImageRegionCropDialog(sourcePath: String, onDismiss: () -> Unit, onCropped: (String) -> Unit,
    previewInParent: Boolean = false) {
    val context = LocalContext.current
    val skin = LocalLolitaSkin.current
    val scope = rememberCoroutineScope()
    var source by remember(sourcePath) { mutableStateOf<CropPreview?>(null) }
    var loading by remember(sourcePath) { mutableStateOf(true) }
    var busy by remember(sourcePath) { mutableStateOf(false) }
    var error by remember(sourcePath) { mutableStateOf<String?>(null) }
    var selection by remember(sourcePath) { mutableStateOf(NormalizedCropRect.FULL) }
    var quarterTurns by remember(sourcePath) { mutableIntStateOf(0) }
    var fineRotation by remember(sourcePath) { mutableFloatStateOf(0f) }
    var zoom by remember(sourcePath) { mutableFloatStateOf(1f) }
    var pan by remember(sourcePath) { mutableStateOf(Offset.Zero) }
    var rendered by remember(sourcePath) { mutableStateOf<CropOutput?>(null) }
    var cropJob by remember(sourcePath) { mutableStateOf<Job?>(null) }
    var showAdvanced by remember(sourcePath) { mutableStateOf(false) }
    val owned = remember(sourcePath) { mutableSetOf<String>() }
    val scroll = rememberScrollState()
    ObserveSkinScroll(scroll)
    val rotation = quarterTurns * 90f + fineRotation
    fun invalidatePreview() {
        rendered?.path?.let { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) }
        rendered = null
        error = null
    }
    fun rotate(quarter: Int = quarterTurns, fine: Float = fineRotation) {
        invalidatePreview()
        quarterTurns = quarter
        fineRotation = fine
        selection = NormalizedCropRect.FULL
        zoom = 1f
        pan = Offset.Zero
    }
    fun generateCrop(returnToParent: Boolean) {
        val chosen = selection
        val angle = rotation
        busy = true
        error = null
        cropJob = scope.launch {
            try {
                val output = ImageRegionCropper.crop(context, sourcePath, chosen, angle)
                owned.add(output.path)
                if (returnToParent) {
                    onCropped(output.path)
                    owned.remove(output.path)
                } else rendered = output
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "裁切失败，请重新调整"
            } finally { busy = false }
        }
    }
    LaunchedEffect(sourcePath) {
        try { source = ImageRegionCropper.preview(sourcePath) }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "原图读取失败，请重新选择"
        } finally { loading = false }
    }
    DisposableEffect(sourcePath) {
        onDispose {
            cropJob?.cancel()
            source?.bitmap?.recycle()
            ImageFileHelper.cleanupUnreferencedImages(owned.toList())
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(skin.spacingMedium),
            shape = RoundedCornerShape(skin.cornerRadiusLarge), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(skin.spacingLarge), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("选择图片区域", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.semantics {contentDescription = "关闭裁切"}) { SkinIcon(IconKey.Close) }
                }
                Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    Text("只使用确认后的选区像素；原图保持不变。此步骤不会调用识图或生图服务。", style = MaterialTheme.typography.bodySmall)
                    if (loading) {
                        ShimmerRect(width = 320.dp, height = 280.dp, modifier = Modifier.fillMaxWidth())
                    }
                    source?.let { original ->
                        val dimensions = CropGeometry.rotatedSize(original.sourceWidth, original.sourceHeight, rotation)
                        val pixels = CropGeometry.pixelRect(selection, dimensions.width, dimensions.height)
                        if (!previewInParent || showAdvanced) {
                            Text("原图 ${original.sourceWidth} × ${original.sourceHeight} px · 当前选区 ${pixels.width} × ${pixels.height} px",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (rendered == null) {
                            CropSelectionCanvas(original, dimensions, rotation, selection, zoom, pan, !busy,
                                onSelection = { invalidatePreview(); selection = it },
                                onViewport = { newZoom, newPan -> zoom = newZoom; pan = newPan })
                            Text(if (previewInParent) "拖动四角调整范围，双指缩放查看。" else
                                "拖动四角调整矩形，拖动框内移动选区；框外拖动图像，双指缩放与平移。缩放仅用于查看，不会增加像素。",
                                style = MaterialTheme.typography.bodySmall)
                            if (previewInParent) TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !busy) {
                                Text(if (showAdvanced) "收起精细调整" else "精细调整与图片信息")
                            }
                            if ((!previewInParent || showAdvanced) &&
                                (original.bitmap.width != original.sourceWidth || original.bitmap.height != original.sourceHeight)) {
                                Text("显示预览已降采样为 ${original.bitmap.width} × ${original.bitmap.height} px；最终从原图区域解码，以实际裁切预览尺寸为准。",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { rotate(quarter = (quarterTurns + 1) % 4) }, enabled = !busy) { Text("旋转90°") }
                                TextButton(onClick = { invalidatePreview(); selection = NormalizedCropRect.FULL }, enabled = !busy) { Text("整图") }
                                TextButton(onClick = { rotate(0, 0f) }, enabled = !busy) { Text("重置") }
                            }
                            if (!previewInParent || showAdvanced) {
                                Text("微调 ${"%.1f".format(fineRotation)}°（旋转会重置选区）", style = MaterialTheme.typography.bodySmall)
                                Slider(value = fineRotation, onValueChange = { rotate(fine = it) }, valueRange = -15f..15f, enabled = !busy)
                                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, enabled = !busy) { Text("适应屏幕") }
                                if (previewInParent) Text("缩放仅用于查看，不会增加像素；最终以返回后的实际裁切图片为准。",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (!previewInParent) OutlinedButton(onClick = { generateCrop(false) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (busy) "正在生成裁切预览…" else "查看实际裁切")
                            }
                        } else {
                            rendered?.let { result ->
                                ImageFrame {
                                    LolitaShimmerImage(model = result.path, contentDescription = "实际裁切图片",
                                        circularRevealEnabled = false, contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(280.dp))
                                }
                                Text("实际裁切 ${result.width} × ${result.height} px · JPEG · 已移除位置等 EXIF 信息",
                                    style = MaterialTheme.typography.bodyMedium)
                                if (result.sampled) Text("为限制长图或大图内存，已按区域降采样；上方为最终文件的实际像素尺寸。",
                                    style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { invalidatePreview() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("返回调整选区") }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (previewInParent) Button(onClick = { generateCrop(true) },
                    enabled = !busy && !loading && source != null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "正在裁切…" else "使用此范围")
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(skin.spacingMedium)) {
                    OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = {
                        val result = rendered
                        if (result == null || !File(result.path).isFile) { error = "裁切预览已失效，请重新生成"; rendered = null }
                        else {
                            owned.remove(result.path)
                            onCropped(result.path)
                        }
                    }, enabled = !busy && rendered != null, modifier = Modifier.weight(1f)) { Text("确认使用选区") }
                }
            }
        }
    }
}

private fun imageViewport(size: IntSize, dimensions: CropDimensions, zoom: Float, pan: Offset): Rect {
    val scale = min(size.width.toFloat() / dimensions.width, size.height.toFloat() / dimensions.height) * 0.9f * zoom
    val width = dimensions.width * scale
    val height = dimensions.height * scale
    val left = (size.width - width) / 2f + pan.x
    val top = (size.height - height) / 2f + pan.y
    return Rect(left, top, left + width, top + height)
}

private fun selectionViewport(image: Rect, selection: NormalizedCropRect) = Rect(
    image.left + image.width * selection.left, image.top + image.height * selection.top,
    image.left + image.width * selection.right, image.top + image.height * selection.bottom)

private fun moveSelection(rect: NormalizedCropRect, delta: Offset): NormalizedCropRect {
    val dx = delta.x.coerceIn(-rect.left, 1f - rect.right)
    val dy = delta.y.coerceIn(-rect.top, 1f - rect.bottom)
    return NormalizedCropRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
}

private fun resizeSelection(rect: NormalizedCropRect, corner: Int, delta: Offset, dimensions: CropDimensions): NormalizedCropRect {
    val minWidth = 1f / dimensions.width
    val minHeight = 1f / dimensions.height
    val left = if (corner == 0 || corner == 3) (rect.left + delta.x).coerceIn(0f, (rect.right - minWidth).coerceAtLeast(0f)) else rect.left
    val right = if (corner == 1 || corner == 2) (rect.right + delta.x).coerceIn((rect.left + minWidth).coerceAtMost(1f), 1f) else rect.right
    val top = if (corner == 0 || corner == 1) (rect.top + delta.y).coerceIn(0f, (rect.bottom - minHeight).coerceAtLeast(0f)) else rect.top
    val bottom = if (corner == 2 || corner == 3) (rect.bottom + delta.y).coerceIn((rect.top + minHeight).coerceAtMost(1f), 1f) else rect.bottom
    return NormalizedCropRect(left, top, right, bottom)
}

@Composable
private fun CropSelectionCanvas(source: CropPreview, dimensions: CropDimensions, rotation: Float,
    selection: NormalizedCropRect, zoom: Float, pan: Offset, enabled: Boolean,
    onSelection: (NormalizedCropRect) -> Unit, onViewport: (Float, Offset) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val border = MaterialTheme.colorScheme.primary
    val handle = MaterialTheme.colorScheme.surface
    val background = MaterialTheme.colorScheme.surfaceVariant
    val currentSelection by rememberUpdatedState(selection)
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentOnSelection by rememberUpdatedState(onSelection)
    val currentOnViewport by rememberUpdatedState(onViewport)
    Canvas(modifier = Modifier.fillMaxWidth().height(320.dp)
        .semantics { contentDescription = "矩形选区，可拖动四角与双指缩放" }
        .onSizeChanged { size = it }
        .pointerInput(source, dimensions, rotation, size, enabled) {
            if (!enabled || size.width == 0 || size.height == 0) return@pointerInput
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val image = imageViewport(size, dimensions, currentZoom, currentPan)
                val frame = selectionViewport(image, currentSelection)
                val corners = listOf(frame.topLeft, frame.topRight, frame.bottomRight, frame.bottomLeft)
                val hitRadius = 26.dp.toPx()
                val corner = corners.indexOfFirst { (first.position - it).getDistance() <= hitRadius }
                val moveFrame = corner < 0 && frame.contains(first.position)
                var multiple = false
                var gestureSelection = currentSelection
                var gestureZoom = currentZoom
                var gesturePan = currentPan
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break
                    if (pressed >= 2) multiple = true
                    val delta = event.calculatePan().let { if (it.x.isFinite() && it.y.isFinite()) it else Offset.Zero }
                    if (multiple) {
                        val oldZoom = gestureZoom
                        val zoomChange = event.calculateZoom().takeIf { it.isFinite() } ?: 1f
                        val newZoom = (oldZoom * zoomChange).coerceIn(1f, 64f)
                        val centroid = event.calculateCentroid()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val anchor = if (centroid.x.isFinite() && centroid.y.isFinite()) centroid else center
                        gesturePan = gesturePan + delta + (anchor - center - gesturePan) * (1f - newZoom / oldZoom)
                        gestureZoom = newZoom
                        currentOnViewport(gestureZoom, gesturePan)
                    } else if (delta != Offset.Zero) {
                        val currentImage = imageViewport(size, dimensions, gestureZoom, gesturePan)
                        val normalized = Offset(delta.x / currentImage.width, delta.y / currentImage.height)
                        when {
                            corner >= 0 -> { gestureSelection = resizeSelection(gestureSelection, corner, normalized, dimensions); currentOnSelection(gestureSelection) }
                            moveFrame -> { gestureSelection = moveSelection(gestureSelection, normalized); currentOnSelection(gestureSelection) }
                            else -> { gesturePan += delta; currentOnViewport(gestureZoom, gesturePan) }
                        }
                    }
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }) {
        drawRect(background)
        val image = imageViewport(size, dimensions, zoom, pan)
        val frame = selectionViewport(image, selection)
        val matrix = Matrix().apply {
            setRectToRect(RectF(0f, 0f, source.bitmap.width.toFloat(), source.bitmap.height.toFloat()),
                RectF(0f, 0f, source.sourceWidth.toFloat(), source.sourceHeight.toFloat()), Matrix.ScaleToFit.FILL)
            postRotate(rotation % 360f)
            val bounds = RectF(0f, 0f, source.sourceWidth.toFloat(), source.sourceHeight.toFloat())
            val rotate = Matrix().apply { setRotate(rotation % 360f) }
            rotate.mapRect(bounds)
            postTranslate(-bounds.left, -bounds.top)
            postScale(image.width / dimensions.width, image.height / dimensions.height)
            postTranslate(image.left, image.top)
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.clipRect(0f, 0f, this.size.width, this.size.height)
            canvas.nativeCanvas.drawBitmap(source.bitmap, matrix, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG))
            canvas.nativeCanvas.restore()
        }
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, this@Canvas.size.width, this@Canvas.size.height))
            addRect(frame)
        }
        drawPath(shade, Color.Black.copy(alpha = 0.5f))
        drawRect(border, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
        listOf(frame.topLeft, frame.topRight, frame.bottomRight, frame.bottomLeft).forEach {
            drawCircle(border, 7.dp.toPx(), it)
            drawCircle(handle, 4.dp.toPx(), it)
        }
    }
}
