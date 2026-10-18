package com.lolita.app.ui.screen.item

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.item.recognition.ItemRecognitionViewModel
import com.lolita.app.ui.screen.common.SizeOptionsViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Real Screen -> Panel -> recognition/editor VMs -> Room. No provider request is submitted. */
class WardrobeRecognitionFlowTest {
    private val compose = createComposeRule()
    private val nativeViewport = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val oldDensity = resources.displayMetrics.densityDpi
            val oldScale = resources.configuration.fontScale
            val oldNightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val oldNightMode = requireNotNull(Regex("Night mode: (auto|no|yes|custom)").find(shell("cmd uimode night")))
                .groupValues[1]
            val densityOverride = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
            val savedScale = shell("settings get system font_scale").trim()
            val dpi = (resources.displayMetrics.widthPixels / 320f * 160).toInt()
            val night = description.methodName.contains("Dark")
            try {
                shell("wm density $dpi")
                shell("settings put system font_scale 2.0")
                shell("cmd uimode night ${if (night) "yes" else "no"}")
                awaitConfiguration(dpi, 2f, if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                dark.value = night
                base.evaluate()
            } finally {
                if (savedScale == "null") shell("settings delete system font_scale")
                else {
                    require(savedScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
                    shell("settings put system font_scale $savedScale")
                }
                shell(densityOverride?.let { "wm density $it" } ?: "wm density reset")
                shell("cmd uimode night $oldNightMode")
                awaitConfiguration(oldDensity, oldScale, oldNightMask)
            }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(nativeViewport).around(compose)
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: Fixture
    private val active = mutableStateOf<Session?>(null)
    private val visible = mutableStateOf(true)
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private var saved = 0

    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun awaitConfiguration(dpi: Int, scale: Float, night: Int) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val resources = instrumentation.targetContext.resources
            if (resources.displayMetrics.densityDpi == dpi && kotlin.math.abs(resources.configuration.fontScale - scale) < 0.01f &&
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == night) return
            Thread.sleep(25)
        }
        error("Native density/font/night configuration did not become $dpi dpi / $scale / $night")
    }

    private class Owner : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private data class Session(val owner: Owner, val editor: ItemEditViewModel, val recognition: ItemRecognitionViewModel)

