package com.lolita.app.ui.screen.price

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.tasks.ImageTaskBar
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PriceAutofillIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var vm: PriceEditViewModel
    private val store = ViewModelStore()
    private var itemId = 0L
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val large = mutableStateOf(false)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val brand = db.brandDao().insertBrand(Brand(name = "独立测试品牌"))
        val category = db.categoryDao().insertCategory(Category(name = "OP"))
        itemId = db.itemDao().insertItem(Item(name = "测试裙", brandId = brand, categoryId = category, description = "", status = ItemStatus.WISHED))
        orders = OrderRepository(db, context)
        instrumentation.runOnMainSync {
            vm = PriceEditViewModel(orders, ItemRepository(db.itemDao(), database = db))
            store.put("price-autofill", vm)
        }
    }
    @After fun close() = runBlocking {
        val job = withContext(Dispatchers.Main) { vm.viewModelScope.coroutineContext[Job] }
        instrumentation.runOnMainSync { store.clear() }
        job?.join()
        db.close()
    }
    private fun edit(action: PriceEditViewModel.() -> Unit) = instrumentation.runOnMainSync { vm.action() }
    @Test fun templatesFillBothWaysAndManualOverrideIsNeverRewritten() {
        edit { updateTotalPrice("680"); updateShipping("10") }
        assertEquals("690", vm.uiState.value.stages.single().amount)
        edit { updatePriceType(PriceType.DEPOSIT_BALANCE) }
        assertEquals(listOf("定金", "尾款"), vm.uiState.value.stages.map { it.name })
        edit { updateStage(0, uiState.value.stages[0].copy(amount = "200")) }
        assertEquals("490", vm.uiState.value.stages[1].amount)
        edit { updateStage(1, uiState.value.stages[1].copy(amount = "400")); updateShipping("20") }
        assertEquals("400", vm.uiState.value.stages[1].amount)
        assertFalse(vm.isValid())
        edit { updateTotalPrice("") }
        assertEquals("580", vm.uiState.value.totalPrice)
        assertTrue(vm.isValid())
    }
    @Test fun changingPlanTypeDoesNotDiscardManualIdsDatesOrAmounts() {
        edit { updateStage(0, uiState.value.stages[0].copy(amount = "680", dueDate = 12345)); updatePriceType(PriceType.DEPOSIT_BALANCE) }
        assertEquals(1, vm.uiState.value.stages.size)
        assertEquals(12345L, vm.uiState.value.stages.single().dueDate)
        assertEquals("680", vm.uiState.value.stages.single().amount)
        edit { applyTemplate() }
        assertEquals(2, vm.uiState.value.stages.size)
        assertEquals("680", vm.uiState.value.totalPrice)
    }
    @Test fun persistedOrderEditsPreserveActualPaymentsAndOnlySaveOnExplicitSubmit() = runBlocking {
        val price = orders.createOrder(Price(itemId = itemId, type = PriceType.DEPOSIT_BALANCE, totalPrice = 690.0, shipping = 10.0),
            listOf(PaymentStage(priceId = 0, name = "定金", amount = 200.0), PaymentStage(priceId = 0, name = "尾款", amount = 490.0)))
        orders.recordPayment(price, 200.0, 1_700_000_000_000)
        val before = orders.getOrder(price).first()!!
        edit { loadPrice(price) }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.stages.first().id != 0L } }
        assertNull(vm.uiState.value.calculationHint)
        edit { updateStage(1, uiState.value.stages[1].copy(amount = "")); updateTotalPrice("780") }
        assertEquals("590", vm.uiState.value.stages[1].amount)
        assertEquals(before, orders.getOrder(price).first())
        assertTrue(withContext(Dispatchers.Main) { vm.update(price) }.isSuccess)
        val after = orders.getOrder(price).first()!!
        assertEquals(before.payments, after.payments)
        assertEquals(before.stages.map { it.id }, after.stages.map { it.id })
        assertEquals(790.0, after.price.totalPrice, 0.0)
    }
    @Test fun historicalOrderRemainsReadOnlyAndLoadingDoesNotCalculate() = runBlocking {
        val price = orders.createOrder(Price(itemId = itemId, type = PriceType.FULL, totalPrice = 680.0),
            listOf(PaymentStage(priceId = 0, name = "全款", amount = 680.0)))
        orders.confirmHistoricalPurchase(itemId)
        edit { loadPrice(price); updateTotalPrice("900") }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.stages.first().id != 0L } }
        val loaded = vm.uiState.value
        assertEquals(PurchaseRecordStatus.HISTORICAL_OWNED, loaded.purchaseRecordStatus)
        assertNull(loaded.calculationHint)
        edit { updateTotalPrice("900"); updateShipping("10"); applyTemplate() }
        assertEquals(loaded, vm.uiState.value)
    }
    private fun show() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else 1f)) {
                LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.width(320.dp).fillMaxHeight()) {
                            Box(Modifier.weight(1f)) { PriceEditScreen(itemId, null, {}, {}, vm) }
                            ImageTaskBar(listOf(ImageTask("00000000-0000-0000-0000-000000000001", ImageTaskKind.MANNEQUIN,
                                ImageGenerationProvider.SEEDREAM, "私有名称不进入任务栏", "{}", emptyList(), status = ImageTaskStatus.GENERATING)), {})
                        }
                    }
                }
            }
        }
    }
    private fun capture(name: String) {
        val file = File(context.filesDir, "daily-price-tasks/$name.png").apply { parentFile!!.mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun actualPriceAndTaskBarSevenSkinsLightDarkAndLargeText() {
        edit {
            updateTotalPrice("680"); updateShipping("10"); updatePriceType(PriceType.DEPOSIT_BALANCE)
            updateStage(0, uiState.value.stages[0].copy(amount = "200"))
        }
        show()
        SkinType.entries.forEach { type -> listOf(false, true).forEach { night ->
            compose.runOnIdle { skin.value = type; dark.value = night }
            compose.onNodeWithText("订单价格").performScrollTo()
            compose.onNodeWithTag("globalImageTaskBar").assertIsDisplayed()
            capture("${type.name}-${if (night) "dark" else "light"}-price")
            compose.onNodeWithText("已按含邮费总额补算尾款，可直接修改。").performScrollTo().assertIsDisplayed()
            if (compose.onAllNodes(hasText("490") and hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithText("编辑阶段 2").performScrollTo().performClick()
            }
            compose.onNode(hasText("490") and hasSetTextAction()).performScrollTo().assertIsDisplayed()
            capture("${type.name}-${if (night) "dark" else "light"}-plan")
        } }
        compose.runOnIdle { large.value = true }
        compose.onNodeWithText("多段").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("globalImageTaskBar").assertIsDisplayed()
        capture("large-plan")
        compose.onNodeWithText("商品金额").performScrollTo().performClick()
        compose.onNodeWithText("商品金额").performTextReplacement("700")
        assertEquals("510", vm.uiState.value.stages[1].amount)
        compose.onAllNodesWithText("保存价格与计划").assertCountEquals(1)
        compose.onNodeWithText("保存价格与计划").assertIsDisplayed().assertIsEnabled()
        capture("large-keyboard")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "daily-price-tasks/large-keyboard-system.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun paymentHasOneSaveAboveRealKeyboardAndOnlyExplicitSaveWritesPayment() = runBlocking {
        val price = orders.createOrder(Price(itemId = itemId, type = PriceType.FULL, totalPrice = 680.0),
            listOf(PaymentStage(priceId = 0, name = "全款", amount = 680.0)))
        lateinit var payment: PaymentEditViewModel
        instrumentation.runOnMainSync { payment = PaymentEditViewModel(orders); store.put("payment-editor", payment) }
        var saved = 0
        compose.setContent {
            val host = LocalView.current
            SideEffect { (host.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else 1f)) {
                LolitaTheme(skin.value, dark.value) { Surface(Modifier.width(320.dp).fillMaxHeight()) {
                    PaymentEditScreen(price, null, {}, { saved++ }, payment)
                } }
            }
        }
        compose.waitUntil(10_000) { !payment.uiState.value.isLoading && payment.uiState.value.itemName.isNotBlank() }
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            compose.onAllNodesWithText("保存付款记录").assertCountEquals(1)
            compose.onNodeWithText("保存付款记录").assertIsDisplayed().assertIsNotEnabled()
            capture("payment-${type.name.lowercase()}-${if (night) "dark" else "light"}")
        }
        compose.runOnIdle { large.value = true }
        compose.onNodeWithText("本次实付金额").performScrollTo().performClick().performTextInput("125.50")
        assertTrue(orders.getOrder(price).first()!!.payments.isEmpty())
        compose.onAllNodesWithText("保存付款记录").assertCountEquals(1)
        compose.onNodeWithText("保存付款记录").assertIsDisplayed().assertIsEnabled()
        capture("payment-large-keyboard")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "daily-price-tasks/payment-real-ime.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText("保存付款记录").performClick()
        compose.waitUntil(10_000) { saved == 1 }
        val order = orders.getOrder(price).first()!!
        assertEquals(1, order.payments.size)
        assertEquals(125.50, order.paidAmount, 0.0)
    }
}
