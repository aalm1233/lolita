package com.lolita.app.ui.screen.square

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.data.square.*
import com.lolita.app.domain.model.ItemComposition
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.usecase.AddSquareWishUseCase
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

/** Dedicated AVD only. Current public package is pushed to /sdcard/Download/square-polish.zip. */
class SquarePolishTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext
    private val testRoot = File(target.cacheDir, "browse-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(target) {
        override fun getFilesDir() = File(testRoot, "files").apply { mkdirs() }
        override fun getCacheDir() = File(testRoot, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(testRoot, "no-backup").apply { mkdirs() }
    }
    private lateinit var db: LolitaDatabase
    private lateinit var repo: SquareRepository
    private lateinit var drafts: ItemDraftRepository
    private var model: SquareViewModel? = null
    private val store = ViewModelStore()
    private val skin = mutableStateOf(SkinType.CLASSIC)
    private val dark = mutableStateOf(false)
    private val money = MutableStateFlow(true)
    private var openedId = 0L
    private var wishOpened = false
    private val outgoing = mutableListOf<Intent>()
    private var linkMode = "app"
    private lateinit var host: android.view.View
    private fun <T> io(block: suspend () -> T) = runBlocking(Dispatchers.IO) { block() }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        drafts = ItemDraftRepository(db, ItemRepository(db.itemDao(), database = db, context = context))
        repo = SquareRepository(context, db, drafts, defaultServiceUrl="")
        ImageFileHelper.configure(context, db)
        io { repo.load() }
    }
    @After fun cleanup() {
        inst.runOnMainSync { store.clear() }
        io { db.close() }
        testRoot.deleteRecursively()
        ImageFileHelper.configure(target, com.lolita.app.di.AppModule.database())
    }
    private fun imported(): SquareCatalog = io {
        val input = File(target.cacheDir, "polish-public-fixture.zip")
        if (!input.isFile) {
            val fd = inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-polish.zip")
            FileInputStream(fd.fileDescriptor).use { source -> input.outputStream().use { source.copyTo(it) } }; fd.close()
        }
        val preview = repo.preview(Uri.fromFile(input)) {}; repo.apply(preview)
        repo.catalog.value!!
    }
    private fun show() {
        inst.runOnMainSync { model = SquareViewModel(repo, SavedStateHandle(), money); store.put("square", model!!) }
        compose.setContent {
            val view = LocalView.current
            SideEffect { host = view; (view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            val cfg = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            val base = LocalContext.current
            val linkContext = remember(base) { object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) {
                    if (intent.action != Intent.ACTION_VIEW) { super.startActivity(intent); return }
                    outgoing.add(Intent(intent))
                    when (linkMode) {
                        "browser" -> if (intent.`package` != null) throw ActivityNotFoundException()
                        "blocked" -> throw SecurityException("controlled blocked handler")
                    }
                }
            } }
            CompositionLocalProvider(LocalConfiguration provides cfg, LocalContext provides linkContext) {
                LolitaTheme(skin.value, dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { SkinArtBackground(); SquareScreen({}, { openedId = it }, model!!, { wishOpened = true }) }
                    }
                }
            }
        }
        compose.waitUntil(15000) { model!!.busy.value == null }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); inst.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val dir = File(target.filesDir, "square-polish-native").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    private fun open(id: String) { compose.runOnIdle { model!!.open(id) }; compose.waitForIdle() }
    private fun scroll(tag: String) = compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag(tag))

    @Test fun collapsedSearchCancelBackAndDetailReturnKeepFilterContext() {
        val c = imported(); show()
        val event = SquareVisibility.events(c).first { it.canRegister }
        val keyword = event.title.take(3)
        val currentKind = SquareEventPresentation.kind(event)
        compose.runOnIdle { model!!.set("shop", event.shopId); model!!.set("kind", currentKind) }
        compose.onNodeWithTag("square-search").assertDoesNotExist()
        compose.onNodeWithTag("square-search-open").performClick()
        compose.onNodeWithTag("square-search").assertIsFocused()
        compose.onNodeWithText("搜索商品或店铺").assertExists()
        compose.onNodeWithTag("square-search").performTextInput(keyword)
        compose.onNodeWithTag("square-event-${event.id}").assertExists()
        open(event.id)
        compose.onNodeWithTag("square-product").assertExists()
        compose.runOnIdle { model!!.back() }
        compose.onNodeWithTag("square-search").assertTextContains(keyword)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("square-search").assertDoesNotExist()
        compose.onNodeWithTag("square-search-open").assertIsDisplayed()
        assertEquals("", model!!.query.value)
        assertEquals(event.shopId, model!!.shop.value); assertEquals(currentKind, model!!.kind.value)
        compose.onNodeWithTag("square-search-open").performClick()
        compose.onNodeWithTag("square-search").performTextInput(keyword)
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        screenshot("search-real-ime")
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == false }
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("square-search").fetchSemanticsNodes().isEmpty() }
        assertEquals("", model!!.query.value)
        compose.onNodeWithTag("square-search-open").assertIsDisplayed()
        assertEquals(event.shopId, model!!.shop.value); assertEquals(currentKind, model!!.kind.value)
    }

    @Test fun collapsedAndExpandedSearchAcrossSevenSkinsAndModes() {
        imported(); show()
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            val suffix = "${type.name.lowercase()}-${if (night) "dark" else "light"}"
            compose.onNodeWithTag("square-search").assertDoesNotExist()
            compose.onNodeWithTag("square-search-open").assertIsDisplayed()
            screenshot("search-collapsed-$suffix")
            compose.onNodeWithTag("square-search-open").performClick()
            compose.onNodeWithTag("square-search").assertIsFocused()
            compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            compose.onNodeWithText("取消").assertIsDisplayed()
            screenshot("search-expanded-$suffix")
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == false }
        }
    }

    @Test fun compactHierarchyShowsOwnTitleAndSwitchesRealOzzColour() {
        imported(); show(); screenshot("feed-normal")
        open("angelo-villainess-elegance-deposit")
        compose.onNodeWithTag("square-product-title").assertTextEquals("蕾丝姬袖连衣裙")
        scroll("square-color:紫色"); compose.onNodeWithTag("square-color:紫色").performClick()
        assertEquals("紫色", model!!.currentBrowseChoice()!!.color)
        assertTrue(model!!.currentBrowseSelection()!!.photos.isNotEmpty())
        screenshot("ozz-choices")
        scroll("square-parameters")
        compose.onNodeWithText("衣长(cm)").assertExists()
        compose.onNodeWithText("完整参数与售卖说明").assertExists()
        compose.onNodeWithText("完整参数与售卖说明").performClick()
        screenshot("ozz-parameters-expanded")
        compose.runOnIdle { money.value = false }
        compose.onAllNodes(hasText("¥", substring = true)).assertCountEquals(0)
    }

    @Test fun seriesReferenceCanBeViewedButDirectWishHasNoBorrowedCover() {
        imported(); show(); open("jixiangtian-2025-repeat-drop")
        compose.onNodeWithTag("square-series-reference").assertIsDisplayed()
        compose.onNodeWithText("暂无单品图").assertDoesNotExist(); screenshot("neo-reference")
        val before = model!!.currentBrowseSelection()!!
        assertTrue(before.photos.isEmpty()); assertTrue(before.seriesPhotos.isNotEmpty())
        compose.onNodeWithTag("square-add-wish").performClick()
        compose.waitUntil(15000) { io { db.itemDao().getItemCount() } == 1 && model!!.busy.value == null }
        assertTrue(io { db.itemDao().getAllItemsList().single() }.imageUrls.isEmpty())
        compose.onNodeWithText("已在心愿 · 查看").assertExists()
    }

    @Test fun bunnySearchSeparatesPartAndColourAndKeepsSourceIdentity() {
        val c = imported(); show(); open("bow-bunny-acc-spot")
        compose.onNodeWithTag("square-all-parts").performClick()
        compose.onNodeWithTag("square-part-search").performTextInput("经典颈饰")
        screenshot("bunny-search")
        compose.onNodeWithTag("square-part-option-${SquarePresentation.groups(c, c.events.single { it.id == "bow-bunny-acc-spot" }).single { it.label == "经典颈饰" }.key}").performClick()
        assertEquals("acc-01", model!!.currentBrowsePart()!!.partKey)
        scroll("square-color:白蓝"); compose.onNodeWithTag("square-color:白蓝").performClick()
        assertEquals("acc-02", model!!.currentBrowsePart()!!.partKey)
        assertEquals("白蓝", model!!.currentBrowseChoice()!!.color)
        screenshot("bunny-colour")
        compose.onNodeWithTag("square-add-wish").performClick()
        compose.waitUntil(15000) { io { db.itemDao().getItemCount() } == 1 && model!!.busy.value == null }
        compose.onNodeWithText("已在心愿 · 查看").assertExists()
        compose.onNodeWithTag("square-color:棕色").performClick()
        assertEquals("acc-04", model!!.currentBrowsePart()!!.partKey)
        assertTrue(model!!.currentWishMatch().exact.isEmpty())
    }

    @Test fun accessoryFilterCardAndDetailAgreeAndClearKeepsShopAndSearch() {
        val c = imported(); show(); val e = c.events.single { it.id == "jixiangtian-2025-repeat-drop" }
        compose.runOnIdle { model!!.set("shop", e.shopId); model!!.set("form", "小物"); model!!.set("query", "吉祥天") }
        compose.onNodeWithTag("square-event-${e.id}").performScrollTo().performClick()
        assertTrue(model!!.currentBrowsePart()!!.accessory)
        assertFalse(model!!.currentBrowsePart()!!.name.contains("OP")); screenshot("filtered-accessory")
        compose.runOnIdle { model!!.back() }
        compose.onNodeWithTag("square-filter-button").performClick()
        compose.onNodeWithTag("square-filter-form:小物").assertIsDisplayed()
        compose.onNodeWithTag("square-filter-done").assertIsDisplayed(); screenshot("filters-full-height")
        compose.onNodeWithText("清除属性").performClick()
        assertEquals(e.shopId, model!!.shop.value); assertEquals("吉祥天", model!!.query.value)
        assertEquals("", model!!.form.value)
        compose.onNodeWithTag("square-filter-done").performClick()
        compose.runOnIdle { model!!.set("color", "不存在的颜色") }
        compose.onNodeWithText("清除属性筛选").assertIsDisplayed(); screenshot("empty-filter-actions")
        compose.onNodeWithText("清除属性筛选").performClick()
        assertEquals(e.shopId, model!!.shop.value); assertEquals("吉祥天", model!!.query.value)
    }

    @Test fun multiSearchKeepsHiddenChoicesAndSavesIndependentMembers() {
        val c = imported(); show(); open("bow-bunny-acc-spot")
        compose.onNodeWithText("选多件").performClick()
        val second = model!!.browseParts().first { it.partKey == "acc-02" }
        compose.runOnIdle { model!!.toggleWish(second.key) }
        compose.onNodeWithTag("square-wish-options").performScrollToNode(hasTestTag("square-wish-search"))
        compose.onNodeWithTag("square-wish-search").performTextInput("贝雷帽")
        assertEquals(2, model!!.selectedWishes().size)
        compose.onNodeWithTag("square-save-wishes").assertIsDisplayed().assertTextContains("2 件", substring = true)
        screenshot("multi-hidden-selection")
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.onNodeWithTag("square-save-wishes").performClick()
        compose.waitUntil(20000) { io { db.itemDao().getItemCount() } == 2 && model!!.busy.value == null }
        assertTrue(io { db.priceDao().getAllPricesList().isEmpty() })
    }

    @Test fun actualSevenSkinsLightDarkHaveReachableFeedDetailReferenceAndFilters() {
        imported(); show()
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night; model!!.back() }
            val suffix = "${type.name.lowercase()}-${if (night) "dark" else "light"}"
            screenshot("feed-$suffix")
            open("angelo-villainess-elegance-deposit")
            compose.onNodeWithTag("square-add-wish").assertIsDisplayed(); screenshot("detail-$suffix")
            compose.runOnIdle { model!!.back() }; open("jixiangtian-2025-repeat-drop")
            compose.onNodeWithTag("square-series-reference").assertIsDisplayed(); screenshot("reference-$suffix")
            compose.runOnIdle { model!!.back() }
            compose.onNodeWithTag("square-filter-button").performClick()
            compose.onNodeWithTag("square-filter-done").assertIsDisplayed(); screenshot("filters-$suffix")
            compose.onNodeWithTag("square-filter-done").performClick()
            open("bow-bunny-acc-spot")
            compose.onNodeWithTag("square-all-parts").performClick(); screenshot("parts-$suffix")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("选多件").performClick()
            compose.onNodeWithTag("square-save-wishes").assertIsDisplayed(); screenshot("multi-$suffix")
            compose.runOnIdle { model!!.closeMulti(); model!!.back() }
        }
    }

    @Test fun realFilterAndPartSearchKeyboardKeepActionsReachable() {
        imported(); show()
        compose.onNodeWithTag("square-filter-button").performClick()
        compose.onNodeWithTag("square-filter-color-search").performScrollTo().performTextInput("粉")
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithTag("square-filter-done").assertIsDisplayed(); screenshot("filter-real-ime")
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.onNodeWithTag("square-filter-done").performClick()
        open("bow-bunny-acc-spot"); compose.onNodeWithTag("square-all-parts").performClick()
        compose.onNodeWithTag("square-part-search").performTextInput("颈饰")
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithText("完成").assertIsDisplayed(); screenshot("part-real-ime")
    }
}
