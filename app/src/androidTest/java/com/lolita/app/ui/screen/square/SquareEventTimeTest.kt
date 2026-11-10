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

/** Dedicated AVD only. Current public package is pushed to /sdcard/Download/square-time-sale.zip. */
class SquareEventTimeTest {
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
        val input = File(target.cacheDir, "time-public-fixture.zip")
        if (!input.isFile) {
            val fd = inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-time-sale.zip")
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
        val dir = File(target.filesDir, "square-time-native").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    private fun open(id: String) { compose.runOnIdle { model!!.open(id) }; compose.waitForIdle() }
    private fun scroll(tag: String) = compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag(tag))

    @Test fun currentStockRereleaseAndHistoricalTimeAgreeWithTabs() {
        val c = imported(); show()
        val spot = SquareBrowseFilter(kind="现货").events(c).map { it.id }.toSet()
        val repeat = SquareBrowseFilter(kind="再贩").events(c).map { it.id }.toSet()
        val corrected = setOf("jixiangtian-2025-repeat-drop", "qifeng-2024-repeat", "victoria-garden-2025-rerelease", "wangyue-baihe-resale", "xuwangzhimian-2024-repeat")
        assertTrue(spot.containsAll(corrected)); assertTrue(repeat.intersect(corrected).isEmpty())
        assertTrue("biaobenzu-2-round3" in repeat)
        open("qifeng-2024-repeat")
        compose.onNodeWithText("现货 · 再贩 · 历史预约 2024年5月30日 上午10时—2024年6月27日下午17时").assertExists()
        compose.onNodeWithContentDescription("时间与售卖依据").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("本轮现货/掉落开启日期未提供", substring=true).fetchSemanticsNodes().isNotEmpty() }
        screenshot("history-evidence")
    }

    @Test fun datedLaunchCaptureAndTailStayExplicitAndReadOnly() {
        imported(); show(); open("dorothy-cotton")
        compose.onNodeWithText("上新 · 开售 10月30日 20:00").assertExists()
        compose.onNodeWithContentDescription("时间与售卖依据").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("原文未注明年份",substring=true).fetchSemanticsNodes().isNotEmpty() }; screenshot("launch-evidence")
        compose.runOnIdle { model!!.back() }; open("jixiangtian-2025-repeat-drop")
        compose.onNodeWithText("现货 · 再贩 · 采集 2026-11-09").assertExists()
        compose.runOnIdle { model!!.back() }; open("sleeve-balance")
        compose.onNodeWithText("尾款 10月15日上午10时—11月12日下午17时").assertExists()
        compose.onNodeWithTag("square-add-wish").assertDoesNotExist()
        compose.onNodeWithContentDescription("时间与售卖依据").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("图示未注明年份",substring=true).fetchSemanticsNodes().isNotEmpty() }; screenshot("tail-evidence")
    }

    @Test fun sevenSkinsLightDarkPresentTypedTimeAndStockIdentity() {
        imported(); show()
        for (type in SkinType.entries) for (night in listOf(false,true)) {
            compose.runOnIdle { skin.value=type;dark.value=night;model!!.back();model!!.set("kind","现货") }
            val suffix="${type.name.lowercase()}-${if(night) "dark" else "light"}"
            compose.onNodeWithTag("square-event-jixiangtian-2025-repeat-drop").assertExists()
            screenshot("stock-$suffix")
            open("dorothy-cotton")
            compose.onNodeWithText("上新 · 开售 10月30日 20:00").assertExists(); screenshot("launch-$suffix")
            compose.onNodeWithContentDescription("时间与售卖依据").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("原文未注明年份",substring=true).fetchSemanticsNodes().isNotEmpty() }; screenshot("evidence-$suffix")
                compose.runOnIdle { model!!.back() }; open("sleeve-balance")
            compose.onNodeWithTag("square-add-wish").assertDoesNotExist(); screenshot("tail-$suffix")
            compose.runOnIdle { model!!.back() }
        }
    }
}
