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

/** Dedicated AVD only. Current public package is pushed to /sdcard/Download/square-browse.zip. */
class SquareBrowseTest {
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
        repo = SquareRepository(context, db, drafts)
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
        val input = File(target.cacheDir, "browse-public-fixture.zip")
        if (!input.isFile) {
            val fd = inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-browse.zip")
            FileInputStream(fd.fileDescriptor).use { source -> input.outputStream().use { source.copyTo(it) } }; fd.close()
        }
        val preview = repo.preview(Uri.fromFile(input)) {}; repo.apply(preview)
        repo.catalog.value!!
    }
    private fun part(c: SquareCatalog, event: String, key: String) = SquareBrowse.parts(c, c.events.single { it.id == event }).single { it.partKey == key }
    private fun request(c: SquareCatalog, choices: List<SquareBrowseChoice>, grouped: Boolean = false, event: String = "dorothy-cotton") =
        SquareBrowse.request(c, c.events.single { it.id == event }, choices, grouped)
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
        val dir = File(target.filesDir, "square-browse-native").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun taobaoButton(): SemanticsNodeInteraction {
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-open-taobao"))
        return compose.onNodeWithTag("square-open-taobao")
    }
    private fun hash(path: String) = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun directUnknownSizeAtomicCopiesAndConcurrentReceipt() {
        val c = imported(); val jsk = part(c, "dorothy-cotton", "JSK")
        val req = request(c, listOf(SquareBrowseChoice(jsk.key, "蓝色")))
        val results = io { coroutineScope { listOf(async { repo.addWishes(req) }, async { repo.addWishes(req) }).awaitAll() } }
        assertEquals(results[0].itemIds, results[1].itemIds); assertEquals(1, results.sumOf { it.added })
        val item = io { db.itemDao().getAllItemsList().single() }
        assertEquals(ItemStatus.WISHED, item.status); assertNull(item.size); assertEquals(listOf("蓝色"), item.colors)
        assertNull(item.coordinateId); assertNull(item.parentItemId); assertNull(item.locationId)
        assertTrue(io { db.brandDao().getBrandById(item.brandId)!! }.isUnsetBrand)
        assertEquals(CategorySemantic.JSK, io { db.categoryDao().getCategoryById(item.categoryId)!! }.categorySemantic)
        val source = io { db.orderImportDao().sources().single() }; val snapshot = SquareWishSnapshot.decode(source.snapshotJson, source.imagePaths.size)
        assertNull(snapshot.members.single().origins.first().selectedSkuKey)
        assertTrue(item.imageUrls.isNotEmpty()); assertTrue((item.imageUrls + source.imagePaths).all { it.startsWith(File(context.filesDir, "images").path) && File(it).isFile })
        assertEquals(c.assets.single { it.path == SquareBrowse.select(c, jsk, req.choices.single()).photos.first() }.sha256, hash(item.imageUrls.first()))
        assertEquals(0, io { db.priceDao().getAllPricesList().size }); assertTrue(io { db.itemDraftDao().getAll().isEmpty() })
        io { db.itemDao().updateItem(item.copy(status = ItemStatus.OWNED, description = "保留用户备注")); repo.addWishes(req) }
        assertEquals(ItemStatus.OWNED, io { db.itemDao().getItemById(item.id)!! }.status)
        assertEquals("保留用户备注", io { db.itemDao().getItemById(item.id)!! }.description)
    }

    @Test fun multipleWritesRollbackAndOrphanJournalProtectsCommittedImages() {
        val c = imported(); val choices = listOf(SquareBrowseChoice(part(c, "dorothy-cotton", "JSK").key, "蓝色", "L"), SquareBrowseChoice(part(c, "dorothy-cotton", "KC").key))
        val req = request(c, choices)
        val failing = AddSquareWishUseCase(context, db) { index -> if (index == 1) error("测试中断第二件") }
        try { io { failing(c, req, repo::image) }; fail("Must roll back entire save") } catch (_: IllegalStateException) {}
        assertEquals(0, io { db.itemDao().getItemCount() }); assertTrue(io { db.orderImportDao().sources().isEmpty() })
        assertTrue(File(context.filesDir, "images").listFiles().orEmpty().isEmpty())
        assertTrue(io { db.brandDao().getAllBrandsList().isEmpty() }); assertTrue(io { db.categoryDao().getAllCategoriesList().isEmpty() })
        val result = io { repo.addWishes(req) }; assertEquals(2, result.added)
        val protected = io { db.itemDao().getItemById(result.itemIds.first())!! }.imageUrls.first()
        val orphan = io { ImageFileHelper.copyExistingImageToInternalStorage(context, protected) }
        File(context.noBackupFilesDir, "square-wish-images.json").writeText(SquarePackage.gson.toJson(listOf(protected, orphan)))
        io { AddSquareWishUseCase(context, db).cleanupInterrupted() }
        assertTrue(File(protected).exists()); assertFalse(File(orphan).exists()); assertFalse(File(context.noBackupFilesDir, "square-wish-images.json").exists())
        val remaining=io{db.orderImportDao().sources().single{it.itemId==result.itemIds.last()}}
        val hashes=remaining.imagePaths.map(::hash)
        io{ItemRepository(db.itemDao(),database=db,context=context).deleteItem(db.itemDao().getItemById(result.itemIds.first())!!)}
        assertEquals(hashes,remaining.imagePaths.map(::hash))

    }

    @Test fun personalGroupKeepsOwnSpecsAndBackupSixteenRoundTrip() {
        val c = imported(); val req = request(c, listOf(SquareBrowseChoice(part(c, "dorothy-cotton", "JSK").key, "蓝色", "L"), SquareBrowseChoice(part(c, "dorothy-cotton", "KC").key)), true)
        val result = io { repo.addWishes(req) }; assertEquals(1, result.added)
        val before = io { db.itemDao().getItemById(result.itemIds.single())!! }
        assertEquals(ItemComposition.SET, before.composition); assertNull(before.size); assertTrue(before.colors.isEmpty())
        val record = io { db.orderImportDao().sources().single() }; val data = SquareWishSnapshot.decode(record.snapshotJson, record.imagePaths.size)
        assertEquals("L", data.members[0].size); assertEquals("", data.members[1].size)
        assertNotEquals(data.members[0].photos, data.members[1].photos)
        assertNotEquals(data.members[0].documents, data.members[1].documents)
        assertEquals("胸围(cm，素鸡可拉伸)", data.members[0].sizeTable!!.columns.first())
        val originalHashes = record.imagePaths.map(::hash)
        val manager = BackupManager(context, db)
        val zip = io { manager.exportToZip().getOrThrow() }
        io { manager.previewBackup(zip).getOrThrow(); manager.importBackup(zip).getOrThrow() }
        val after = io { db.itemDao().getItemById(before.id)!! }; val restored = io { db.orderImportDao().sources().single() }
        assertEquals(data, SquareWishSnapshot.decode(restored.snapshotJson, restored.imagePaths.size))
        assertEquals(originalHashes, restored.imagePaths.map(::hash)); assertEquals(before.copy(imageUrls = after.imageUrls, sizeChartImageUrl = after.sizeChartImageUrl, sizeChartImageUrls = after.sizeChartImageUrls), after)
        assertEquals(0, io { db.priceDao().getAllPricesList().size })
        val json = io { manager.exportToJson().getOrThrow() }; val text = context.contentResolver.openInputStream(json)!!.bufferedReader().use { it.readText() }
        assertEquals(16, JsonParser.parseString(text).asJsonObject["formatVersion"].asInt)
        val csv = io { manager.exportToCsv().getOrThrow() }; val csvText = context.contentResolver.openInputStream(csv)!!.bufferedReader().use { it.readText() }
        assertTrue(csvText.contains("广场心愿成员")); assertFalse(csvText.contains(req.choices[0].key))
        val invalid = JsonParser.parseString(text).asJsonObject.apply { addProperty("formatVersion", 15) }
        val wrong = File(context.cacheDir, "invalid-old.json").apply { writeText(invalid.toString()) }
        assertTrue(io { manager.previewBackup(Uri.fromFile(wrong)).isFailure })
        assertEquals(1, io { db.itemDao().getItemCount() })
    }

    @Test fun missingMappingUnknownCategoryAndOldDraftStaySafe() {
        val c = imported(); val brooch = part(c, "sleeve-repeat", "胸针")
        val req = request(c, listOf(SquareBrowseChoice(brooch.key)), event = "sleeve-repeat")
        val result = io { repo.addWishes(req) }; val item = io { db.itemDao().getItemById(result.itemIds.single())!! }
        assertTrue(item.imageUrls.isEmpty()); assertNull(item.size); assertTrue(item.colors.isEmpty())
        val groupSku = c.products.single { it.id == "1088022205740" }.skus.first { it.content == "JSK" && it.color == "蓝色" && it.size == "M" }
        val old = io { repo.prepare("dorothy-cotton", groupSku.key, null) }
        val jsk = part(c, "dorothy-cotton", "JSK")
        val paused = io { repo.addWishes(request(c, listOf(SquareBrowseChoice(jsk.key, "蓝色")))) }
        assertEquals(listOf(old.draftId), paused.draftIds); assertEquals(1, io { db.itemDao().getItemCount() })
        assertNotNull(io { drafts.get(old.draftId!!) })
        val bundle=c.products.single{it.id=="1088022205740"}.skus.first{it.content=="JSK+KC"&&it.color=="蓝色"&&it.size=="M"}
        val oldKc=io{repo.prepare("dorothy-cotton",bundle.key,"KC")}
        val kc=part(c,"dorothy-cotton","KC")
        assertTrue(oldKc.draftId in io{repo.addWishes(request(c,listOf(SquareBrowseChoice(kc.key))))}.draftIds)

        val unknownC = c.copy(products = c.products.map { p -> p.copy(skus = p.skus.map { sku -> sku.copy(parts = sku.parts.map { it.copy(category = null) }) }) })
        val unknownPart = part(unknownC, "liana", "袖套")
        val unknown = io { AddSquareWishUseCase(context, db)(unknownC, request(unknownC, listOf(SquareBrowseChoice(unknownPart.key)), event = "liana"), repo::image) }
        assertEquals(CategorySemantic.UNCONFIRMED, io { db.categoryDao().getCategoryById(db.itemDao().getItemById(unknown.itemIds.single())!!.categoryId)!! }.categorySemantic)
        try { io { repo.addWishes(request(c, listOf(SquareBrowseChoice(jsk.key)), event = "pilgrim-balance")) }; fail("Tail must stay read only") } catch (_: IllegalArgumentException) {}
    }

    @Test fun corruptPhotoAndStaleRequestNeverPartiallySave() {
        val c = imported(); val jsk = part(c, "dorothy-cotton", "JSK"); val req = request(c, listOf(SquareBrowseChoice(jsk.key, "蓝色")))
        val photo = SquareBrowse.select(c, jsk, req.choices.single()).photos.first(); File(repo.image(photo)).writeBytes(byteArrayOf(1, 2, 3))
        try { io { repo.addWishes(req) }; fail("Corrupt original must fail") } catch (_: IllegalArgumentException) {}
        assertEquals(0, io { db.itemDao().getItemCount() }); assertTrue(File(context.filesDir, "images").listFiles().orEmpty().isEmpty())
        try { io { repo.addWishes(req.copy(eventRevision = req.eventRevision + 1)) }; fail("Stale request must fail") } catch (_: IllegalArgumentException) {}
        assertEquals(0, io { db.itemDao().getItemCount() })
    }

    @Test fun nativeDirectWishAndPartColourParametersKeepBrowsing() {
        val c = imported(); show(); compose.runOnIdle { model!!.open("dorothy-cotton") }
        compose.onNodeWithTag("square-product-images").assertIsDisplayed()
        compose.onNodeWithTag("square-add-wish").assertIsDisplayed().performClick()
        compose.waitUntil(15000) { io { db.itemDao().getItemCount() } == 1 && model!!.busy.value == null }
        compose.onNodeWithTag("square-product").assertExists(); compose.onNodeWithTag("square-wish-sheet").assertDoesNotExist()
        assertEquals(0L, openedId); assertNull(io { db.itemDao().getAllItemsList().single() }.size)
        compose.onNodeWithTag("square-color:黑色").performScrollTo().performClick()
        compose.waitUntil(5000) { model!!.currentBrowseChoice()?.color == "黑色" }
        compose.runOnIdle { assertTrue("Black must not match blue: ${model!!.currentWishMatch()}",model!!.currentWishMatch().exact.isEmpty()) }
        screenshot("after-black-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
        compose.onNodeWithTag("square-add-wish").assertTextContains("加入心愿")
        assertEquals("Colour tap must not trigger saved-item navigation",0L,openedId)
        compose.onNodeWithTag("square-color:蓝色").performClick()
        compose.onNodeWithText("已在心愿 · 查看").assertExists()
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-part-list"))
        compose.onNodeWithTag("square-part-list").performScrollToNode(hasTestTag("square-part-KC"))
        compose.onNodeWithTag("square-part-KC").performClick()
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-parameters"))
        compose.onNodeWithText("宽(cm)", substring = true).assertExists()
        screenshot("kc-parameters")
        compose.runOnIdle { money.value = false }
        compose.onAllNodes(hasText("¥310.40", substring = true)).assertCountEquals(0)
    }

    @Test fun fourteenSkinsAndSmallTextUseActualBrowsePages() {
        imported(); show(); val small = target.resources.configuration.screenWidthDp <= 320
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night; model!!.back() }
            compose.onNodeWithTag("square-feed").assertExists()
            val suffix = "${type.name.lowercase()}-${if (night) "dark" else "light"}${if (small) "-small" else ""}"
            screenshot("feed-$suffix")
            compose.runOnIdle { model!!.open("dorothy-cotton") }
            compose.onNodeWithTag("square-add-wish").assertIsDisplayed(); screenshot("product-$suffix")
            compose.onNodeWithText("选多件").performClick()
            compose.onNodeWithTag("square-wish-part-KC").assertExists(); compose.onNodeWithTag("square-save-wishes").assertIsDisplayed()
            screenshot("multi-$suffix")
            compose.runOnIdle { model!!.closeMulti(); model!!.back() }
        }
        compose.runOnIdle { model!!.open("sleeve-repeat"); model!!.selectBrowse(model!!.browseParts().single { it.partKey == "胸针" }.key) }
        compose.onNodeWithText("暂无单品图").assertExists(); compose.onNodeWithTag("square-add-wish").assertIsDisplayed()
        screenshot("missing-component-${if (small) "small" else "normal"}")
        compose.runOnIdle { model!!.back(); model!!.open("sleeve-balance") }
        compose.onNodeWithTag("square-add-wish").assertDoesNotExist(); compose.onNodeWithText("选多件").assertDoesNotExist()
        screenshot("tail-${if (small) "small" else "normal"}")
    }

    @Test fun smallDoubleFontAndSearchKeyboardKeepPrimaryReachable() {
        Assume.assumeTrue(target.resources.configuration.screenWidthDp <= 320 && target.resources.configuration.fontScale >= 1.99f)
        imported(); show()
        compose.onNodeWithTag("square-search").performClick().performTextInput("漫游")
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        screenshot("small-real-ime-search")
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.runOnIdle { model!!.open("dorothy-cotton") }
        compose.onNodeWithTag("square-add-wish").assertIsDisplayed()
        compose.onNodeWithText("选多件").performClick(); compose.onNodeWithTag("square-save-wishes").assertIsDisplayed()
        screenshot("small-multi-primary")
    }

    @Test fun savedSelectionRestoresAndAlreadyOwnedDoesNotBecomeWishedAgain() {
        val c = imported(); val saved = SavedStateHandle(); lateinit var first: SquareViewModel
        inst.runOnMainSync { first = SquareViewModel(repo, saved, money); store.put("first", first) }
        compose.waitUntil(15000) { first.busy.value == null }
        inst.runOnMainSync { first.open("dorothy-cotton"); first.chooseBrowse(size = "L"); first.openMulti(); first.toggleWish(part(c, "dorothy-cotton", "KC").key); first.groupWishes(true) }
        val state = saved.keys().associateWith { saved.get<Any?>(it) }; lateinit var restored: SquareViewModel
        inst.runOnMainSync { restored = SquareViewModel(repo, SavedStateHandle(state), money); store.put("restored", restored) }
        compose.waitUntil(15000) { restored.busy.value == null }
        assertEquals(first.currentBrowseChoice(), restored.currentBrowseChoice()); assertEquals(first.selectedWishes(), restored.selectedWishes()); assertTrue(restored.multiGrouped.value)
        inst.runOnMainSync { restored.saveWishes(multiple = true) }
        compose.waitUntil(15000) { restored.wishResult.value?.itemIds?.size == 1 && restored.busy.value == null }
        val result = restored.wishResult.value!!; val item = io { db.itemDao().getItemById(result.itemIds.single())!! }
        io { db.itemDao().updateItem(item.copy(status = ItemStatus.OWNED)) }
        val again = io { repo.addWishes(request(c, restored.selectedWishes(), true)) }
        assertEquals(0, again.added); assertEquals(ItemStatus.OWNED, io { db.itemDao().getItemById(item.id)!! }.status)
    }
    @Test fun optionalInfoOpensOnTapAndBackReturnsToProduct() {
        imported(); show(); compose.runOnIdle { model!!.open("dorothy-cotton") }
        compose.onNodeWithText("价格与库存为采集快照。").assertDoesNotExist()
        compose.onNodeWithContentDescription("快照信息").performScrollTo().performClick()
        compose.onNodeWithText("价格与库存为采集快照。").assertIsDisplayed()
        screenshot("info-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.waitUntil(5000) { compose.onAllNodesWithText("价格与库存为采集快照。").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("价格与库存为采集快照。").assertDoesNotExist()
        compose.onNodeWithTag("square-product").assertExists()
    }

    @Test fun secondShopShortOpAndSleevesHaveIndependentImagesQuotesAndTables() {
        val c=imported(); show(); val short=part(c,"liana","十字OP短款"); val sleeve=part(c,"liana","袖套")
        compose.runOnIdle { model!!.open("liana"); model!!.selectBrowse(short.key) }
        compose.onNodeWithText("¥610.00").assertExists()
        val shortPhotos=model!!.currentBrowseSelection()!!.photos
        assertTrue(shortPhotos.isNotEmpty())
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-parameters"))
        assertTrue(compose.onAllNodes(hasText("短款裙长(cm)",substring=true)).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("长款裙长(cm)",substring=true).assertDoesNotExist()
        screenshot("lilizi-short-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
        compose.runOnIdle { model!!.selectBrowse(sleeve.key) }
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-product-title"))
        compose.onNodeWithText("¥175.00").assertExists()
        assertNotEquals(shortPhotos,model!!.currentBrowseSelection()!!.photos)
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-parameters"))
        assertTrue(compose.onAllNodes(hasText("推荐臂围(cm)",substring=true)).fetchSemanticsNodes().isNotEmpty())
        screenshot("lilizi-sleeves-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
    }

    @Test fun taobaoIntentPriorityFallbackAndUntrustedSources() {
        val calls = mutableListOf<Intent>()
        var mode = "app"
        val handler = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) {
                calls.add(Intent(intent))
                if (mode == "blocked") throw SecurityException()
                if (mode == "browser" && intent.`package` != null) throw ActivityNotFoundException()
            }
        }
        val url = "https://item.taobao.com/item.htm?id=123456"
        assertTrue(openSquareTaobao(handler, url)); assertEquals(1, calls.size)
        assertEquals("com.taobao.taobao", calls.single().`package`)
        assertEquals(Intent.ACTION_VIEW, calls.single().action); assertEquals(url, calls.single().dataString)
        calls.clear(); mode = "browser"
        assertTrue(openSquareTaobao(handler, url)); assertEquals(2, calls.size); assertNull(calls.last().`package`)
        calls.clear(); mode = "blocked"
        assertFalse(openSquareTaobao(handler, url)); assertEquals(2, calls.size)
        for (bad in listOf("", "http://item.taobao.com/item.htm?id=1", "https://taobao.com.evil.test/item", "https://taobao.com@evil.test/item", "https://evil.test@item.taobao.com/item", "intent://item", "https://item.taobao.com:444/item", "https://example.com/item")) {
            calls.clear(); assertFalse(bad, openSquareTaobao(handler, bad)); assertTrue(calls.isEmpty())
        }
        assertNotNull(squareTaobaoUri("https://detail.tmall.com/item.htm?id=123"))
    }

    @Test fun taobaoCurrentSourcesCancelTailAndCopyFailure() {
        imported(); show(); compose.runOnIdle { model!!.open("dorothy-cotton") }
        fun sources() = model!!.currentBrowseSelection()!!.rows.map { it.first }.filter { squareTaobaoUri(it.url) != null }.distinctBy { it.url }
        val products = sources(); assertTrue(products.size > 1)
        taobaoButton().performScrollTo().performClick()
        compose.onNodeWithText("选择商品链接").assertIsDisplayed()
        products.forEach { compose.onNodeWithTag("square-taobao-source:${it.id}").assertExists() }
        compose.onNodeWithText("取消").performClick(); assertTrue(outgoing.isEmpty())
        compose.onNodeWithTag("square-open-taobao").performClick()
        compose.onNodeWithTag("square-taobao-source:${products.last().id}").performScrollTo().performClick()
        assertEquals(products.last().url, outgoing.single().dataString)
        assertEquals("com.taobao.taobao", outgoing.single().`package`)
        compose.onNodeWithTag("square-product").assertExists()
        compose.runOnIdle { model!!.chooseBrowse(color = "黑色", size = "L"); outgoing.clear(); linkMode = "browser" }
        val black = sources()
        taobaoButton().performScrollTo().performClick()
        if (black.size > 1) compose.onNodeWithTag("square-taobao-source:${black.first().id}").performScrollTo().performClick()
        assertEquals(black.first().url, outgoing.last().dataString); assertNull(outgoing.last().`package`)
        assertEquals("黑色", model!!.currentBrowseChoice()!!.color); assertEquals("L", model!!.currentBrowseChoice()!!.size)
        compose.runOnIdle { model!!.back(); model!!.open("sleeve-balance"); outgoing.clear(); linkMode = "blocked" }
        compose.onNodeWithTag("square-add-wish").assertDoesNotExist()
        taobaoButton().performScrollTo().performClick()
        compose.onNodeWithText("无法打开淘宝，请复制链接后重试").assertIsDisplayed()
        screenshot("taobao-failure")
        val expected = outgoing.last().dataString
        compose.onNodeWithText("复制链接").performClick()
        compose.runOnIdle {
            val clip = target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(expected, clip.primaryClip!!.getItemAt(0).text.toString())
        }
        assertEquals(0, io { db.itemDao().getItemCount() }); assertEquals(0, io { db.priceDao().getAllPricesList().size })
    }

    @Test fun taobaoFourteenSkinsAndLargeText() {
        imported(); show(); val small = target.resources.configuration.screenWidthDp <= 320
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night; model!!.back(); model!!.open("dorothy-cotton") }
            val suffix = "${type.name.lowercase()}-${if (night) "dark" else "light"}${if (small) "-small" else ""}"
            taobaoButton().performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("square-add-wish").assertIsDisplayed(); screenshot("taobao-product-$suffix")
            compose.onNodeWithTag("square-open-taobao").performClick()
            compose.onNodeWithText("选择商品链接").assertIsDisplayed(); screenshot("taobao-chooser-$suffix")
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { model!!.back(); model!!.open("sleeve-balance") }
            taobaoButton().performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("square-add-wish").assertDoesNotExist(); screenshot("taobao-tail-$suffix")
        }
        assertTrue(outgoing.isEmpty())
    }

    @Test fun taobaoUnavailableSourceStaysDisabled() {
        val products = mutableStateOf(emptyList<SquareProduct>())
        compose.setContent { LolitaTheme { SquareTaobaoButton(products.value, remember { SnackbarHostState() }) } }
        compose.onNodeWithTag("square-open-taobao").assertIsNotEnabled()
        compose.onNodeWithText("暂无淘宝链接").assertExists()
        val c = imported()
        compose.runOnIdle { products.value = listOf(c.products.first().copy(url = "https://example.com/item")) }
        compose.onNodeWithTag("square-open-taobao").assertIsNotEnabled()
    }

}
