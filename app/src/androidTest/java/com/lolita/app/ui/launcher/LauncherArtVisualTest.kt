package com.lolita.app.ui.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Actual Android resource inflation and software Canvas drawing, with no XML/path simulation. */
@RunWith(AndroidJUnit4::class)
class LauncherArtVisualTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density
    private val sizes = listOf(24, 48)

    private enum class Mask { CIRCLE, ROUNDED_SQUARE }
    private data class Theme(val name: String, val foreground: Int, val background: Int)
    private data class Row(val name: String, val images: List<Bitmap>)

    @Test
    fun renderRealLauncherLayersMasksAndThemedIcons() {
        val rows = mutableListOf<Row>()
        val inspectionSize = 324 // 3 pixels per 108-unit viewport; tolerance is one raster pixel.
        val foreground = draw(load(R.drawable.ic_launcher_foreground), inspectionSize)
        val monochrome = draw(load(R.drawable.ic_launcher_monochrome), inspectionSize)
        try {
            rows += Row("彩色前景 · 108画布", sizes.map { draw(load(R.drawable.ic_launcher_foreground), px(it)) })
            rows += Row("单色原层 · 108画布", sizes.map { draw(load(R.drawable.ic_launcher_monochrome), px(it)) })
            verifySafetyCircle(foreground, "foreground")
            verifySafetyCircle(monochrome, "monochrome")
            verifyMonochromeNegativeSpace(monochrome)
            for ((name, id) in listOf("系统 adaptive" to R.mipmap.ic_launcher,
                "系统 round" to R.mipmap.ic_launcher_round)) {
                // Drawing the inflated AdaptiveIconDrawable itself exercises the platform mask.
                rows += Row(name, sizes.map { side ->
                    val adaptive = loadAdaptive(id)
                    assertNotNull("$name foreground", adaptive.foreground)
                    assertNotNull("$name background", adaptive.background)
                    if (Build.VERSION.SDK_INT >= 33) {
                        assertNotNull("$name API 33+ monochrome layer missing", adaptive.monochrome)
                    }
                    draw(adaptive, px(side)).also { verifyNonempty(it, name) }
                })
            }
            for (mask in Mask.entries) {
                rows += Row("彩色 · ${mask.label()}", sizes.map { side ->
                    val adaptive = loadAdaptive(R.mipmap.ic_launcher)
                    verifyMaskDoesNotCutForeground(adaptive.foreground, px(side), mask)
                    drawMasked(adaptive, px(side), mask)
                })
            }
            val themes = listOf(
                Theme("浅色主题", Color.rgb(82, 48, 103), Color.rgb(237, 221, 244)),
                Theme("暗色主题", Color.rgb(230, 211, 163), Color.rgb(47, 40, 31))
            )
            for (theme in themes) {
                for (mask in Mask.entries) {
                    rows += Row("${theme.name} · ${mask.label()}", sizes.map { side ->
                        val adaptive = loadAdaptive(R.mipmap.ic_launcher)
                        val layer = if (Build.VERSION.SDK_INT >= 33) {
                            requireNotNull(adaptive.monochrome) { "API 33+ launcher monochrome missing" }
                        } else {
                            load(R.drawable.ic_launcher_monochrome)
                        }
                        layer.mutate().setTint(theme.foreground)
                        verifyMaskDoesNotCutForeground(layer, px(side), mask)
                        drawMasked(adaptive, px(side), mask, layer, theme.background).also {
                            verifyTheme(it, theme, mask)
                        }
                    })
                }
            }
        } finally {
            try {
                // Keep completed rows as diagnostic evidence even when an assertion fails.
                if (rows.isNotEmpty()) exportContactSheet(rows)
            } finally {
                foreground.recycle()
                monochrome.recycle()
                rows.flatMap { it.images }.forEach(Bitmap::recycle)
            }
        }
    }

    private fun load(id: Int): Drawable = requireNotNull(context.getDrawable(id)).mutate()

    private fun loadAdaptive(id: Int): AdaptiveIconDrawable {
        val drawable = load(id)
        assertTrue("Launcher resource must inflate as AdaptiveIconDrawable on API 26+", drawable is AdaptiveIconDrawable)
        return drawable as AdaptiveIconDrawable
    }

    private fun px(dp: Int): Int = (dp * density).roundToInt()

    private fun bitmap(side: Int): Bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)

    private fun draw(drawable: Drawable, side: Int): Bitmap = bitmap(side).also {
        drawable.setBounds(0, 0, side, side)
        drawable.draw(Canvas(it))
    }

    private fun maskPath(side: Int, mask: Mask): Path = Path().apply {
        val s = side.toFloat()
        when (mask) {
            Mask.CIRCLE -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            Mask.ROUNDED_SQUARE -> addRoundRect(RectF(0f, 0f, s, s), s * .22f, s * .22f, Path.Direction.CW)
        }
    }

    private fun drawLayer(canvas: Canvas, layer: Drawable, side: Int) {
        // Platform adaptive icons inset each layer by 25%: 108 viewport units cover 72 visible units.
        val inset = (side * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
        layer.setBounds(-inset, -inset, side + inset, side + inset)
        layer.draw(canvas)
    }

    private fun drawMasked(
        adaptive: AdaptiveIconDrawable,
        side: Int,
        mask: Mask,
        themedLayer: Drawable? = null,
        themedBackground: Int = Color.TRANSPARENT
    ): Bitmap = bitmap(side).also {
        val canvas = Canvas(it)
        canvas.clipPath(maskPath(side, mask))
        if (themedLayer == null) {
            drawLayer(canvas, adaptive.background, side)
            drawLayer(canvas, adaptive.foreground, side)
        } else {
            canvas.drawColor(themedBackground)
            drawLayer(canvas, themedLayer, side)
        }
    }

    private fun verifySafetyCircle(image: Bitmap, name: String) {
        verifyNonempty(image, name)
        val center = image.width / 2.0
        val radius = image.width * 33.0 / 108.0
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (Color.alpha(image.getPixel(x, y)) > 8) {
                count++
                assertTrue("$name painted outside 66-unit safe circle at ($x,$y)",
                    hypot(x + .5 - center, y + .5 - center) <= radius + 1.0)
            }
        }
        assertTrue("$name unexpectedly became a filled plate", count < image.width * image.height / 3)
    }

    private fun verifyNonempty(image: Bitmap, name: String) {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        assertTrue("$name contains no opaque art", pixels.count { Color.alpha(it) > 200 } > image.width)
    }

    private fun verifyMonochromeNegativeSpace(image: Bitmap) {
        fun alphaAt(x: Float, y: Float): Int = Color.alpha(image.getPixel(
            (x / 108 * image.width).toInt(), (y / 108 * image.height).toInt()))
        // Semantic landmarks: doors have openings; the waist bow is transparent, not a dark fill.
        for ((x, y) in listOf(34.5f to 52f, 73.5f to 52f, 54f to 52f, 51.5f to 60f, 56.5f to 60f)) {
            assertTrue("Monochrome opening/bow ($x,$y) must be alpha negative space", alphaAt(x, y) < 32)
        }
        for ((x, y) in listOf(30.5f to 52f, 77.5f to 52f, 54f to 46f, 54f to 68f)) {
            assertTrue("Monochrome wardrobe/dress ($x,$y) disappeared", alphaAt(x, y) > 200)
        }
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        assertTrue("Monochrome has color-painted pixels instead of an alpha-only white layer",
            pixels.filter { Color.alpha(it) > 200 }.all {
                Color.red(it) > 250 && Color.green(it) > 250 && Color.blue(it) > 250
            })
    }

    private fun verifyMaskDoesNotCutForeground(layer: Drawable, side: Int, mask: Mask) {
        val before = bitmap(side)
        val after = bitmap(side)
        try {
            drawLayer(Canvas(before), layer, side)
            val clipped = Canvas(after)
            clipped.clipPath(maskPath(side, mask))
            drawLayer(clipped, layer, side)
            for (y in 0 until side) for (x in 0 until side) {
                val original = Color.alpha(before.getPixel(x, y))
                if (original > 32) {
                    assertTrue("${mask.label()} cuts art at ${side}px ($x,$y)",
                        Color.alpha(after.getPixel(x, y)) >= original - 8)
                }
            }
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    private fun verifyTheme(image: Bitmap, theme: Theme, mask: Mask) {
        assertTrue("${theme.name}/${mask.label()} must preserve a transparent mask corner",
            Color.alpha(image.getPixel(0, 0)) == 0)
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        assertTrue("${theme.name} foreground tint did not render", pixels.any { it == theme.foreground })
        assertTrue("${theme.name} background tint did not render", pixels.any { it == theme.background })
        assertTrue("${theme.name} must contain only themed solid colors and antialiased blends",
            pixels.filter { Color.alpha(it) == 255 }.all { pixel ->
                fun inRange(channel: (Int) -> Int): Boolean {
                    val a = channel(theme.foreground)
                    val b = channel(theme.background)
                    return channel(pixel) in (minOf(a, b) - 2)..(maxOf(a, b) + 2)
                }
                inRange(Color::red) && inRange(Color::green) && inRange(Color::blue)
            })
    }

    private fun Mask.label(): String = when (this) {
        Mask.CIRCLE -> "圆形"
        Mask.ROUNDED_SQUARE -> "圆角方形"
    }

    private fun exportContactSheet(rows: List<Row>) {
        val largest = px(48)
        val rowHeight = largest * 2 + 70
        val cellWidth = largest * 3 + 60
        val sheet = Bitmap.createBitmap(cellWidth * 2, 94 + rowHeight * rows.size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(248, 246, 249))
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(63, 44, 69)
                textSize = 20f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            canvas.drawText("桌面衣橱标识 · Android真实Drawable与Canvas", 16f, 26f, text)
            text.textSize = 15f
            canvas.drawText("API ${Build.VERSION.SDK_INT} · density=$density · 左24dp / 右48dp · 原尺寸 + 2倍像素审阅", 16f, 52f, text)
            canvas.drawText("上两行保留108画布透明留白；adaptive行包含平台缩放。透明区域以棋盘显示。", 16f, 76f, text)
            rows.forEachIndexed { rowIndex, row ->
                row.images.forEachIndexed { column, image ->
                    val left = column * cellWidth
                    val top = 94 + rowIndex * rowHeight
                    canvas.drawText("${row.name} · ${sizes[column]}dp", left + 16f, top + 24f, text)
                    val y = top + 40
                    checkerboard(canvas, left + 16, y, image.width)
                    canvas.drawBitmap(image, left + 16f, y.toFloat(), null)
                    val zoomX = left + largest + 36
                    checkerboard(canvas, zoomX, y, image.width * 2)
                    canvas.drawBitmap(image, null, Rect(zoomX, y, zoomX + image.width * 2, y + image.height * 2),
                        Paint().apply { isFilterBitmap = false })
                }
            }
            val directory = File(context.filesDir, "launcher-art-test")
            assertTrue("Cannot create launcher art export directory", directory.isDirectory || directory.mkdirs())
            File(directory, "launcher-art-contact-sheet.png").outputStream().use {
                assertTrue("Contact sheet PNG export failed", sheet.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            sheet.recycle()
        }
    }

    private fun checkerboard(canvas: Canvas, left: Int, top: Int, side: Int) {
        val paint = Paint()
        val step = 8
        for (y in 0 until side step step) for (x in 0 until side step step) {
            paint.color = if ((x / step + y / step) % 2 == 0) Color.WHITE else Color.rgb(225, 222, 228)
            canvas.drawRect((left + x).toFloat(), (top + y).toFloat(),
                (left + minOf(x + step, side)).toFloat(), (top + minOf(y + step, side)).toFloat(), paint)
        }
    }
}
