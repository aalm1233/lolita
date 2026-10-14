package com.lolita.app.ui.theme.skin.component

import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.getSkinConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Checks packaged Android resources without touching application data or settings. */
@RunWith(AndroidJUnit4::class)
class SkinArtBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sevenPackagedIllustrationsDecodeAndLeaveTheMiddleTransparent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = listOf("sweet", "gothic", "chinese", "classic", "navy", "country", "victorian")
        val fingerprints = mutableSetOf<Int>()
        for (name in names) {
            val resourceName = "bg_art_$name"
            val id = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            assertNotEquals("缺少独立背景素材 $resourceName", 0, id)
            val bitmap = BitmapFactory.decodeResource(context.resources, id)
            assertNotNull("背景必须是 Android 能解码的 PNG：$resourceName", bitmap)
            bitmap!!
            try {
                assertTrue("背景素材必须带透明通道：$resourceName", bitmap.hasAlpha())
                assertTrue("背景素材不能是微小占位图：$resourceName", bitmap.width >= 512 && bitmap.height >= 512)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("每套必须是独立插画，不能复用其他皮肤位图：$resourceName", fingerprints.add(pixels.contentHashCode()))
                val transparent = pixels.count { Color.alpha(it) <= 8 }
                val visible = pixels.count { Color.alpha(it) >= 32 }
                assertTrue("至少 65% 画面保持留白：$resourceName", transparent.toDouble() / pixels.size >= 0.65)
                assertTrue("边角需要实际插画，不能全透明：$resourceName", visible.toDouble() / pixels.size >= 0.005)

                // The central half is where labels, monetary amounts and photos need a clean surface.
                var middleVisible = 0
                var middleCount = 0
                for (y in bitmap.height / 4 until bitmap.height * 3 / 4) {
                    for (x in bitmap.width / 4 until bitmap.width * 3 / 4) {
                        middleCount++
                        if (Color.alpha(pixels[y * bitmap.width + x]) > 8) middleVisible++
                    }
                }
                assertTrue("中央内容区不能有白底或密集装饰：$resourceName", middleVisible.toDouble() / middleCount <= 0.01)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun realIllustrationsPreserveThemeSurfaceInBothModesAndStayStatic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chosen = mutableStateOf(SkinType.DEFAULT to false)
        val landscape = mutableStateOf(false)
        val draws = AtomicInteger()
        val output = File(context.filesDir, "skin-art-background-test/${UUID.randomUUID()}").apply { mkdirs() }
        compose.setContent {
            val (type, dark) = chosen.value
            val skin = getSkinConfig(type)
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalLolitaSkin provides skin,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(1f),
            ) {
                MaterialTheme(colorScheme = if (dark) skin.darkColorScheme else skin.lightColorScheme) {
                    val width = if (landscape.value) 360.dp else 240.dp
                    val height = if (landscape.value) 240.dp else 360.dp
                    Box(Modifier.size(width, height).background(MaterialTheme.colorScheme.background).testTag("art-surface")) {
                        SkinArtBackground(Modifier.fillMaxSize().drawWithContent {
                            draws.incrementAndGet()
                            drawContent()
                        })
                    }
                }
            }
        }
        for (skinType in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { chosen.value = skinType to dark }
            val skin = getSkinConfig(skinType)
            val base = (if (dark) skin.darkColorScheme else skin.lightColorScheme).background.toArgb()
            var snapshot: Bitmap? = null
            compose.waitUntil(15_000) {
                snapshot = compose.onNodeWithTag("art-surface").captureToImage().asAndroidBitmap()
                countChanged(snapshot!!, base) > 100
            }
            val bitmap = snapshot!!
            val name = "${skinType.name.lowercase()}-${if (dark) "dark" else "light"}"
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertCenterPreservesBase(bitmap, base, name)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
                .assertCountEquals(0)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
                .assertCountEquals(0)

            // Advance real Compose frames after Coil has settled: static art must not request them.
            compose.waitForIdle()
            val settledDraws = compose.runOnIdle { draws.get() }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("静态背景不应随时间重新绘制：$name", settledDraws, draws.get())
            }
            if (skinType == SkinType.DEFAULT && !dark) {
                // Resize the already-loaded real component instead of substituting test artwork.
                compose.runOnIdle { landscape.value = true }
                var wideSnapshot: Bitmap? = null
                compose.waitUntil(15_000) {
                    wideSnapshot = compose.onNodeWithTag("art-surface").captureToImage().asAndroidBitmap()
                    wideSnapshot!!.width > wideSnapshot!!.height && countChanged(wideSnapshot!!, base) > 100
                }
                val wide = wideSnapshot!!
                File(output, "default-light-landscape.png").outputStream().use {
                    wide.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertCenterPreservesBase(wide, base, "default-light-landscape")
                compose.runOnIdle { landscape.value = false }
            }
        }
    }

    @Test
    fun missingImageCanFailSilentlyWhileDefaultFailureBehaviorIsPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = File(context.cacheDir, "missing-art-${UUID.randomUUID()}.png")
        val base = androidx.compose.ui.graphics.Color(0xFF345678)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Row(Modifier.size(200.dp, 100.dp)) {
                    Box(Modifier.size(100.dp).background(base)) {
                        LolitaShimmerImage(missing, null, Modifier.fillMaxSize(), shimmerEnabled = false, circularRevealEnabled = false)
                    }
                    Box(Modifier.size(100.dp).background(base).testTag("silent-failure")) {
                        LolitaShimmerImage(missing, null, Modifier.fillMaxSize(), shimmerEnabled = false,
                            circularRevealEnabled = false, showFailurePlaceholder = false)
                    }
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("?").fetchSemanticsNodes().size == 1 }
        val silent = compose.onNodeWithTag("silent-failure").captureToImage().asAndroidBitmap()
        assertEquals("背景加载失败时保持原表面，无问号/渐变/闪烁", 0, countChanged(silent, base.toArgb()))
    }

    private fun countChanged(bitmap: Bitmap, base: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (!sameRgb(bitmap.getPixel(x, y), base)) count++
        }
        return count
    }

    private fun assertCenterPreservesBase(bitmap: Bitmap, base: Int, name: String) {
        var unchangedMiddle = 0
        var middleCount = 0
        for (y in bitmap.height / 4 until bitmap.height * 3 / 4) {
            for (x in bitmap.width / 4 until bitmap.width * 3 / 4) {
                middleCount++
                if (sameRgb(bitmap.getPixel(x, y), base)) unchangedMiddle++
            }
        }
        assertTrue("真实图层的中央应保留主题底色：$name", unchangedMiddle.toDouble() / middleCount >= 0.98)
    }

    private fun sameRgb(first: Int, second: Int): Boolean =
        kotlin.math.abs(Color.red(first) - Color.red(second)) <= 2 &&
            kotlin.math.abs(Color.green(first) - Color.green(second)) <= 2 &&
            kotlin.math.abs(Color.blue(first) - Color.blue(second)) <= 2
}
