package com.lolita.app.data

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.*
import com.lolita.app.ui.component.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Controlled local fixture transport; no paid generation or model-quality assertion. */
class DisplayComparisonScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var f: MaterialTestFixture
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var expectedResult: String? = null
    @Before fun setup() { f = MaterialTestFixture(); runBlocking { f.init() } }
    @After fun close() { runBlocking { f.close() } }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        // Check decoded pixels, not just the presence of an image semantics node.
        for ((description,path) in listOfNotNull("本次原图完整预览" to f.source,expectedResult?.let{"生成图完整预览" to it})) {
          val decoded = android.graphics.BitmapFactory.decodeFile(path)
          val expected = decoded.getPixel(decoded.width / 2, decoded.height / 2); decoded.recycle()
          compose.waitUntil(10000) {
            compose.mainClock.advanceTimeByFrame()
            val images=compose.onAllNodesWithContentDescription(description)
            images.fetchSemanticsNodes().indices.all { index ->
                val bitmap=images[index].captureToImage().asAndroidBitmap()
                val actual=bitmap.getPixel(bitmap.width/2,bitmap.height/2);bitmap.recycle()
                listOf(android.graphics.Color.red(actual)-android.graphics.Color.red(expected), android.graphics.Color.green(actual)-android.graphics.Color.green(expected), android.graphics.Color.blue(actual)-android.graphics.Color.blue(expected)).all{kotlin.math.abs(it)<20}
            }
          }
        }
        compose.onAllNodesWithText("图片无法读取").assertCountEquals(0)
        val dir = File(context.filesDir, "display-comparison-native-20261028").apply { mkdirs() }
        compose.onNodeWithTag("displayComparison").captureToImage().asAndroidBitmap().let { im ->
            File(dir, "$name.png").outputStream().use { im.compress(Bitmap.CompressFormat.PNG, 100, it) }; im.recycle()
        }
    }
    @Test fun frozenProductInputSkipsTemplateAndMissingResultNeverFallsBackToInput() = runBlocking {
        val item = f.categoryItems().first()
        val batch = MaterialBatchIdentity(UUID.randomUUID().toString(), "对照批次")
        f.coordinator.submitDisplayBatch(batch, UUID.randomUUID().toString(), listOf(BatchDisplayInput(item.id,
            f.coordinator.prepareDisplayInput(item.id, item.imageUrls.first()), item.imageUrls.first(), "", 0)), ImageGenerationProvider.SEEDREAM)
        val task = f.db.imageTaskDao().getAll().single()
        assertEquals(2, task.sourceImagePaths.size)
        val before = requireNotNull(DisplayImageComparison.from(task, null))
        assertEquals(task.sourceImagePaths.last(), before.input)
        assertNotEquals(task.sourceImagePaths.first(), before.input)
        assertNull(before.result)
        // Updating the album must not redirect historical comparison to another image.
        f.db.itemDao().updateItem(item.copy(imageUrls = emptyList()))
        assertEquals(before, DisplayImageComparison.from(task, null))
        assertNull(DisplayImageComparison.from(task.copy(kind = ImageTaskKind.SINGLE_ITEM_MATERIAL), null))
        compose.setContent { LolitaTheme { DisplayComparisonViewer(before, "原图保留，结果未返回") {} } }
        compose.onNodeWithText("暂无可用生成图").assertIsDisplayed()
        compose.onNodeWithContentDescription("本次原图完整预览").assertIsDisplayed()
        compose.onNodeWithContentDescription("生成图完整预览").assertDoesNotExist()
    }
    @Test fun compareModesSynchronizeZoomAndPanAndRestoreAcrossSevenSkins() {
        val result = File(f.root, "controlled-result.png").apply { writeBytes(f.transparent) }.absolutePath
        expectedResult=result
        val comparison = DisplayImageComparison("controlled", f.source, result, f.source)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config) { LolitaTheme(skin.value, dark.value) {
                DisplayComparisonViewer(comparison, "奶油花园 · 同一物品对照") {}
            } }
        }
        compose.onNodeWithContentDescription("本次原图完整预览").assertIsDisplayed()
        compose.onNodeWithContentDescription("生成图完整预览").assertIsDisplayed()
        compose.onNodeWithText("放大", useUnmergedTree = true).performClick()
        fun transform() = compose.onNodeWithTag("comparisonTransform").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(transform().startsWith("缩放150%"))
        compose.onNodeWithTag("comparison本次原图").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(30f, 25f), 300) }
        val moved = transform(); assertTrue(moved.endsWith("已移动"))
        compose.onNodeWithTag("compareMode2").performClick()
        compose.onNodeWithContentDescription("本次原图完整预览").assertDoesNotExist()
        assertEquals(moved, transform())
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("compareMode2").assertIsSelected(); assertEquals(moved, transform())
        compose.onNodeWithTag("compareMode1").performClick()
        compose.onNodeWithContentDescription("生成图完整预览").assertDoesNotExist()
        assertEquals(moved, transform())
        compose.onNodeWithText("还原").performClick(); assertTrue(transform().startsWith("缩放100%"))
        compose.onNodeWithTag("compareMode0").performClick()
        for (night in listOf(false, true)) for (type in SkinType.entries) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            compose.onNodeWithTag("comparison本次原图").assertIsDisplayed()
            compose.onNodeWithTag("comparison生成图").assertIsDisplayed()
            compose.onNodeWithText("还原").assertIsDisplayed()
            snapshot("viewer-${type.name.lowercase()}-${if(night)"dark" else "light"}")
        }
    }
    @Test fun smallScreenLargeFontKeepsBothImagesAndControlsVisible() {
        val config = context.resources.configuration
        Assume.assumeTrue(config.screenWidthDp <= 320 && config.fontScale >= 1.99f)
        val original = File(f.root, "full-original.png").apply { writeBytes(f.opaque) }.absolutePath
        compose.setContent { LolitaTheme { DisplayComparisonViewer(DisplayImageComparison("small", f.source, null, original), "长名称物品与完整原图对照") {} } }
        compose.onNodeWithTag("comparison本次原图").assertIsDisplayed()
        compose.onNodeWithTag("comparison生成图").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("comparison本次原图").fetchSemanticsNode().boundsInRoot.height > 100)
        compose.onNodeWithText("放大").assertIsDisplayed().performClick()
        compose.onNodeWithText("还原").assertIsDisplayed().performClick()
        compose.onNodeWithText("查看完整原图").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("关闭图片预览").performClick()
        compose.onNodeWithTag("compareMode0").assertIsSelected()
        snapshot("viewer-small-font2-missing")
    }
}