    private inner class Fixture {
        val root = File(target.cacheDir, "recognition-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "keys").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(root, "ui.preferences_pb") }))
        val items = ItemRepository(db.itemDao(), database = db)
        val brands = BrandRepository(db.brandDao(), db.itemDao(), db)
        val categories = CategoryRepository(db.categoryDao(), db.itemDao(), db)
        val drafts = ItemDraftRepository(db, items)
        val repository = RecognitionRepository(db, context)
        val owners = mutableListOf<Owner>()
        var transportCalls = 0
        val coordinator = RecognitionCoordinator(db, context, scope,
            readKey = { error("This UI regression must never read a provider key") }, keyRevision = { 0 },
            recognize = { _, _, _ -> transportCalls++; error("No network is allowed in this UI fixture") }, providerReady = { true })
        lateinit var album: String
        lateinit var region: String
        lateinit var taskId: String

        suspend fun image(source: Boolean): String {
            val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (source) Color.GREEN else Color.BLUE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f }
            if (source) {
                canvas.drawText("A / S : 60 cm", 12f, 42f, paint)
                canvas.drawText("B / M : 70 cm", 12f, 85f, paint)
            }
            val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
            bitmap.recycle()
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            coordinator.withRestorePaused { }
            album = image(false); region = image(true)
            db.brandDao().insertBrand(Brand(id = 1, name = "测试保留品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "识图测试假发", categorySemantic = CategorySemantic.FULL_WIG))
            db.itemDao().insertItem(Item(id = 1, brandId = 1, categoryId = 1, name = "手工原名", status = ItemStatus.OWNED,
                description = "手工备注不得被识图替换", imageUrls = listOf(album)))
        }

        suspend fun session(): Session = withContext(Dispatchers.Main) {
            val owner = Owner().also(owners::add)
            val editor = ItemEditViewModel(items, brands, categories,
                CoordinateRepository(db.coordinateDao(), db.itemDao(), db), PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                StyleRepository(db.styleDao(), db.itemDao(), db), SeasonRepository(db.seasonDao(), db.itemDao(), db),
                SourceRepository(db.sourceDao(), db.itemDao(), db), LocationRepository(db.locationDao(), db.itemDao()), drafts, StorageRepository(db))
            owner.viewModelStore.put("test-editor-lifetime", editor)
            val recognition = ItemRecognitionViewModel(repository, coordinator, RecognitionSettings(context))
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return when (modelClass) {
                        ItemRecognitionViewModel::class.java -> recognition
                        SizeOptionsViewModel::class.java -> SizeOptionsViewModel(preferences, items)
                        else -> error("Unexpected screen ViewModel ${modelClass.name}")
                    } as T
                }
            }
            // Use the public provider to populate its normal default key, as Panel's viewModel() does.
            val provider = ViewModelProvider(owner, factory)
            provider[ItemRecognitionViewModel::class.java]
            provider[SizeOptionsViewModel::class.java]
            Session(owner, editor, recognition)
        }

        suspend fun seedSuggestions(editor: ItemEditViewModel) {
            withContext(Dispatchers.Main) { editor.keepDraft() }
            val id = repository.addRegion(editor.uiState.value.draftId!!, region, editor.currentRecognitionValues(), WardrobeFamily.WIG, false)
            val task = repository.get(id)!!
            val refs = task.request().regions.map { it.id }
            val measure = RecognitionMeasurement(RecognitionMeasurementTarget.HAIR_LENGTH, RecognitionMeasurementBasis.PRODUCT_MEASURED, RecognitionMeasurementUnit.CM)
            val result = RecognitionResult(candidates = listOf(
                RecognitionCandidate("color", RecognitionField.COLOR, "奶茶色", RecognitionSource.TEXT, refs, "颜色：奶茶色"),
                RecognitionCandidate("length-a", RecognitionField.WIG_LENGTH, "60 cm", RecognitionSource.TEXT, refs, "商品实测 A款 S 发长60 cm", "A款", "S", measure),
                RecognitionCandidate("length-b", RecognitionField.WIG_LENGTH, "70 cm", RecognitionSource.TEXT, refs, "商品实测 B款 M 发长70 cm", "B款", "M", measure)))
            db.recognitionDao().update(task.copy(status = RecognitionStatus.AWAITING_REVIEW, requestSent = true, resultJson = result.encode(), actualModel = "controlled-local-model"))
            taskId = id
        }
    }

