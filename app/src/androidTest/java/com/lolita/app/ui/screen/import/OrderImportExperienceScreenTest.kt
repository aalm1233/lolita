package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.model.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.SizeOptionsViewModel
import com.lolita.app.ui.screen.item.PurchaseReviewContent
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalLayoutApi::class)
class OrderImportExperienceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val target = ins.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var sourcePhoto: String
    private lateinit var sourceDetail: String
    private lateinit var root: File
    private lateinit var context: ContextWrapper
    private lateinit var prefs: AppPreferences
    private lateinit var scope: CoroutineScope
    private lateinit var vm: TaobaoImportViewModel
    private lateinit var supplement: ItemSourceSupplementViewModel
    private val store = ViewModelStore()
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val compact = mutableStateOf(false)
    private val page = mutableStateOf("ENTRY")
    private lateinit var rows: List<ImportItemState>
    private lateinit var orders: List<TaobaoOrder>
    private lateinit var item: Item
    private lateinit var host: android.view.View
    private var keyboard: SoftwareKeyboardController? = null
    private val output by lazy { File(target.filesDir, "order-import-native").apply { mkdirs() } }

    @Before
    fun setup() {
        root = File(target.cacheDir, "import-screen-${UUID.randomUUID()}").apply { mkdirs() }
        context =
            object : ContextWrapper(target) {
                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            }
        fun fixtureImage(name: String, width: Int, height: Int): String {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()
            for (y in 0 until height step 80) {
                paint.color =
                    if ((y / 80) % 2 == 0) android.graphics.Color.rgb(75, 100, 145)
                    else android.graphics.Color.rgb(230, 190, 210)
                canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + 80).toFloat(), paint)
            }
            val file = File(context.filesDir, name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            return file.absolutePath
        }
        sourcePhoto = fixtureImage("fixture-photo.png", 400, 600)
        sourceDetail = fixtureImage("fixture-detail.png", 400, 2400)
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        ImageFileHelper.configure(context, db)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefs =
            AppPreferences(
                context,
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { File(root, "settings.preferences_pb") },
                ),
            )
        runBlocking {
            db.brandDao().insertBrand(Brand(id = 1, name = "云间庭院原创设计工坊与长名称店铺"))
            db.categoryDao()
                .insertCategory(
                    Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK)
                )
            item =
                Item(
                    id = 1,
                    brandId = 1,
                    categoryId = 1,
                    name = "云间庭院·晨雾长裙",
                    description = "手工备注",
                    size = "M",
                    status = ItemStatus.OWNED,
                )
            db.itemDao().insertItem(item)
            prefs.setShowTotalPrice(true)
        }
        compose.runOnIdle {
            vm =
                TaobaoImportViewModel(
                    target.applicationContext as Application,
                    BrandRepository(db.brandDao(), db.itemDao(), db),
                    CategoryRepository(db.categoryDao(), db.itemDao(), db),
                    ItemRepository(db.itemDao(), database = db, context = context),
                    PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                    OrderRepository(db, context),
                    db,
                )
            supplement =
                ItemSourceSupplementViewModel(
                    target.applicationContext as Application,
                    1,
                    db,
                    ItemRepository(db.itemDao(), database = db, context = context),
                    ItemDraftRepository(
                        db,
                        ItemRepository(db.itemDao(), database = db, context = context),
                    ),
                )
            store.put("import", vm)
            store.put("supplement", supplement)
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        SizeOptionsViewModel(prefs, ItemRepository(db.itemDao())) as T
                },
            )[SizeOptionsViewModel::class.java]
        }
        compose.waitUntil(10000) {
            !vm.uiState.value.isRestoring &&
                vm.uiState.value.brands.any { it.id == 1L } &&
                !supplement.state.value.busy
        }
        orders =
            (0..1).map { i ->
                val original =
                    TaobaoOrderItem(
                        if (i == 0) "云间庭院·晨雾长裙" else "深海航线·蕾丝领衬衫",
                        "https://item.taobao.com/item.htm?id=${i+1}",
                        "绀色;M;JSK",
                        i + 1,
                        1388.5,
                        "JSK",
                        "M",
                        "绀色",
                        "ORDER-202610-${i+1}",
                        "2026-10-01",
                        "云间庭院原创设计工坊与长名称店铺",
                    )
                TaobaoOrder(
                    original.orderId,
                    original.orderTime,
                    "交易成功",
                    original.shopName,
                    1398.5,
                    10.0,
                    listOf(original),
                )
            }
        rows =
            orders.mapIndexed { i, o ->
                ImportItemState(
                    o.items[0],
                    o.items[0].name,
                    1,
                    1,
                    "绀色",
                    "M",
                    1388.5,
                    "2026-10-01",
                    shipping = 10.0,
                    paidAmount = 1398.5,
                    financialConfirmed = i == 0,
                    sourceKey = OrderImportSource.key(o.items[0], 0),
                    sourceGroupKey = OrderImportSource.group(o.items[0]),
                )
            }
        show("ENTRY")
        compose.setContent {
            host = LocalView.current
            val controller = LocalSoftwareKeyboardController.current
            SideEffect {
                keyboard = controller
                (host.context as android.app.Activity)
                    .window
                    .setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    )
            }
            val config =
                Configuration(LocalConfiguration.current).apply {
                    uiMode =
                        (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark.value) Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO
                }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(density.density, if (compact.value) 2f else 1f),
                LocalViewModelStoreOwner provides
                    object : ViewModelStoreOwner {
                        override val viewModelStore = store
                    },
            ) {
                LolitaTheme(skin.value, dark.value) {
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides
                            MaterialTheme.colorScheme.onSurface
                    ) {
                        key(page.value, skin.value, dark.value, compact.value) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                SkinArtBackground(Modifier.matchParentSize())
                                val state by vm.uiState.collectAsState()
                                when (page.value) {
                                    "DETAIL" -> ImportDetailContent(state, vm, {})
                                    "supplement" ->
                                        ProductSupplementCompare(
                                            ProductSupplementState(
                                                item = item,
                                                candidate =
                                                    TaobaoProductDraft(
                                                        "网页候选长裙",
                                                        "云间庭院",
                                                        "L",
                                                        "材质：棉；颜色：绀色",
                                                        "",
                                                        "https://item.taobao.com/item.htm?id=1",
                                                        emptyList(),
                                                        imagePaths = listOf(sourcePhoto),
                                                        sizeChartImagePaths = listOf(sourceDetail),
                                                    ),
                                                brands = state.brands,
                                                categories = state.categories,
                                                selected = setOf("photos", "details"),
                                                busy = false,
                                            ),
                                            supplement,
                                            {},
                                            {},
                                        )
                                    "purchase" ->
                                        PurchaseReviewContent(
                                            listOf(
                                                ItemPurchaseReview(
                                                    item,
                                                    1,
                                                    1,
                                                    PurchaseRecordStatus.HISTORICAL_OWNED,
                                                    true,
                                                ),
                                                ItemPurchaseReview(
                                                    item.copy(id = 2, name = "同单蕾丝领衬衫"),
                                                    1,
                                                    1,
                                                    PurchaseRecordStatus.HISTORICAL_OWNED,
                                                    true,
                                                ),
                                            ),
                                            false,
                                            {},
                                            {},
                                            {},
                                        )
                                    else ->
                                        OrderImportExperience(state, vm, {}, {}, {}, {}, {}, prefs)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @After
    fun close() = runBlocking {
        compose.runOnIdle {
            keyboard?.hide()
            store.clear()
        }
        delay(250)
        scope.cancel()
        db.close()
        root.deleteRecursively()
        ImageFileHelper.configure(target, AppModule.database())
    }

    @Suppress("UNCHECKED_CAST")
    private fun state() =
        TaobaoImportViewModel::class
            .java
            .getDeclaredField("_uiState")
            .apply { isAccessible = true }
            .get(vm) as MutableStateFlow<TaobaoImportUiState>

    private fun show(value: String) {
        compose.runOnIdle {
            page.value = value
            val session = UUID.randomUUID().toString()
            state().value =
                vm.uiState.value.copy(
                    sessionId = null,
                    currentStep =
                        runCatching { ImportStep.valueOf(value) }.getOrDefault(ImportStep.ENTRY),
                    orders = orders,
                    importItems = rows,
                    selectedItems = setOf("${orders[0].orderId}:0", "${orders[1].orderId}:0"),
                    fileLoaded = true,
                    fileCount = 2,
                    isRestoring = false,
                    sessions =
                        if (value == "ENTRY")
                            listOf(ImportSessionSummary(session, 1800000000000L, 2, 1))
                        else emptyList(),
                    files =
                        listOf(
                            OrderImportFile(
                                sessionId = session,
                                name = "十月订单.xlsx",
                                sha256 = "a".repeat(64),
                                state = "READY",
                                dataJson = Gson().toJson(orders),
                            ),
                            OrderImportFile(
                                sessionId = session,
                                name = "待重新选择.xlsx",
                                sha256 = "",
                                state = "FAILED",
                                problem = "表头缺少商品金额，请重新导出",
                            ),
                        ),
                    conflicts =
                        if (value == "CONFLICT")
                            listOf(
                                ImportSourceConflict(
                                    orders[0].orderId,
                                    listOf(
                                        orders[0],
                                        orders[0].copy(
                                            totalPaid = 1298.5,
                                            sourceFileName = "更新订单.xlsx",
                                        ),
                                    ),
                                )
                            )
                        else emptyList(),
                    importResult = ImportResult(1, 0, 1, listOf(1)),
                    resultItems = listOf(item),
                    query = "",
                    selectionFilter = "全部",
                )
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        File(output, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun allChangedPagesRenderWithSevenRealSkinsInLightAndDark() {
        val pages =
            listOf(
                "ENTRY",
                "FILES",
                "SELECT",
                "CONFLICT",
                "PREPARE",
                "DETAIL",
                "REVIEW",
                "RESULT",
                "ADDED",
                "STORAGE",
                "supplement",
                "purchase",
            )
        for (s in SkinType.entries) for (d in listOf(false, true)) {
            compose.runOnIdle {
                skin.value = s
                dark.value = d
            }
            for (p in pages) {
                show(p)
                capture("${s.name}-${if(d)"dark" else "light"}-$p")
            }
        }
    }

    @Test
    fun searchPreservesHiddenSelectionAndPrivacyChangesImmediately() {
        show("SELECT")
        compose.onNodeWithText("搜索商品、店铺或订单号").performTextInput("晨雾")
        compose.waitUntil { visibleOrderLines(vm.uiState.value).size == 1 }
        compose.onNodeWithTag("orderImportExperience").performScrollToNode(hasText("另有 1 条已选被筛选隐藏"))
        compose.onNodeWithText("另有 1 条已选被筛选隐藏").assertExists()
        compose.onNodeWithText("清除当前结果").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(setOf("${orders[1].orderId}:0"), vm.uiState.value.selectedItems)
        }
        runBlocking { prefs.setShowTotalPrice(false) }
        compose.onNodeWithTag("orderImportExperience").performScrollToNode(hasText("金额已隐藏"))
        compose.onNodeWithText("金额已隐藏").assertExists()
        compose.onNodeWithText("¥1398.50").assertDoesNotExist()
        capture("selection-hidden-money")
    }

    @Test
    fun detailUsesFinalTitleWithSevenSkinsAndSmallFontPagesKeepPrimaryAction() {
        for (s in SkinType.entries) for (d in listOf(false, true)) {
            compose.runOnIdle {
                skin.value = s
                dark.value = d
            }
            show("DETAIL")
            capture("${s.name}-${if(d)"dark" else "light"}-DETAIL")
        }
        compose.runOnIdle {
            compact.value = true
            skin.value = SkinType.DEFAULT
            dark.value = false
        }
        for (p in
            listOf(
                "ENTRY",
                "FILES",
                "SELECT",
                "CONFLICT",
                "PREPARE",
                "DETAIL",
                "REVIEW",
                "RESULT",
                "ADDED",
                "STORAGE",
                "supplement",
                "purchase",
            )) {
            show(p)
            capture("small-font2-$p")
        }
    }

    @Test
    fun paidDatePickerKeepsSelectedLocalDayAndIncompleteMoneyCannotBeConfirmed() {
        show("DETAIL")
        compose.onNodeWithTag("importDetailList").performScrollToNode(hasText("选择实际付款日期"))
        compose.onNodeWithText("选择实际付款日期").performScrollTo().performClick()
        compose.onNodeWithText("使用所选日期").performClick()
        compose.runOnIdle {
            assertEquals("2026-10-01", vm.uiState.value.importItems[0].purchaseDate)
        }
        compose.onNodeWithTag("importDetailList").performScrollToNode(hasText("本件分摊邮费"))
        compose.onNodeWithText("本件分摊邮费").performScrollTo().performTextReplacement("12.")
        compose.runOnIdle {
            vm.updateImportItem(0) { it.copy(financialConfirmed = true) }
            assertFalse(isImportItemReady(0, vm.uiState.value.importItems))
        }
    }

    @Test
    fun supplementPhotosAndLongDetailsOpenFullFitWithoutChangingOriginalBytes() {
        val before =
            listOf(sourcePhoto, sourceDetail).map {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(File(it).readBytes())
                    .toList()
            }
        show("supplement")
        for ((description, name) in
            listOf("完整商品图" to "supplement-photo", "完整资料图" to "supplement-long-detail")) {
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasContentDescription(description))
            compose.onNodeWithContentDescription(description).performScrollTo().performClick()
            compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("图片预览 1").onFirst().assertIsDisplayed()
            compose.waitForIdle()
            File(output, "$name.png").outputStream().use {
                ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
        }
        assertEquals(
            before,
            listOf(sourcePhoto, sourceDetail).map {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(File(it).readBytes())
                    .toList()
            },
        )
    }

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText().trim() }

    @Test
    fun smallScreenTwoTimesFontKeepsFinanceInputAndSaveAboveRealKeyboard() {
        val previous = shell("settings get secure show_ime_with_hard_keyboard")
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            compose.runOnIdle { compact.value = true }
            show("DETAIL")
            compose.onNodeWithTag("importDetailList").performScrollToNode(hasText("本件分摊邮费"))
            compose.onNodeWithText("本件分摊邮费").performScrollTo().performClick()
            compose.runOnIdle { keyboard?.show() }
            compose.waitUntil(10000) {
                androidx.core.view.ViewCompat.getRootWindowInsets(host)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
            compose.onNodeWithText("本件分摊邮费").assertIsDisplayed().performTextReplacement("12.")
            compose.onNodeWithText("核对本件并继续").assertIsDisplayed()
            compose.waitForIdle()
            compose.waitUntil(5000) {
                val field = compose.onNodeWithText("本件分摊邮费").fetchSemanticsNode().boundsInRoot
                val save = compose.onNodeWithText("核对本件并继续").fetchSemanticsNode().boundsInRoot
                field.height > 0 && field.bottom <= save.top && field.top >= 0
            }
            ins.waitForIdleSync()
            File(output, "small-font2-real-ime.png").outputStream().use {
                ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle {
                assertEquals("12.", vm.uiState.value.importItems[0].shippingInput)
                keyboard?.hide()
            }
        } finally {
            if (previous == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
}
