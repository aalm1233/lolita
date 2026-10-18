package com.lolita.app.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

/** Coordinates refer to the complete EXIF-corrected and user-rotated image, not the viewport. */
data class NormalizedCropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && left < right && top < bottom) {
            "请选择图片内有效的矩形区域"
        }
    }
    companion object { val FULL = NormalizedCropRect(0f, 0f, 1f, 1f) }
}

data class CropDimensions(val width: Int, val height: Int)
data class CropPixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Integer geometry is shared by labels, preview and the file actually returned to the caller. */
object CropGeometry {
    const val MAX_OUTPUT_DIMENSION = 4096
    const val MAX_OUTPUT_PIXELS = 8_000_000L
    fun pixelRect(region: NormalizedCropRect, width: Int, height: Int): CropPixelRect {
        require(width > 0 && height > 0)
        val left = (region.left * width).roundToInt().coerceIn(0, width - 1)
        val top = (region.top * height).roundToInt().coerceIn(0, height - 1)
        return CropPixelRect(left, top, (region.right * width).roundToInt().coerceIn(left + 1, width),
            (region.bottom * height).roundToInt().coerceIn(top + 1, height))
    }
    fun rotatedSize(width: Int, height: Int, rotationDegrees: Float): CropDimensions {
        require(width > 0 && height > 0 && rotationDegrees.isFinite())
        val radians = Math.toRadians(rotationDegrees.toDouble() % 360)
        fun extent(value: Double) = ceil(value - 0.00001).toInt().coerceAtLeast(1)
        return CropDimensions(extent(abs(width * cos(radians)) + abs(height * sin(radians))),
            extent(abs(width * sin(radians)) + abs(height * cos(radians))))
    }
    fun outputSize(width: Int, height: Int, sample: Int = 1,
        maxDimension: Int = MAX_OUTPUT_DIMENSION, maxPixels: Long = MAX_OUTPUT_PIXELS): CropDimensions {
        require(width > 0 && height > 0 && sample > 0 && maxDimension > 0 && maxPixels > 0)
        val scale = minOf(1.0 / sample, maxDimension.toDouble() / width, maxDimension.toDouble() / height,
            sqrt(maxPixels.toDouble() / width.toDouble() / height.toDouble()))
        return CropDimensions(floor(width * scale + 0.000001).toInt().coerceAtLeast(1),
            floor(height * scale + 0.000001).toInt().coerceAtLeast(1))
    }
}

data class CropPreview(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)
data class CropOutput(val path: String, val width: Int, val height: Int,
    val sourceWidth: Int, val sourceHeight: Int, val sampled: Boolean)

/** No network, source writes or metadata copying. Large sources are decoded by selected region. */
object ImageRegionCropper {
    private const val MAX_DECODE_PIXELS = 6_000_000L
    private const val MAX_PREVIEW_PIXELS = 1_600_000L
    private data class Source(val path: String, val width: Int, val height: Int, val orientation: Int) {
        val orientedWidth get() = if (orientation in 5..8) height else width
        val orientedHeight get() = if (orientation in 5..8) width else height
    }
    private data class Rendered(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int, val sampled: Boolean)

