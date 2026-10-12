package com.lolita.app.data.share

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lolita.app.domain.usecase.ShareCardContent
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

data class SharePalette(val background: Int, val surface: Int, val text: Int, val accent: Int,
    val radius: Float, val skinName: String, val serif: Boolean = false)

object ShareCardRenderer {
    /** One bounded bitmap per six items; all items survive large wardrobes without a giant allocation. */
    suspend fun render(context: Context, content: ShareCardContent, palette: SharePalette): List<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "share_cards").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val files = mutableListOf<File>()
        val chunks = content.items.chunked(6).ifEmpty { listOf(emptyList()) }
        try {
            chunks.forEachIndexed { index, items ->
                ensureActive()
                val header = layout(content.title, 52f, palette.text, 960, palette.serif)
                val labels = items.map { layout(listOfNotNull(it.name, it.brand).joinToString("\n"), 36f, palette.text, 740, palette.serif) }
                val heroHeight = if (index == 0 && content.images.isNotEmpty()) 650 else 0
                val height = 80 + header.height + 36 + heroHeight + labels.sumOf { maxOf(160, it.height + 40) + 24 } + 100
                val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(palette.background)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    var y = 50f
                    drawText(canvas, header, 60f, y); y += header.height + 36
                    if (heroHeight > 0) {
                        val width = 960f / content.images.size
                        content.images.forEachIndexed { photo, path ->
                            drawImage(context, canvas, path, RectF(60f + photo * width, y, 60f + (photo + 1) * width - 8, y + 620), palette, paint)
                        }
                        y += heroHeight
                    }
                    items.forEachIndexed { position, item ->
                        val label = labels[position]
                        val rowHeight = maxOf(160, label.height + 40).toFloat()
                        paint.color = palette.surface
                        canvas.drawRoundRect(RectF(60f, y, 1020f, y + rowHeight), palette.radius, palette.radius, paint)
                        drawImage(context, canvas, item.image, RectF(76f, y + 16, 204f, y + 144), palette, paint)
                        drawText(canvas, label, 240f, y + 20)
                        y += rowHeight + 24
                    }
                    paint.color = palette.accent
                    canvas.drawRect(60f, y + 12, 1020f, y + 16, paint)
                    drawText(canvas, layout("我的Lolita · ${palette.skinName}  ${index + 1}/${chunks.size}", 26f, palette.text, 960, palette.serif), 60f, y + 34)
                    ensureActive()
                    val file = File(directory, "${UUID.randomUUID()}.png")
                    files += file
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "分享图片写入失败" } }
                } finally { bitmap.recycle() }
            }
            files
        } catch (e: Exception) {
            files.forEach { it.delete() }
            throw e
        }
    }
    private fun layout(text: String, size: Float, color: Int, width: Int, serif: Boolean): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(if (serif) "serif" else "sans-serif", Typeface.NORMAL)
        }
        val display = if (text.length > 300) text.take(299) + "…" else text
        return StaticLayout.Builder.obtain(display, 0, display.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }
    private fun drawText(canvas: Canvas, text: StaticLayout, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore()
    }
    private suspend fun drawImage(context: Context, canvas: Canvas, path: String?, rect: RectF, palette: SharePalette, paint: Paint) {
        paint.color = palette.surface
        canvas.drawRoundRect(rect, palette.radius, palette.radius, paint)
        val result = path?.let { context.imageLoader.execute(ImageRequest.Builder(context).data(it).size(1000, 1000).allowHardware(false).build()) }
        val bitmap = (result as? SuccessResult)?.drawable as? BitmapDrawable
        if (bitmap == null) {
            val placeholder = layout("暂无图片", 24f, palette.text, rect.width().toInt(), false)
            drawText(canvas, placeholder, rect.left + 8, rect.centerY())
            return
        }
        val image = bitmap.bitmap
        val scale = minOf(rect.width() / image.width, rect.height() / image.height)
        val width = image.width * scale; val height = image.height * scale
        canvas.drawBitmap(image, null, RectF(rect.centerX() - width / 2, rect.centerY() - height / 2,
            rect.centerX() + width / 2, rect.centerY() + height / 2), paint)
    }
}
