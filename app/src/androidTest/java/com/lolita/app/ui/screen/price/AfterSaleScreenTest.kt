package com.lolita.app.ui.screen.price

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.domain.usecase.*
import com.lolita.app.ui.screen.budget.*
import com.lolita.app.ui.screen.stats.*
import com.lolita.app.ui.screen.item.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class AfterSaleScreenTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation(); private val context = inst.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var after: AfterSaleRepository
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository
    private lateinit var prefs: AppPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefFile = File(context.cacheDir, "after-sale-${UUID.randomUUID()}.preferences_pb")
    private val models = ViewModelStore(); private val tracked = mutableListOf<ViewModel>()
    private var itemId = 0L; private var wish = 0L; private var priceId = 0L; private var activeId = 0L
    private lateinit var snap: AfterSaleSnapshot; private lateinit var active: AfterSaleSnapshot
    private lateinit var reference: ItemReferencePrice; private lateinit var wishItem: Item
    private lateinit var editor: AfterSaleEditorViewModel
    private lateinit var overview: PriceManageViewModel; private lateinit var ledger: PaymentManageViewModel
    private lateinit var stats: StatsViewModel; private lateinit var budget: BudgetViewModel
    private lateinit var referenceOverview: PriceManageViewModel
    private val page = mutableStateOf("reference"); private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false); private val big = mutableStateOf(false)
    private var saved = 0; private var back = 0; private var destination = ""; private var imeBottom = 0
    private fun <T : ViewModel> track(vm: T): T { tracked += vm; models.put("${tracked.size}", vm); return vm }
    private fun <T> main(action: () -> T): T = runBlocking { withContext(Dispatchers.Main) { action() } }
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        orders = OrderRepository(db, context); after = AfterSaleRepository(db, context); items = ItemRepository(db.itemDao(), database = db)
        prefs = AppPreferences(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { prefFile })); prefs.setShowTotalPrice(true)
        val brand = db.brandDao().insertBrand(Brand(name = "真实原生验收品牌")); val category = db.categoryDao().insertCategory(Category(name = "JSK"))
        suspend fun item(name: String) = db.itemDao().insertItem(Item(name = name, description = "", brandId = brand, categoryId = category, status = ItemStatus.WISHED, size = "M"))
        itemId = item("蔷薇花园连衣裙 · 莓粉色 M · 保留原订单")
        wish = item("星夜花园 · 心愿参考")
        suspend fun order(id: Long): Long {
            val price = orders.createOrder(Price(itemId = id, type = PriceType.DEPOSIT_BALANCE, totalPrice = 692.0), listOf(
                PaymentStage(priceId = 0, name = "定金", amount = 200.0), PaymentStage(priceId = 0, name = "尾款", amount = 492.0)))
            orders.recordPayment(price, 200.0, System.currentTimeMillis() - 86400000)
            after.recordRefund(OrderRefund(priceId = price, amount = 50.0, kind = RefundKind.PRICE_DIFFERENCE, receivedAt = System.currentTimeMillis() - 1000, note = "已核对到账，原付款保留", operationId = UUID.randomUUID().toString()), after.snapshot(price)!!.fingerprint)
            return price
        }
        priceId = order(itemId); after.cancelOrder(priceId, 200.0, "双方确认取消", after.snapshot(priceId)!!.fingerprint, true)
        activeId = order(item("海军领长裙 · 当前购买"))
        after.saveReference(wish, 680.0, "店铺标价，购买时再核对", null)
        reference = after.getReference(wish)!!; wishItem = db.itemDao().getItemById(wish)!!
        snap = after.snapshot(priceId)!!; active = after.snapshot(activeId)!!
        main {
            editor = track(AfterSaleEditorViewModel(after, items)); overview = track(PriceManageViewModel(orders, items, active.item.id))
            referenceOverview = track(PriceManageViewModel(orders, items, wish))
            ledger = track(PaymentManageViewModel(orders, priceId))
            budget = track(BudgetViewModel(BudgetRepository(db.budgetDao()), prefs))
            stats = track(StatsViewModel(items, CoordinateRepository(db.coordinateDao(), db.itemDao(), db), OutfitLogRepository(db.outfitLogDao(), db), PriceRepository(db.priceDao(), db.paymentDao(), context, db), prefs))
        }
        Unit
    }
    @After fun cleanup() = runBlocking {
        val jobs = tracked.map { it.viewModelScope.coroutineContext[Job] }; main { models.clear() }; jobs.forEach { it?.join() }
        scope.cancel(); prefFile.delete(); db.close()
    }
    private fun show() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current); val view = LocalView.current
            SideEffect { imeBottom = bottom; (view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            CompositionLocalProvider(LocalPricingPreferences provides prefs, LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, if (big.value) 2f else 1f)) {
                LolitaTheme(skin.value, dark.value) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { Box(Modifier.width(if (big.value) 320.dp else 400.dp).fillMaxHeight()) {
                    SkinArtBackground()
                    key(page.value, skin.value, dark.value, big.value) { when (page.value) {
                        "reference", "refund", "cancel" -> {
                            val mode = when (page.value) { "reference" -> AfterSaleEditorMode.REFERENCE; "refund" -> AfterSaleEditorMode.REFUND; else -> AfterSaleEditorMode.CANCEL }
                            AfterSaleEditorBody(AfterSaleEditorState(mode = mode, itemName = if (mode == AfterSaleEditorMode.REFERENCE) wishItem.name else active.item.name,
                                amount = when (mode) { AfterSaleEditorMode.REFERENCE -> "680.00"; AfterSaleEditorMode.REFUND -> "50.00"; else -> "200.00" },
                                note = "已核对资料", reference = if (mode == AfterSaleEditorMode.REFERENCE) reference else null,
                                snapshot = if (mode == AfterSaleEditorMode.REFERENCE) null else active, confirmed = true, loading = false), {}, {}, {}, {}, {}, {}, {}, {})
                        }
                        "aftersale" -> AfterSaleBody(AfterSaleUiState(snap, false), {}, { destination = it.name }, {}, { _, _ -> })
                        "overview" -> PriceManageScreen(active.item.id, {}, {}, {}, viewModel = overview, onNavigateToAfterSale = { destination = "after:$it" })
                        "reference-overview" -> PriceManageScreen(wish, {}, {}, {}, viewModel = referenceOverview, onNavigateToReference = { destination = "reference" })
                        "ledger" -> PaymentManageScreen(priceId, {}, {}, ledger, onNavigateToAfterSale = { destination = "after:$priceId" })
                        "budget" -> BudgetContent(budget, onNavigateToLedger = { destination = "ledger:$it" })
                        "stats" -> StatsContent(stats)
                        "wishlist" -> {
                            val visible by prefs.showTotalPrice.collectAsState(true)
                            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                val card = ItemCardData(wishItem, "验收品牌", null, "JSK", 680.12, visible)
                                ItemCard(card, {}, {}, {})
                                Row { ItemGridCard(card, {}, {}, {}, Modifier.weight(1f)); ItemGridCard(card.copy(itemPrice = 0.0), {}, {}, {}, Modifier.weight(1f)) }
                            }
                        }
                        "detail" -> ItemOrderSummary(wishItem, ItemEditUiState(referencePrice = reference), {}, {})
                        "live" -> AfterSaleEditorScreen(AfterSaleEditorMode.REFUND, priceId, { back++ }, { saved++ }, editor)
                    } }
                } } } }
            }
        }
        compose.waitUntil(10_000) { !overview.uiState.value.isLoading && !referenceOverview.uiState.value.isLoading && !ledger.uiState.value.isLoading && !budget.state.value.loading && !stats.uiState.value.isLoading }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle(); val dir = File(context.filesDir, "after-sale-native").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun sevenSkinsLightDarkAndSmallLargeText() {
        show(); val args = InstrumentationRegistry.getArguments()
        val pages = args.getString("afterSalePages")?.split(",") ?: listOf("reference", "refund", "cancel", "aftersale", "overview", "reference-overview", "ledger", "budget", "stats", "detail")
        val sizes = when (args.getString("afterSaleSize")) { "small" -> listOf(true); "normal" -> listOf(false); else -> listOf(false, true) }
        for (name in pages) for (large in sizes) for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { page.value = name; big.value = large; skin.value = type; dark.value = night }
            if (name == "stats") compose.onNodeWithText("全部净支出").performScrollTo()
            capture("$name-${type.name.lowercase()}-${if (night) "dark" else "light"}-${if (large) "320-large" else "normal"}")
            if (large && name in listOf("reference", "refund", "cancel")) {
                compose.onNodeWithTag("afterSaleNote").performScrollTo()
                capture("$name-bottom-${type.name.lowercase()}-${if (night) "dark" else "light"}")
            }
        }
    }
    @Test fun privacyHidesReferenceRefundNetAmountsAndNotesLive() = runBlocking {
        show()
        for (name in listOf("aftersale", "overview", "reference-overview", "ledger", "budget", "stats", "detail", "wishlist")) {
            compose.runOnIdle { page.value = name }
            prefs.setShowTotalPrice(false)
            compose.waitUntil(5000) { compose.onAllNodes(hasText("¥", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onAllNodes(hasContentDescription("¥", substring = true), useUnmergedTree = true).assertCountEquals(0)
            capture("privacy-$name")
            prefs.setShowTotalPrice(true)
        }
    }
    @Test fun realImeOverRefundInlineFeedbackAndSave() = runBlocking {
        page.value = "live"; big.value = true; show()
        compose.waitUntil(10_000) { !editor.state.value.loading }
        compose.onNodeWithText("实际到账金额").performScrollTo().performClick().performTextInput("151")
        compose.onNodeWithText("超过可退余额 ¥1.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存退款到账").assertIsNotEnabled()
        compose.onNodeWithText("实际到账金额").performScrollTo().performClick().performTextReplacement("50")
        compose.waitUntil(5000) { imeBottom > 0 }
        compose.onNodeWithText("保存退款到账").assertIsDisplayed().assertIsEnabled()
        capture("refund-small-ime")
        inst.uiAutomation.takeScreenshot()?.let { bitmap -> File(context.filesDir, "after-sale-native/refund-system-ime.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        compose.onNodeWithText("保存退款到账").performClick(); compose.waitUntil(10_000) { saved == 1 }
        val result = after.snapshot(priceId)!!
        assertEquals(100.0, result.refunded, 0.0); assertEquals(100.0, result.pendingRefund!!, 0.0); assertEquals(snap.payments, result.payments)
        assertEquals(2, result.refunds.size)
    }
    @Test fun backProtectsDraftAndExplicitDiscardDoesNotWrite() = runBlocking {
        page.value = "live"; big.value = true; show()
        compose.waitUntil(10_000) { !editor.state.value.loading }
        compose.onNodeWithText("实际到账金额").performScrollTo().performClick().performTextInput("25.50")
        compose.waitUntil(5000) { imeBottom > 0 }
        inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { imeBottom == 0 }
        inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        inst.uiAutomation.takeScreenshot()?.let { bitmap -> File(context.filesDir, "after-sale-native/refund-discard-confirmation.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        compose.onNodeWithText("继续编辑").performClick()
        assertEquals("25.50", editor.state.value.amount); assertEquals(0, back)
        compose.onNodeWithTag("afterSaleBack").performClick()
        compose.onNodeWithText("放弃并返回").performClick()
        assertEquals(1, back); assertEquals(snap, after.snapshot(priceId)); assertEquals(0, saved)
    }
    @Test fun referenceAndRefundActionsNavigateWithoutWriting() = runBlocking {
        page.value = "reference-overview"; show()
        compose.onNodeWithTag("priceManageList").performScrollToNode(hasText("编辑参考价"))
        compose.onNodeWithText("编辑参考价").performClick(); assertEquals("reference", destination)
        compose.runOnIdle { page.value = "aftersale" }
        compose.onNodeWithTag("afterSaleList").performScrollToNode(hasText("记录退款到账"))
        compose.onNodeWithText("记录退款到账").performClick(); assertEquals("REFUND", destination)
        assertEquals(snap, after.snapshot(priceId)); assertEquals(reference, after.getReference(wish))
    }
}
