package com.lolita.app.ui.screen.mannequin

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real bundled head pixels and screen; product and service boundaries are controlled, never paid. */
@RunWith(AndroidJUnit4::class)
class WigHeadTemplateVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appendedCandidateCanBeChosenAndZoomedAcrossSkinsAndLargeText() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "head-visual-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val source = File(root, "controlled-product.png")
        Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.GRAY)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val catalog = WigHeadTemplateCatalog(context)
        val store = ViewModelStore()
        lateinit var vm: MannequinViewModel
        var paid = 0
        val skin = mutableStateOf(SkinType.DEFAULT)
        val dark = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            vm = MannequinViewModel(SavedStateHandle(mapOf("itemId" to 1L)),
                item = flowOf(Item(id = 1, brandId = 1, categoryId = 1, name = "受控假发图", description = "", status = ItemStatus.OWNED, imageUrls = listOf(source.path))),
                categories = flowOf(listOf(Category(id = 1, name = "全头假发", categorySemantic = CategorySemantic.FULL_WIG))),
                readService = { ImageServiceStatus(ImageGenerationProvider.SEEDREAM, false) }, selectService = {}, serviceChanges = flowOf(0),
                saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = flowOf(emptyList()), readRetryTask = { null },
                validateLegacy = { false }, appendLegacy = { _, _ -> }, discardLegacy = {}, submitTask = { _, _ -> paid++; "unexpected" },
                readHeadTemplates = { catalog.templates }, snapshotHead = catalog::snapshot,
                readHeadPreference = { null }, writeHeadPreference = { WigHeadTemplateCatalog.writePreference(context, it) }, pinCropReferences = { { } })
            store.put("vm", vm)
        }
        try {
            compose.waitUntil(10_000) { !vm.uiState.value.isLoadingHead && vm.uiState.value.selectedHeadTemplate != null && vm.uiState.value.keyLoaded }
            compose.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = 320
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) {
                    LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.size(320.dp, 640.dp)) { MannequinScreen({}, vm) }
                        }
                    }
                }
            }
            compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText("选择头模 · 单选"))
            compose.onAllNodes(hasText("候选", substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasText("评审", substring = true)).assertCountEquals(0)
            compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText("蓝紫妆"))
            compose.onNodeWithText("蓝紫妆").assertIsDisplayed()
            val name = "素面·无五官"
            fun reveal() {
                compose.onNodeWithTag("mannequinWorkspace").performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            }
            reveal()
            compose.onNodeWithText(name).onParent().onChildren().filterToOne(hasText("选用此头模")).performClick()
            compose.waitUntil(10_000) { vm.uiState.value.selectedHeadTemplate?.id == "faceless-ivory" && !vm.uiState.value.isLoadingHead }
            assertEquals("faceless-ivory", WigHeadTemplateCatalog.readPreference(context)!!.id)
            val output = File(app.filesDir, "faceless-head-visuals").apply { mkdirs() }
            fun capture(file: String) {
                // Dialog uses a separate Android window; root capture can read the activity below it.
                val bitmap = if (file == "full-preview.png") {
                    compose.waitForIdle()
                    instrumentation.waitForIdleSync()
                    // Semantics may exist before Coil draws in the separate Dialog window.
                    // The light-gray reference must be visibly rendered, not a black viewer.
                    val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                    var visible: Bitmap? = null
                    while (visible == null && android.os.SystemClock.uptimeMillis() < deadline) {
                        compose.mainClock.advanceTimeByFrame()
                        compose.waitForIdle()
                        val frame = instrumentation.uiAutomation.takeScreenshot()
                        val center = frame.getPixel(frame.width / 2, frame.height / 2)
                        if (android.graphics.Color.red(center) > 90 &&
                            android.graphics.Color.green(center) > 90 &&
                            android.graphics.Color.blue(center) > 90) visible = frame
                        else { frame.recycle(); android.os.SystemClock.sleep(100) }
                    }
                    requireNotNull(visible) { "Full head preview never rendered the actual image" }
                } else compose.onRoot().captureToImage().asAndroidBitmap()
                bitmap.also {
                    File(output, file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (currentSkin in SkinType.entries) for (night in listOf(false, true)) {
                compose.runOnIdle { skin.value = currentSkin; dark.value = night }
                reveal()
                compose.onNodeWithText("已选用").assertIsDisplayed()
                capture("${currentSkin.name.lowercase()}-${if (night) "dark" else "light"}.png")
            }
            for (night in listOf(false, true)) {
                compose.runOnIdle { skin.value = SkinType.DEFAULT; dark.value = night; scale.floatValue = 2f }
                reveal()
                compose.onNodeWithText("已选用").assertIsDisplayed()
                capture("large-${if (night) "dark" else "light"}.png")
            }
            compose.onNodeWithContentDescription("放大头模：$name").performClick()
            compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("图片预览 1").onFirst().assertIsDisplayed()
            capture("full-preview.png")
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
            assertEquals(0, paid)
            assertEquals("faceless-ivory", vm.uiState.value.selectedHeadTemplate!!.id)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            root.deleteRecursively()
        }
    }
}
