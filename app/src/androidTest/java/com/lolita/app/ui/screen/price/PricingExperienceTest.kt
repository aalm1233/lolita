package com.lolita.app.ui.screen.price

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.domain.usecase.*
import com.lolita.app.ui.screen.budget.*
import com.lolita.app.ui.screen.calendar.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Isolated ledger/preferences. Native UI, saved-state reconstruction, and real IME. */
class PricingExperienceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var items: ItemRepository
    private lateinit var preferences: AppPreferences
    private val store = ViewModelStore()
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferenceFile = File(context.cacheDir, "pricing-${UUID.randomUUID()}.preferences_pb")
    private val models = mutableListOf<ViewModel>()
    private var itemId = 0L
    private var priceId = 0L
    private var historicalItem = 0L
    private var historicalPrice = 0L
    private var wish = 0L
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val night = mutableStateOf(false)
    private val large = mutableStateOf(false)
    private val page = mutableStateOf("overview")
    private var destination: Pair<Long, Long?>? = null
    private var saved = 0
    private var imeBottom = 0
    private fun date(day: String) = LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun <T : ViewModel> track(model: T): T { models += model; store.put("vm-${models.size}", model); return model }
    private fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
    private fun copied(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Before fun prepare() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        orders = OrderRepository(db, context); items = ItemRepository(db.itemDao(), database = db)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferenceScope, produceFile = { preferenceFile }))
        preferences.setShowTotalPrice(true)
        val brand = db.brandDao().insertBrand(Brand(name = "价格验收品牌"))
        val category = db.categoryDao().insertCategory(Category(name = "JSK"))
        suspend fun item(name: String) = db.itemDao().insertItem(Item(name = name, brandId = brand, categoryId = category, description = "", status = ItemStatus.WISHED))
        itemId = item("蔷薇长裙 · 蓝色 M · 原记录保全")
        priceId = orders.createOrder(Price(itemId = itemId, type = PriceType.DEPOSIT_BALANCE, totalPrice = 692.0, shipping = 12.0),
            listOf(PaymentStage(priceId = 0, name = "定金", amount = 200.0, dueDate = date("2026-10-01")),
                PaymentStage(priceId = 0, name = "尾款", amount = 492.0, dueDate = date("2026-11-01"))))
        orders.recordPayment(priceId, 200.0, date("2026-10-01"))
        historicalItem = item("白蕾丝长裙 · 历史已购")
        historicalPrice = orders.createOrder(Price(itemId = historicalItem, type = PriceType.FULL, totalPrice = 888.0),
            listOf(PaymentStage(priceId = 0, name = "全款", amount = 888.0)))
        orders.confirmHistoricalPurchase(historicalItem)
        wish = item("星夜花园 · 尚未建立订单")
        val undated = item("雾蓝发带 · 日期待定")
        orders.createOrder(Price(itemId = undated, type = PriceType.FULL, totalPrice = 680.0),
            listOf(PaymentStage(priceId = 0, name = "全款", amount = 680.0)))
        Unit
    }
    @After fun cleanup() = runBlocking {
        val jobs = models.map { it.viewModelScope.coroutineContext[Job] }
        main { store.clear() }; jobs.forEach { it?.join() }; preferenceScope.cancel(); preferenceFile.delete(); db.close()
    }
    private suspend fun await(vm: PaymentEditViewModel) { withTimeout(10_000) { vm.uiState.first { !it.isLoading } } }
    private suspend fun await(vm: PriceEditViewModel) { withTimeout(10_000) { vm.uiState.first { !it.isLoading } } }

    @Test fun paymentInputsSurviveReloadAndSavedStateWithFreshLedgerValidation() = runBlocking {
        val handle = SavedStateHandle()
        val vm = main { track(PaymentEditViewModel(orders, handle)).also { it.loadPayment(priceId, null) } }
        await(vm)
        val stage = vm.uiState.value.stages.last().id
        main { vm.updateAmount("500"); vm.updatePaidDate(date("2026-09-25")); vm.updateStageId(stage); vm.loadPayment(priceId, null) }
        assertEquals("500", vm.uiState.value.amount)
        assertTrue(paymentAmountError(vm.uiState.value)!!.contains("¥8.00")); assertFalse(vm.isValid())
        val restored = main { track(PaymentEditViewModel(orders, copied(handle))).also { it.loadPayment(priceId, null) } }
        await(restored)
        assertEquals("500", restored.uiState.value.amount); assertEquals(date("2026-09-25"), restored.uiState.value.paidDate)
        assertEquals(stage, restored.uiState.value.stageId); assertTrue(restored.hasUnsavedChanges)
        main { restored.updateAmount("100") }
        assertTrue(withContext(Dispatchers.Main) { restored.save(priceId) }.isSuccess)
        assertFalse(withContext(Dispatchers.Main) { restored.save(priceId) }.isSuccess)
        assertEquals(300.0, orders.getOrder(priceId).first()!!.paidAmount, 0.0)
        val stale = main { track(PaymentEditViewModel(orders, copied(handle))).also { it.loadPayment(priceId, null) } }
        await(stale); assertTrue(stale.uiState.value.loadFailed); assertFalse(stale.isValid())
    }

    @Test fun priceDraftRetainsIdsDatesManualAmountsAndSavedState() = runBlocking {
        val before = orders.getOrder(priceId).first()!!
        val handle = SavedStateHandle()
        val vm = main { track(PriceEditViewModel(orders, items, handle)).also { it.loadPrice(priceId, itemId) } }
        await(vm)
        main { vm.updateTotalPrice("780"); vm.updateStage(1, vm.uiState.value.stages[1].copy(amount = "592", dueDate = date("2026-12-01"))); vm.loadPrice(priceId, itemId) }
        val restored = main { track(PriceEditViewModel(orders, items, copied(handle))).also { it.loadPrice(priceId, itemId) } }
        await(restored)
        assertEquals("780", restored.uiState.value.totalPrice); assertEquals(before.stages.map { it.id }, restored.uiState.value.stages.map { it.id })
        assertEquals(date("2026-12-01"), restored.uiState.value.stages[1].dueDate)
        assertEquals(before, orders.getOrder(priceId).first())
        val result = withContext(Dispatchers.Main) { restored.update(priceId) }; assertTrue(result.isSuccess)
        assertEquals(before.payments, orders.getOrder(priceId).first()!!.payments)
        assertFalse(withContext(Dispatchers.Main) { restored.update(priceId) }.isSuccess)
    }

    @Test fun missingOrderAndHistoricalUnknownDateNeverBecomeOrdinaryNewPayments() = runBlocking {
        val missing = main { track(PaymentEditViewModel(orders)).also { it.loadPayment(999999, null) } }; await(missing)
        assertTrue(missing.uiState.value.loadFailed); assertFalse(missing.isValid())
        val handle = SavedStateHandle()
        val historical = main { track(PaymentEditViewModel(orders, handle)).also { it.loadPayment(historicalPrice, null) } }; await(historical)
        assertNull(historical.uiState.value.paidDate)
        main { historical.updateAmount("200"); historical.updatePaidDateUnknown(true) }
        val restored = main { track(PaymentEditViewModel(orders, copied(handle))).also { it.loadPayment(historicalPrice, null) } }; await(restored)
        assertTrue(restored.uiState.value.isPaidDateUnknown); assertNull(restored.uiState.value.paidDate)
        assertTrue(withContext(Dispatchers.Main) { restored.save(historicalPrice) }.isSuccess)
        val order = orders.getOrder(historicalPrice).first()!!
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, order.purchaseRecordStatus)
        assertNull(order.payments.single().paidDate); assertNull(order.knownRemainingAmount)
    }

    private lateinit var detailItem: Item
    private lateinit var detailState: com.lolita.app.ui.screen.item.ItemEditUiState
    private lateinit var overview: PriceManageViewModel
    private lateinit var history: PriceManageViewModel
    private lateinit var payment: PaymentEditViewModel
    private lateinit var supplement: PaymentEditViewModel
    private lateinit var editor: PriceEditViewModel
    private lateinit var ledger: PaymentManageViewModel
    private lateinit var calendar: PaymentCalendarViewModel
    private lateinit var budget: BudgetViewModel
    private fun show() {
        runBlocking {
            detailItem = items.getItemById(itemId)!!
            val order = orders.getOrder(priceId).first()!!
            detailState = com.lolita.app.ui.screen.item.ItemEditUiState(pricesWithPayments = listOf(
                com.lolita.app.data.local.dao.PriceWithPayments(order.price, order.payments,
                    OrderBinding(itemId, priceId, PurchaseRecordStatus.TRACKED))))
        }
        main {
            overview = track(PriceManageViewModel(orders, items, itemId)); history = track(PriceManageViewModel(orders, items, historicalItem))
            payment = track(PaymentEditViewModel(orders)); supplement = track(PaymentEditViewModel(orders)); editor = track(PriceEditViewModel(orders, items))
            ledger = track(PaymentManageViewModel(orders, priceId))
            calendar = track(PaymentCalendarViewModel(PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                PaymentRepository(db.paymentDao(), context, db.itemDao(), db), items, { date("2026-10-23") }))
            budget = track(BudgetViewModel(BudgetRepository(db.budgetDao()), preferences))
        }
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = bottom }
            val view = LocalView.current
            SideEffect { (view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            CompositionLocalProvider(LocalPricingPreferences provides preferences, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else 1f)) {
                LolitaTheme(skin.value, night.value) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { Box(Modifier.width(if (large.value) 320.dp else 400.dp).fillMaxHeight()) {
                    SkinArtBackground()
                    key(page.value, skin.value, night.value, large.value) { when (page.value) {
                        "detail" -> com.lolita.app.ui.screen.item.ItemOrderSummary(detailItem, detailState, {}, {}, onNavigateToRecordPayment = { destination = it to null })
                        "overview" -> PriceManageScreen(itemId, {}, {}, {}, viewModel = overview, onNavigateToPaymentEdit = { id, stage -> destination = id to stage })
                        "history" -> PriceManageScreen(historicalItem, {}, {}, {}, viewModel = history)
                        "payment" -> PaymentEditScreen(priceId, null, {}, { saved++ }, payment)
                        "supplement" -> PaymentEditScreen(historicalPrice, null, {}, { saved++ }, supplement)
                        "price" -> PriceEditScreen(wish, null, {}, { saved++ }, editor)
                        "ledger" -> PaymentManageScreen(priceId, {}, {}, ledger)
                        "calendar" -> PaymentCalendarContent(calendar, onRecordPayment = { id, stage -> destination = id to stage }, onViewUndated = { page.value = "budget" })
                        "budget" -> BudgetContent(budget, onNavigateToOrder = { destination = it to null }, onNavigateToLedger = { destination = it to null })
                    } }
                } } } }
            }
        }
        compose.waitUntil(10_000) { !overview.uiState.value.isLoading && !history.uiState.value.isLoading && !calendar.uiState.value.isLoading && !budget.state.value.loading }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val dir = File(context.filesDir, "pricing-experience-native").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun directPaymentCalendarAndBudgetNavigateWithoutWritingMoney() = runBlocking {
        val before = orders.getOrder(priceId).first()!!
        large.value = InstrumentationRegistry.getArguments().getString("pricingCaptureSize") == "small"
        show()
        compose.onNodeWithTag("priceManageList").performScrollToNode(hasTestTag("recordPaymentDirect"))
        compose.onNodeWithTag("recordPaymentDirect").performClick()
        assertEquals(priceId to before.stages.last().id, destination)
        compose.runOnIdle { page.value = "calendar"; calendar.selectMonth(10) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("记付款"))
        compose.onNodeWithText("记付款").performClick()
        assertEquals(priceId to before.stages.last().id, destination)
        assertEquals(before, orders.getOrder(priceId).first())
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("查看日期待定金额与预算"))
        compose.onNodeWithText("查看日期待定金额与预算").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("雾蓝发带 · 日期待定"))
        compose.onNodeWithText("雾蓝发带 · 日期待定").performClick()
        assertTrue(destination!!.first != itemId)
        compose.runOnIdle { page.value = "detail" }
        compose.onNodeWithTag("itemRecordPayment").performClick()
        assertEquals(priceId to null, destination)
        assertEquals(before, orders.getOrder(priceId).first())
    }
    @Test fun privacyRemovesAmountsAndSemanticsAcrossOverviewLedgerAndCalendarLive() = runBlocking {
        show()
        for (mode in listOf("detail", "overview", "ledger", "calendar")) {
            compose.runOnIdle { page.value = mode }
            preferences.setShowTotalPrice(false)
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("¥", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onAllNodes(hasContentDescription("¥", substring = true), useUnmergedTree = true).assertCountEquals(0)
            capture("privacy-$mode")
            preferences.setShowTotalPrice(true)
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("¥", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }
    @Test fun realImeOverpaymentFeedbackPartialPaymentSaveAndRevoke() = runBlocking {
        page.value = "payment"; large.value = true; show()
        compose.waitUntil(10_000) { !payment.uiState.value.isLoading }
        compose.onNodeWithText("本次实付金额").performScrollTo().performClick().performTextInput("500")
        compose.onNodeWithText("最多可记录¥492.00，当前超出¥8.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存付款记录").assertIsNotEnabled()
        compose.onNodeWithText("本次实付金额").performTextReplacement("100")
        compose.onNodeWithText("保存付款记录").assertIsDisplayed().assertIsEnabled()
        compose.waitUntil(5_000) { imeBottom > 0 }
        capture("payment-large-ime")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "pricing-experience-native/payment-system-ime.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        compose.onNodeWithText("保存付款记录").performClick(); compose.waitUntil(10_000) { saved == 1 }
        val after = orders.getOrder(priceId).first()!!
        assertEquals(300.0, after.paidAmount, 0.0); assertEquals(392.0, after.remainingAmount, 0.0)
        orders.deletePayment(after.payments.maxBy { it.id }.id)
        assertEquals(200.0, orders.getOrder(priceId).first()!!.paidAmount, 0.0)
    }
    @Test fun affectedScreensSevenSkinsLightDarkAndSmallLargeText() {
        show()
        val args = InstrumentationRegistry.getArguments()
        val pages = args.getString("pricingCapturePages")?.split(",") ?: listOf("detail", "overview", "payment", "price", "history", "supplement", "ledger", "calendar", "budget")
        val sizes = when (args.getString("pricingCaptureSize")) { "small" -> listOf(true); "normal" -> listOf(false); else -> listOf(false, true) }
        for (mode in pages) {
            for (big in sizes) for (type in SkinType.entries) for (dark in listOf(false, true)) {
                compose.runOnIdle { page.value = mode; large.value = big; skin.value = type; night.value = dark }
                if (mode == "payment") compose.waitUntil(10_000) { !payment.uiState.value.isLoading }
                if (mode == "supplement") compose.waitUntil(10_000) { !supplement.uiState.value.isLoading }
                if (mode == "price") compose.waitUntil(10_000) { !editor.uiState.value.isLoading }
                capture("$mode-${type.name.lowercase()}-${if (dark) "dark" else "light"}-${if (big) "320-large" else "normal"}")
            }
        }
    }
}
