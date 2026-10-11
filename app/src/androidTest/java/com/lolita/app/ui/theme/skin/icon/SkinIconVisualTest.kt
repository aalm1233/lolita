package com.lolita.app.ui.theme.skin.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renders the public SkinIcon entry point through all seven real providers on Android.
 * No golden images: geometry is reviewed in the exported contact sheets; assertions
 * cover drawable bounds, caller sizing, monochrome tint and transparent tint behavior.
 * Uses the ComponentActivity supplied by the existing debug ui-test-manifest dependency.
 */
@RunWith(AndroidJUnit4::class)
class SkinIconVisualTest {
    private val keys = IconKey.entries
    private val skins = SkinType.entries
    private val density = 2f
    private val cellWidth = 104
    private val cellHeight = 88
    private val outputDirectory = File(
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
        "skin-icon-contact-sheets"
    )

    @Test
    fun renderAllSkinsAndVerifyOpticalBoundsAndTint() {
        outputDirectory.mkdirs()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val light = render(scenario, 24, 24, Color(0xFF44364C))
            saveContactSheet(light, "light", 0xFFF8F5FA.toInt(), 0xFF44364C.toInt())
            saveContactSheet(light, "overview-light", 0xFFF8F5FA.toInt(), 0xFF44364C.toInt(), overviewKeys)
            saveRaw(light, "light")
            verifyBoundsAndTint(light, 24, 24, 0x44364C)

            val dark = render(scenario, 24, 24, Color(0xFFF3EAF7))
            saveContactSheet(dark, "dark", 0xFF211D28.toInt(), 0xFFF3EAF7.toInt())
            saveContactSheet(dark, "overview-dark", 0xFF211D28.toInt(), 0xFFF3EAF7.toInt(), overviewKeys)
            saveRaw(dark, "dark")
            verifyBoundsAndTint(dark, 24, 24, 0xF3EAF7)

            // Theme previews request 18dp, navigation 26dp, and larger controls 32dp.
            for (side in listOf(18, 26, 32)) {
                val sized = render(scenario, side, side, Color(0xFF44364C))
                saveRaw(sized, "${side}dp")
                verifyBoundsAndTint(sized, side, side, 0x44364C)
                verifyCallerSize(light, sized, side)
                sized.recycle()
            }

            val rectangular = render(scenario, 40, 24, Color(0xFF44364C))
            verifyBoundsAndTint(rectangular, 40, 24, 0x44364C)
            verifyRectangularSlot(light, rectangular)
            rectangular.recycle()

            val transparent = render(scenario, 24, 24, Color(0x0044364C))
            val pixels = pixels(transparent)
            assertTrue("alpha=0 tint must paint no visible pixels", pixels.all { it ushr 24 == 0 })
            transparent.recycle()
            light.recycle()
            dark.recycle()
        }
    }

    private fun render(
        scenario: ActivityScenario<ComponentActivity>,
        widthDp: Int,
        heightDp: Int,
        tint: Color
    ): Bitmap {
        val compositionReady = CountDownLatch(1)
        lateinit var view: ComposeView
        scenario.onActivity { activity ->
            view = ComposeView(activity).apply {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density)) {
                        SideEffect { compositionReady.countDown() }
                        Layout(content = {
                            for (key in keys) {
                                for (skin in skins) {
                                    CompositionLocalProvider(LocalLolitaSkin provides getSkinConfig(skin)) {
                                        SkinIcon(key, Modifier.size(widthDp.dp, heightDp.dp), tint)
                                    }
                                }
                            }
                        }) { measurables, _ ->
                            val iconWidth = (widthDp * density).toInt()
                            val iconHeight = (heightDp * density).toInt()
                            val icons = measurables.map {
                                it.measure(Constraints.fixed(iconWidth, iconHeight))
                            }
                            layout(skins.size * cellWidth, keys.size * cellHeight) {
                                icons.forEachIndexed { index, icon ->
                                    icon.place(
                                        index % skins.size * cellWidth + (cellWidth - iconWidth) / 2,
                                        index / skins.size * cellHeight + (cellHeight - iconHeight) / 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        assertTrue("Compose content did not become ready", compositionReady.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = Bitmap.createBitmap(
            skins.size * cellWidth, keys.size * cellHeight, Bitmap.Config.ARGB_8888
        )
        scenario.onActivity {
            // Explicit offscreen size preserves every row, independently of device display size.
            view.measure(
                View.MeasureSpec.makeMeasureSpec(bitmap.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(bitmap.height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, bitmap.width, bitmap.height)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val opaquePixels: Int
    )

    private fun bounds(pixels: IntArray, width: Int, column: Int, row: Int): Bounds {
        var left = cellWidth
        var right = -1
        var top = cellHeight
        var bottom = -1
        var opaquePixels = 0
        for (y in 0 until cellHeight) {
            for (x in 0 until cellWidth) {
                val pixel = pixels[(row * cellHeight + y) * width + column * cellWidth + x]
                if (pixel ushr 24 == 255) opaquePixels++
                if (pixel ushr 24 > 8) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return Bounds(left, top, right, bottom, opaquePixels)
    }

    private fun verifyBoundsAndTint(bitmap: Bitmap, widthDp: Int, heightDp: Int, rgb: Int) {
        val pixels = pixels(bitmap)
        val side = (minOf(widthDp, heightDp) * density).toInt()
        // Smaller previews naturally have fewer solid pixels, especially the three-dot menu.
        // Scale the sample floor by area while preserving the 24dp-and-up requirement.
        val sampleScale = (minOf(widthDp, heightDp) / 24f).coerceAtMost(1f)
        val minOpaquePixels = kotlin.math.ceil(16f * sampleScale * sampleScale).toInt()
        val left = (cellWidth - side) / 2
        val top = (cellHeight - side) / 2
        for ((row, key) in keys.withIndex()) {
            for ((column, skin) in skins.withIndex()) {
                val bounds = bounds(pixels, bitmap.width, column, row)
                val label = "$skin/$key ${widthDp}×${heightDp}dp $bounds"
                assertTrue("$label has no drawable pixels", bounds.right >= bounds.left)
                assertTrue("$label has too few solid pixels to verify caller tint", bounds.opaquePixels >= minOpaquePixels)
                assertTrue("$label exceeds the centered square", bounds.left >= left &&
                    bounds.top >= top && bounds.right < left + side && bounds.bottom < top + side)
                // A full-frame plate is visually too heavy and obscures action semantics.
                assertTrue("$label lost its optical breathing room", bounds.right - bounds.left < side - 1 &&
                    bounds.bottom - bounds.top < side - 1)
            }
        }
        for ((index, pixel) in pixels.withIndex()) {
            // Software Canvas stores 8-bit premultiplied pixels. Repeated antialiased
            // coverage rounds intermediate channels, then getPixels unpremultiplies
            // them; dividing by fractional alpha magnifies that rounding. Fully
            // opaque pixels retain the original two-level rasterization tolerance.
            // Every cell above must supply enough opaque samples to exercise this.
            if (pixel ushr 24 == 255) {
                for (shift in listOf(0, 8, 16)) {
                    val actual = pixel shr shift and 255
                    val expected = rgb shr shift and 255
                    if (kotlin.math.abs(actual - expected) > 2) {
                        val x = index % bitmap.width
                        val y = index / bitmap.width
                        val label = "${skins[x / cellWidth]}/${keys[y / cellHeight]} " +
                            "pixel=0x${pixel.toUInt().toString(16)} expectedRGB=0x${rgb.toString(16)} " +
                            "at ($x,$y) channelShift=$shift"
                        assertTrue("Icon paint must follow caller tint: $label", false)
                    }
                }
            }
        }
    }

    private fun verifyCallerSize(reference: Bitmap, sized: Bitmap, sideDp: Int) {
        val referencePixels = pixels(reference)
        val sizedPixels = pixels(sized)
        for (row in keys.indices) {
            for (column in skins.indices) {
                val before = bounds(referencePixels, reference.width, column, row)
                val after = bounds(sizedPixels, sized.width, column, row)
                val scale = sideDp / 24f
                assertTrue("${skins[column]}/${keys[row]} ignores caller size ${sideDp}dp",
                    kotlin.math.abs((after.right - after.left) - (before.right - before.left) * scale) <= 3f &&
                        kotlin.math.abs((after.bottom - after.top) - (before.bottom - before.top) * scale) <= 3f)
            }
        }
    }

    private fun verifyRectangularSlot(reference: Bitmap, rectangular: Bitmap) {
        val expected = pixels(reference)
        val actual = pixels(rectangular)
        assertTrue("Rectangular slots must preserve centered 24dp drawings", expected.contentEquals(actual))
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun saveRaw(bitmap: Bitmap, name: String) {
        File(outputDirectory, "skin-icons-raw-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val overviewKeys = listOf(
        IconKey.Home, IconKey.Wishlist, IconKey.Outfit, IconKey.Stats, IconKey.Settings,
        IconKey.Add, IconKey.Delete, IconKey.Edit, IconKey.Search, IconKey.Sort,
        IconKey.Save, IconKey.Close, IconKey.Star, IconKey.Camera
    )

    private val overviewKeyLabels = listOf(
        "首页", "愿望单", "穿搭", "统计", "设置", "添加", "删除", "编辑", "搜索", "排序",
        "保存", "关闭", "星标", "相机"
    )

    private val overviewSkinLabels = listOf("甜美", "哥特", "中华", "古典", "海军", "田园", "维多利亚")

    private fun saveContactSheet(
        raw: Bitmap,
        name: String,
        background: Int,
        foreground: Int,
        selectedKeys: List<IconKey> = keys
    ) {
        val labelWidth = 178
        val headerHeight = 60
        val isOverview = selectedKeys == overviewKeys
        val output = Bitmap.createBitmap(
            raw.width + labelWidth, selectedKeys.size * cellHeight + headerHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(background)
        for ((row, key) in selectedKeys.withIndex()) {
            val sourceRow = keys.indexOf(key)
            canvas.drawBitmap(
                raw,
                Rect(0, sourceRow * cellHeight, raw.width, (sourceRow + 1) * cellHeight),
                Rect(labelWidth, headerHeight + row * cellHeight, output.width, headerHeight + (row + 1) * cellHeight),
                null
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            textSize = if (isOverview) 20f else 16f
            typeface = Typeface.create(if (isOverview) "sans-serif" else "monospace", Typeface.NORMAL)
        }
        for ((column, skin) in skins.withIndex()) {
            val label = if (isOverview) overviewSkinLabels[column]
                else if (skin == SkinType.DEFAULT) "SWEET" else skin.name
            val x = labelWidth + column * cellWidth + (cellWidth - paint.measureText(label)) / 2
            canvas.drawText(label, x, 34f, paint)
        }
        for ((row, key) in selectedKeys.withIndex()) {
            val label = if (isOverview) overviewKeyLabels[row] else key.name
            canvas.drawText(label, 10f, headerHeight + row * cellHeight + cellHeight / 2f + 6f, paint)
        }
        File(outputDirectory, "skin-icons-$name.png").outputStream().use {
            output.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        output.recycle()
    }
}