    @Before fun setup() = runBlocking {
        fixture = Fixture(); fixture.init()
        active.value = fixture.session()
        compose.setContent {
            val current = active.value
            val density = target.resources.displayMetrics.widthPixels / 320f
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = (target.resources.displayMetrics.heightPixels / density).toInt()
                fontScale = 2f
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(density, 2f)) {
                LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (current != null && visible.value) key(current.owner) {
                            CompositionLocalProvider(LocalViewModelStoreOwner provides current.owner) {
                                ItemEditScreen(itemId = 1, onBack = {}, onSaveSuccess = { saved++; visible.value = false }, viewModel = current.editor)
                            }
                        } else if (current != null) {
                            Button(onClick = { scope.launch { active.value = fixture.session(); visible.value = true } }) { Text("重新编辑已保存物品") }
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { !active.value!!.editor.uiState.value.isLoading && active.value!!.editor.uiState.value.draftId != null }
        assertNull(active.value!!.editor.uiState.value.loadError)
        fixture.seedSuggestions(active.value!!.editor)
        compose.waitUntil(5_000) { active.value!!.recognition.uiState.value.tasks.any { it.id == fixture.taskId } }
    }

    @After fun close() {
        compose.runOnIdle { active.value = null; fixture.owners.forEach { it.viewModelStore.clear() } }
        compose.waitForIdle()
        runBlocking { fixture.scope.coroutineContext.job.cancelAndJoin() }
        fixture.db.close(); fixture.root.deleteRecursively()
        ImageFileHelper.configure(AppModule.context(), AppModule.database())
    }

    private fun openReview() {
        compose.onNodeWithText("选择图片并补充资料").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("逐项核对建议").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("逐项核对建议").assertExists()
        checks().assertCountEquals(4)
    }

    private fun chooseVariant(value: String) {
        compose.onNodeWithText("正在登记的款式：", substring = true).performScrollTo().performClick()
        compose.onNodeWithText(value, substring = false).performClick()
    }

    private fun chooseSize(value: String) {
        compose.onNodeWithText("实际购入尺码行：", substring = true).performScrollTo().performClick()
        compose.onNodeWithText(value, substring = false).performClick()
    }

    private fun checks() = compose.onAllNodes(isToggleable() and hasAnyAncestor(isDialog()))
    private fun imageNode(description: String) = compose.onNode(hasContentDescription(description) and
        !hasAnyAncestor(hasContentDescription(description)))
    private fun applySelected() = compose.onNodeWithText("应用所选资料到草稿").performScrollTo().performClick()

    @Test fun realButtonsFilterRowsApplyInPartsSaveAndReopenTheAcceptedEvidence() = runBlocking {
        openReview()
        checks()[1].assertIsOn().performScrollTo().performClick()
        chooseVariant("A款"); chooseSize("S")
        checks()[2].assertIsEnabled().performScrollTo().performClick().assertIsOn()
        chooseVariant("B款")
        checks()[2].assertIsOff().assertIsNotEnabled()
        chooseSize("M")
        checks()[3].assertIsEnabled()
        chooseVariant("A款"); chooseSize("S")
        checks()[2].performScrollTo().performClick()
        applySelected()
        compose.waitUntil(5_000) { active.value!!.editor.uiState.value.specifications.wig.lengthCm == "60 cm" && !active.value!!.recognition.uiState.value.busy }
        assertEquals(1, fixture.db.recognitionDao().getEvidence().size)
        assertEquals("60 cm", fixture.drafts.get(active.value!!.editor.uiState.value.draftId!!)!!.data().let { WardrobeSpecifications.decode(it.specificationsJson).wig.lengthCm })
        assertTrue(active.value!!.editor.uiState.value.colors.isEmpty())
        compose.onNodeWithText("已采纳，可继续核对").assertExists()
        checks()[1].assertIsOff().assertIsEnabled().performScrollTo().performClick()
        applySelected()
        compose.waitUntil(5_000) { active.value!!.editor.uiState.value.colors == listOf("奶茶色") && !active.value!!.recognition.uiState.value.busy }
        assertEquals(2, fixture.db.recognitionDao().getEvidence().size)
        compose.onNodeWithText("回到编辑").performClick()
        compose.onNodeWithContentDescription("保存物品").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { saved == 1 }
        val item = fixture.db.itemDao().getItemById(1)!!
        assertEquals("手工原名", item.name); assertEquals("手工备注不得被识图替换", item.description)
        assertEquals("60 cm", WardrobeSpecifications.decode(item.specificationsJson).wig.lengthCm)
        assertEquals(listOf("奶茶色"), item.colors)
        assertEquals(listOf(fixture.album), item.imageUrls)
        assertTrue(fixture.db.priceDao().getAllPricesList().isEmpty())
        val evidence = fixture.db.recognitionDao().getEvidence()
        assertEquals(2, evidence.size); assertTrue(evidence.all { it.itemId == 1L && it.regionImagePaths == listOf(fixture.region) })
        compose.onNodeWithText("重新编辑已保存物品").performClick()
        compose.waitUntil(10_000) { active.value!!.editor.uiState.value.draftId != null && !active.value!!.editor.uiState.value.isLoading }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("查看已采纳的识图来源（2）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看已采纳的识图来源（2）").performScrollTo().performClick()
        compose.onNodeWithText("发长：60 cm").performClick()
        compose.onNodeWithText("商品实测 A款 S 发长60 cm").assertExists()
        imageNode("证据裁切区域").performScrollTo().assertIsDisplayed()
        assertEquals(evidence.map { it.id }.toSet(), fixture.db.recognitionDao().getEvidence().map { it.id }.toSet())
        assertEquals(0, fixture.transportCalls)
    }

    @Test fun sevenLightSkinsKeepActualRegionsAndApplyControlsVisibleAt320dpAndDoubleText() = skinMatrix(false)
    @Test fun sevenDarkSkinsKeepActualRegionsAndApplyControlsVisibleAt320dpAndDoubleText() = skinMatrix(true)

    private fun skinMatrix(night: Boolean) {
        assertEquals(night, dark.value)
        openReview()
        chooseVariant("A款"); chooseSize("S")
        checks()[2].performScrollTo().performClick()
        val output = File(target.filesDir, "recognition-flow-screenshots/${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = mutableListOf<Map<String, Any>>()
        SkinType.entries.forEach { candidate ->
            compose.runOnIdle { skin.value = candidate }
            compose.onNodeWithText("逐项核对建议").performScrollTo().assertIsDisplayed()
            centerRegionInDialog()
            imageNode("实际识别区域 1").assertIsDisplayed()
            waitForActualRegionPixels(output, candidate, night)
            capture(output, candidate, night, "actual-region", manifest)
            val apply = compose.onNodeWithText("应用所选资料到草稿").performScrollTo().assertIsDisplayed().assertIsEnabled()
            capture(output, candidate, night, "review-apply", manifest)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("应用所选资料到草稿", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("The real apply text must expose its measured layout", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals("The Dialog itself must use actual double text", 2f, layout.layoutInput.density.fontScale, 0.01f)
                assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
                (0 until layout.lineCount).forEach { line ->
                    assertFalse("Apply text must never ellipsize", layout.isLineEllipsized(line))
                    assertTrue("Measured glyph lines must fit the actual text box", layout.getLineLeft(line) >= -1f &&
                        layout.getLineRight(line) <= layout.size.width + 1f && layout.getLineTop(line) >= -1f &&
                        layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
            checks()[1].assertIsOn(); checks()[2].assertIsOn(); checks()[3].assertIsOff().assertIsNotEnabled()
            assertEquals(0, fixture.transportCalls)
        }
        assertEquals(14, manifest.size)
        File(output, "manifest.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(manifest))
    }

    private fun centerRegionInDialog() {
        val vertical = compose.onNode(hasAnyAncestor(isDialog()) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        repeat(2) {
            val image = imageNode("实际识别区域 1").fetchSemanticsNode().boundsInWindow
            val viewport = vertical.fetchSemanticsNode().boundsInWindow
            vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, image.center.y - viewport.center.y) }
            compose.waitForIdle()
        }
    }

    /** Dialogs have their own Android surface; inspect the actual displayed window pixels. */
    private fun waitForActualRegionPixels(output: File, candidate: SkinType, night: Boolean) {
        var latest: Bitmap? = null
        try {
            compose.waitUntil(5_000) {
                latest?.recycle()
                latest = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                val bitmap = requireNotNull(latest)
                var sourceGreen = 0
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.green(pixel) > 245 && Color.red(pixel) < 10 && Color.blue(pixel) < 10) sourceGreen++
                }
                sourceGreen > 200
            }
        } catch (failure: Throwable) {
            File(output, "${candidate.name.lowercase()}-${if (night) "dark" else "light"}-FAILED-native-probe.png")
                .outputStream().use { stream -> latest?.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            File(output, "FAILED-semantics.txt").writeText(compose.onAllNodes(isDialog())[0].printToString())
            throw failure
        } finally { latest?.recycle() }
    }

    private fun capture(output: File, candidate: SkinType, night: Boolean, page: String, manifest: MutableList<Map<String, Any>>) {
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(output, "${candidate.name.lowercase()}-${if (night) "dark" else "light"}-$page-320dp-2x.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        manifest += mapOf("skin" to candidate.name, "dark" to night, "page" to page, "viewportWidthDp" to 320,
            "fontScale" to 2, "widthPx" to bitmap.width, "heightPx" to bitmap.height, "file" to file.name, "sha256" to imageSha256(file.path))
        bitmap.recycle()
    }
}