    private fun source(path: String): Source {
        val file = File(path)
        require(file.isFile && file.canRead() && file.length() in 1..80L * 1024 * 1024) { "原图无法读取或超过 80MiB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..1_000_000 && bounds.outHeight in 1..1_000_000) { "原图不是可读取的图片" }
        val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            .takeIf { it in 1..8 } ?: ExifInterface.ORIENTATION_NORMAL
        return Source(file.path, bounds.outWidth, bounds.outHeight, orientation)
    }

    /** Maps raw source pixels to EXIF-corrected, manually rotated pixels with a nonnegative origin. */
    private fun transform(source: Source, rotation: Float): Matrix {
        require(rotation.isFinite() && abs(rotation) <= 3600f) { "旋转角度无效" }
        val values = when (source.orientation) {
            2 -> floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            3 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
            4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
            5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            6 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            7 -> floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            8 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
        return Matrix().apply {
            setValues(values)
            postRotate(rotation % 360f)
            val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
            mapRect(bounds)
            postTranslate(-bounds.left, -bounds.top)
        }
    }

    @Suppress("DEPRECATION")
    private fun render(source: Source, region: NormalizedCropRect, rotation: Float,
        maxPixels: Long, maxDecodePixels: Long): Rendered {
        val dimensions = CropGeometry.rotatedSize(source.orientedWidth, source.orientedHeight, rotation)
        val crop = CropGeometry.pixelRect(region, dimensions.width, dimensions.height)
        val sourceTransform = transform(source, rotation)
        val inverse = Matrix()
        require(sourceTransform.invert(inverse)) { "图片旋转无法读取" }
        val raw = RectF(crop.left.toFloat(), crop.top.toFloat(), crop.right.toFloat(), crop.bottom.toFloat())
        inverse.mapRect(raw)
        val decodeRect = Rect(floor(raw.left + 0.0001).toInt().coerceIn(0, source.width),
            floor(raw.top + 0.0001).toInt().coerceIn(0, source.height),
            ceil(raw.right - 0.0001).toInt().coerceIn(0, source.width),
            ceil(raw.bottom - 0.0001).toInt().coerceIn(0, source.height))
        require(decodeRect.width() > 0 && decodeRect.height() > 0) { "选区没有图片内容，请调整矩形" }
        var sample = 1
        while (ceil(decodeRect.width().toDouble() / sample) * ceil(decodeRect.height().toDouble() / sample) > maxDecodePixels) sample *= 2
        val outputSize = CropGeometry.outputSize(crop.width, crop.height, sample, maxPixels = maxPixels)
        val decoder = BitmapRegionDecoder.newInstance(source.path, false)
            ?: throw IllegalArgumentException("原图不支持区域读取，请换一张图片")
        val decoded = try {
            decoder.decodeRegion(decodeRect, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IllegalArgumentException("读取所选图片区域失败")
        } finally { decoder.recycle() }
        try {
            val output = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(output)
                canvas.drawColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setRectToRect(RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat()), RectF(decodeRect), Matrix.ScaleToFit.FILL)
                    postConcat(sourceTransform)
                    postTranslate(-crop.left.toFloat(), -crop.top.toFloat())
                    postScale(outputSize.width.toFloat() / crop.width, outputSize.height.toFloat() / crop.height)
                }
                canvas.drawBitmap(decoded, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                return Rendered(output, dimensions.width, dimensions.height,
                    outputSize.width < crop.width || outputSize.height < crop.height)
            } catch (failure: Throwable) { output.recycle(); throw failure }
        } finally { decoded.recycle() }
    }

    suspend fun preview(sourcePath: String): CropPreview {
        var bitmap: Bitmap? = null
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val result = render(source(sourcePath), NormalizedCropRect.FULL, 0f, MAX_PREVIEW_PIXELS, MAX_PREVIEW_PIXELS)
                bitmap = result.bitmap
                currentCoroutineContext().ensureActive()
                CropPreview(result.bitmap, result.sourceWidth, result.sourceHeight)
            }
        } catch (failure: Throwable) { bitmap?.recycle(); throw failure }
    }

    suspend fun crop(context: Context, sourcePath: String, region: NormalizedCropRect,
        rotationDegrees: Float = 0f): CropOutput {
        var temporary: File? = null
        try {
            val metadata = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val result = render(source(sourcePath), region, rotationDegrees, CropGeometry.MAX_OUTPUT_PIXELS, MAX_DECODE_PIXELS)
                try {
                    val directory = File(context.cacheDir, "region-crops").apply { check(isDirectory || mkdirs()) }
                    val file = File.createTempFile("region-", ".jpg", directory).also { temporary = it }
                    // Bitmap.compress writes fresh pixel data, never the source's EXIF, GPS or comments.
                    file.outputStream().use { check(result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "无法保存裁切图片" } }
                    currentCoroutineContext().ensureActive()
                    CropOutput("", result.bitmap.width, result.bitmap.height, result.sourceWidth, result.sourceHeight, result.sampled)
                } finally { result.bitmap.recycle() }
            }
            val path = ImageFileHelper.copyToInternalStorage(context, Uri.fromFile(requireNotNull(temporary)))
            return metadata.copy(path = path)
        } finally { temporary?.delete() }
    }
}
