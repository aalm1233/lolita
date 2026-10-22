package com.lolita.app.ui.screen.item

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.SizeOptionsViewModel
import com.lolita.app.ui.screen.item.recognition.ItemRecognitionViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File
import java.util.UUID

/** Real editor/Room regressions: folding never mutates data; IME and back keep their promises. */
@OptIn(ExperimentalLayoutApi::class)
class ItemEntryRefinementTest {
    private val compose = createComposeRule()
    private val nativeViewport = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            if (!description.methodName.contains("Skins")) { base.evaluate(); return }
            val resources = target.resources
            val oldDensity = resources.displayMetrics.densityDpi
            val oldFont = resources.configuration.fontScale
            val oldNight = requireNotNull(Regex("Night mode: (auto|no|yes|custom)").find(shell("cmd uimode night"))).groupValues[1]
            val oldDensityOverride = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
            val oldScale = shell("settings get system font_scale")
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
                if (oldScale == "null") shell("settings delete system font_scale") else {
                    require(oldScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
                    shell("settings put system font_scale $oldScale")
                }
                shell(oldDensityOverride?.let { "wm density $it" } ?: "wm density reset")
                shell("cmd uimode night $oldNight")
                // Auto/custom may resolve differently while the test runs; restore the saved policy.
                val restoredNight = when (oldNight) {
                    "yes" -> Configuration.UI_MODE_NIGHT_YES
                    "no" -> Configuration.UI_MODE_NIGHT_NO
                    else -> null
                }
                awaitConfiguration(oldDensity, oldFont, restoredNight)
                assertEquals(oldNight, Regex("Night mode: (auto|no|yes|custom)")
                    .find(shell("cmd uimode night"))?.groupValues?.get(1))
            }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(nativeViewport).around(compose)
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: Fixture
    private val visible = mutableStateOf(true)
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private var saved = 0
    private var returned = 0
    private var keyboard: SoftwareKeyboardController? = null
    private var hostView: View? = null
    @Volatile private var imeVisible = false
    @Volatile private var imeTop = 0f

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    private fun awaitConfiguration(dpi: Int, scale: Float, night: Int?) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val resources = target.resources
            if (resources.displayMetrics.densityDpi == dpi && kotlin.math.abs(resources.configuration.fontScale - scale) < 0.01f &&
                (night == null || (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == night)) return
            Thread.sleep(25)
        }
        error("Native entry viewport did not become $dpi dpi / $scale / $night")
    }

    private class Owner : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private inner class Fixture {
        val id = "entry-refinement-${UUID.randomUUID()}"
        val root = File(target.cacheDir, id).apply { mkdirs() }
        val preferenceNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "keys").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val unique = "$id-$name"
                preferenceNames.add(unique)
                return target.getSharedPreferences(unique, mode)
            }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(root, "settings.preferences_pb") }))
        val items = ItemRepository(db.itemDao(), database = db)
        val drafts = ItemDraftRepository(db, items)
        val owner = Owner()
        lateinit var editor: ItemEditViewModel
        val specifications = WardrobeSpecifications(pattern = "原始图案",
            wig = WigSpecifications(mainColor = "原发色", lengthCm = "60 cm", material = "原材质"),
            footwear = FootwearSpecifications(sizeText = "37 EU", innerLengthCm = "23.5 cm"))
        val original = Item(id = 1, name = "原物品", description = "原备注保持完整", brandId = 1, categoryId = 1,
            status = ItemStatus.OWNED, size = "M", colors = listOf("奶茶色"), season = "春,秋", style = "古典",
            source = "旧来源", specificationsJson = specifications.encode(), locationId = 1,
            parentItemId = 2, includedInParentPrice = true)

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            db.brandDao().insertBrand(Brand(id = 1, name = "原品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "测试假发", categorySemantic = CategorySemantic.FULL_WIG))
            db.categoryDao().insertCategory(Category(id = 2, name = "测试鞋履", categorySemantic = CategorySemantic.MARY_JANE))
            db.categoryDao().insertCategory(Category(id = 3, name = "JSK", categorySemantic = CategorySemantic.JSK))
            db.locationDao().insertLocation(Location(id = 1, name = "原房间", description = ""))
            db.itemDao().insertItem(Item(id = 2, name = "原主件", description = "", brandId = 1, categoryId = 3, status = ItemStatus.OWNED))
            db.itemDao().insertItem(original)
            withContext(Dispatchers.Main) {
                editor = ItemEditViewModel(items, BrandRepository(db.brandDao(), db.itemDao(), db),
                    CategoryRepository(db.categoryDao(), db.itemDao(), db), CoordinateRepository(db.coordinateDao(), db.itemDao(), db),
                    PriceRepository(db.priceDao(), db.paymentDao(), context, db), StyleRepository(db.styleDao(), db.itemDao(), db),
                    SeasonRepository(db.seasonDao(), db.itemDao(), db), SourceRepository(db.sourceDao(), db.itemDao(), db),
                    LocationRepository(db.locationDao(), db.itemDao()), drafts, StorageRepository(db))
                owner.viewModelStore.put("entry-editor", editor)
                val coordinator = RecognitionCoordinator(db, context, scope,
                    readKey = { error("No provider key may be read by an editor layout regression") }, keyRevision = { 0 },
                    recognize = { _, _, _ -> error("No network is allowed in this editor regression") }, providerReady = { true })
                val recognition = ItemRecognitionViewModel(RecognitionRepository(db, context), coordinator, RecognitionSettings(context))
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                        ItemRecognitionViewModel::class.java -> recognition
                        SizeOptionsViewModel::class.java -> SizeOptionsViewModel(preferences, items)
                        else -> error("Unexpected ViewModel ${modelClass.name}")
                    } as T
                }
                ViewModelProvider(owner, factory)[ItemRecognitionViewModel::class.java]
                ViewModelProvider(owner, factory)[SizeOptionsViewModel::class.java]
            }
        }
    }

    @Before fun setup() = runBlocking {
        fixture = Fixture(); fixture.init()
        compose.setContent {
            val view = LocalView.current
            val controller = LocalSoftwareKeyboardController.current
            val open = WindowInsets.isImeVisible
            SideEffect {
                keyboard = controller; hostView = view; imeVisible = open
                imeTop = (view.height - (ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)).toFloat()
            }
            LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                  Box(Modifier.fillMaxSize()) {
                    SkinArtBackground()
                    if (visible.value) CompositionLocalProvider(LocalViewModelStoreOwner provides fixture.owner) {
                        ItemEditScreen(itemId = 1, viewModel = fixture.editor,
                            onBack = { returned++; visible.value = false }, onSaveSuccess = { saved++; visible.value = false })
                    }
                  }
                }
            }
        }
        compose.waitUntil(10_000) { !fixture.editor.uiState.value.isLoading && fixture.editor.uiState.value.draftId != null &&
            fixture.editor.uiState.value.item?.id == 1L && fixture.editor.uiState.value.purchaseReviewsLoaded }
        assertNull(fixture.editor.uiState.value.loadError)
    }

    @After fun close() {
        compose.runOnIdle { keyboard?.hide(); visible.value = false; fixture.owner.viewModelStore.clear() }
        compose.waitForIdle()
        runBlocking { fixture.scope.coroutineContext.job.cancelAndJoin() }
        fixture.db.close(); fixture.root.deleteRecursively()
        fixture.preferenceNames.toList().forEach(target::deleteSharedPreferences)
        ImageFileHelper.configure(AppModule.context(), AppModule.database())
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun expandSpecifications() = compose.onNodeWithContentDescription("展开详细规格").performScrollTo().performClick()
    private fun chooseType(current: String, family: String, row: String) {
        compose.onNodeWithText(current).performScrollTo().performClick()
        compose.onNodeWithText(family, substring = false).performScrollTo().performClick()
        compose.onNodeWithText(row).performClick()
    }

    @Test fun foldingAndChangingTypeKeepAllProfilesAndFinancialRelationshipsOnSave() = runBlocking {
        field("备注").assertDoesNotExist()
        field("主要发色").assertDoesNotExist()
        compose.onNodeWithText("所属主件：原主件").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("随主件计价，不重复计价，拥有状态随主件").assertExists()
        compose.onNodeWithContentDescription("展开补充资料").performScrollTo().performClick()
        field("备注").performScrollTo().assertTextContains("原备注保持完整")
        compose.onNodeWithContentDescription("收起补充资料").performScrollTo().performClick()
        expandSpecifications()
        field("长度").performScrollTo().performTextReplacement("68 cm")
        chooseType("假发 · 测试假发", "鞋履", "测试鞋履 · 鞋履")
        expandSpecifications()
        field("鞋码").performScrollTo().assertTextContains("37 EU")
        chooseType("鞋履 · 测试鞋履", "假发", "测试假发 · 假发")
        expandSpecifications()
        field("长度").performScrollTo().assertTextContains("68 cm")
        compose.onNodeWithContentDescription("收起详细规格").performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("保存物品").assertCountEquals(1)
        compose.onNodeWithContentDescription("保存物品").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { saved == 1 }
        val stored = fixture.db.itemDao().getItemById(1)!!
        assertEquals(fixture.original.description, stored.description)
        assertEquals(fixture.original.brandId, stored.brandId)
        assertEquals(fixture.original.season, stored.season)
        assertEquals(fixture.original.style, stored.style)
        assertEquals(fixture.original.source, stored.source)
        assertEquals(fixture.original.locationId, stored.locationId)
        assertEquals(2L, stored.parentItemId)
        assertTrue(stored.includedInParentPrice)
        val spec = WardrobeSpecifications.decode(stored.specificationsJson)
        assertEquals("68 cm", spec.wig.lengthCm)
        assertEquals(fixture.specifications.footwear, spec.footwear)
        assertEquals(fixture.specifications.pattern, spec.pattern)
        assertTrue(fixture.db.priceDao().getAllPricesList().isEmpty())
    }

    @Test fun realKeyboardLeavesTheOnlySaveButtonReachable() {
        val old = shell("settings get secure show_ime_with_hard_keyboard")
        require(old == "null" || old.matches(Regex("-?\\d+")))
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            field("物品名称 *").performScrollTo().performClick().performTextReplacement("键盘录入的物品")
            compose.runOnIdle { keyboard?.show() }
            compose.waitUntil(10_000) { imeVisible }
            val save = compose.onNodeWithContentDescription("保存物品").assertIsDisplayed().assertIsEnabled()
            assertTrue("The save control must be above the real IME", save.fetchSemanticsNode().boundsInWindow.bottom <= imeTop + 1f)
            save.performClick()
            compose.waitUntil(10_000) { saved == 1 }
            runBlocking { assertEquals("键盘录入的物品", fixture.db.itemDao().getItemById(1)!!.name) }
        } finally {
            compose.runOnIdle { keyboard?.hide() }
            shell(if (old == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $old")
        }
    }

    @Test fun normalBackKeepsDraftWithoutSavingOrShowingADiscardPrompt() = runBlocking {
        field("物品名称 *").performScrollTo().performTextReplacement("留到下次的物品")
        val identity = fixture.editor.uiState.value.draftId!!
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5_000) { returned == 1 }
        assertEquals(0, saved)
        assertEquals("原物品", fixture.db.itemDao().getItemById(1)!!.name)
        assertEquals("留到下次的物品", fixture.drafts.get(identity)!!.data().name)
        compose.onNodeWithText("放弃这份草稿？").assertDoesNotExist()
    }

    @Test fun sevenLightSkinsShowFoldedEntryAndKeyboardSave() = entryMatrix(false)
    @Test fun sevenDarkSkinsShowFoldedEntryAndKeyboardSave() = entryMatrix(true)

    private fun entryMatrix(night: Boolean) {
        assertEquals(night, dark.value)
        val output = File(target.filesDir, "entry-refinement-screenshots/${UUID.randomUUID()}").apply { mkdirs() }
        val manifest = mutableListOf<Map<String, Any>>()
        val oldKeyboard = shell("settings get secure show_ime_with_hard_keyboard")
        require(oldKeyboard == "null" || oldKeyboard.matches(Regex("-?\\d+")))
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            SkinType.entries.forEach { candidate ->
                compose.runOnIdle { skin.value = candidate }
                compose.onNodeWithText("添加照片（可选）").performScrollTo().assertIsDisplayed()
                compose.onNode(hasText("识图填入", substring = true) and hasClickAction()).assertExists()
                field("备注").assertDoesNotExist()
                field("主要发色").assertDoesNotExist()
                field("鞋码").assertDoesNotExist()
                compose.onAllNodesWithContentDescription("保存物品").assertCountEquals(1)
                val save = compose.onNodeWithContentDescription("保存物品").assertIsDisplayed().assertIsEnabled()
                verifyTextLayout(compose.onNodeWithText("保存物品", useUnmergedTree = true), "Save must fit at real double text")
                captureEntry(output, candidate, night, "entry", false, manifest)

                compose.onNodeWithContentDescription("展开详细规格").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("展开补充资料").assertExists()
                compose.onNodeWithText("所属主件：原主件").assertExists()
                compose.onNodeWithText("随主件计价，不重复计价，拥有状态随主件").assertExists()
                field("主要发色").assertDoesNotExist()
                field("备注").assertDoesNotExist()
                captureEntry(output, candidate, night, "folded-details", false, manifest)

                field("物品名称 *").performScrollTo().performClick()
                compose.runOnIdle { keyboard?.show() }
                compose.waitUntil(10_000) { imeVisible }
                compose.waitForIdle()
                compose.runOnIdle {
                    val view = requireNotNull(hostView)
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(view))
                    val height = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    assertTrue("The real Android IME must occupy window space", insets.isVisible(WindowInsetsCompat.Type.ime()) && height > 0)
                    imeTop = (view.height - height).toFloat()
                }
                val keyboardSave = compose.onNodeWithContentDescription("保存物品").assertIsDisplayed().assertIsEnabled()
                assertTrue("Save must remain above the actual keyboard", keyboardSave.fetchSemanticsNode().boundsInWindow.bottom <= imeTop + 1f)
                field("物品名称 *").assertIsDisplayed()
                captureEntry(output, candidate, night, "keyboard", true, manifest)
                compose.runOnIdle { keyboard?.hide() }
                compose.waitUntil(5_000) { !imeVisible }
            }
            assertEquals(21, manifest.size)
            File(output, "manifest.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(manifest))
            assertEquals(0, saved)
            runBlocking {
                assertEquals(fixture.original, fixture.db.itemDao().getItemById(1))
                assertTrue(fixture.db.priceDao().getAllPricesList().isEmpty())
            }
        } finally {
            compose.runOnIdle { keyboard?.hide() }
            shell(if (oldKeyboard == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $oldKeyboard")
        }
    }

    private fun verifyTextLayout(node: SemanticsNodeInteraction, message: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$message: actual text layout is required", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertEquals("Screenshot matrix uses real font scale", 2f, layout.layoutInput.density.fontScale, 0.01f)
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
            (0 until layout.lineCount).forEach { line ->
                assertFalse(message, layout.isLineEllipsized(line))
                assertTrue(message, layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f &&
                    layout.getLineTop(line) >= -1f && layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
    }

    private fun captureEntry(output: File, candidate: SkinType, night: Boolean, state: String, keyboardOpen: Boolean,
        manifest: MutableList<Map<String, Any>>) {
        compose.waitForIdle()
        assertEquals(keyboardOpen, imeVisible)
        val resources = target.resources
        assertEquals(320f, resources.displayMetrics.widthPixels / resources.displayMetrics.density, 1f)
        assertEquals(2f, resources.configuration.fontScale, 0.01f)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(output, "${candidate.name.lowercase()}-${if (night) "dark" else "light"}-$state-320dp-2x.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        manifest += mapOf("skin" to candidate.name, "dark" to night, "state" to state, "actualPage" to "ItemEditScreen",
            "widthDp" to 320, "fontScale" to resources.configuration.fontScale, "densityDpi" to resources.displayMetrics.densityDpi,
            "imeVisible" to keyboardOpen, "widthPx" to bitmap.width, "heightPx" to bitmap.height,
            "file" to file.name, "sha256" to imageSha256(file.path))
        bitmap.recycle()
    }
}
