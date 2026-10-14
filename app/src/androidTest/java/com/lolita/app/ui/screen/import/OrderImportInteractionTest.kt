package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.runtime.SideEffect
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.model.TaobaoOrderItem
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.repository.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalLayoutApi::class)
class OrderImportInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LolitaDatabase
    private lateinit var vm: TaobaoImportViewModel
    private val store = ViewModelStore()
    private val compact = mutableStateOf(false)
    private val skinType = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private var keyboardController: SoftwareKeyboardController? = null
    @Volatile private var keyboardOpen = false

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        runBlocking {
            db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        }
        compose.runOnIdle {
            vm = TaobaoImportViewModel(context.applicationContext as Application,
                BrandRepository(db.brandDao(), db.itemDao(), db),
                CategoryRepository(db.categoryDao(), db.itemDao(), db),
                ItemRepository(db.itemDao(), database = db),
                PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                OrderRepository(db, context), db)
            store.put("import", vm)
            val rows = listOf("测试定金裙", "测试尾款裙", "测试全款裙").mapIndexed { i, name ->
                val original = TaobaoOrderItem(name, "", "蓝色;M;JSK", 1, 100.0, "JSK", "M", "蓝色", "order-$i", "2026-10-14", "测试店铺")
                ImportItemState(originalItem = original, name = name, brandId = 1, categoryId = 1,
                    paymentRole = when (i) { 0 -> PaymentRole.DEPOSIT; 1 -> PaymentRole.BALANCE; else -> null })
            }
            val field = TaobaoImportViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(vm) as MutableStateFlow<TaobaoImportUiState>
            state.value = state.value.copy(currentStep = ImportStep.DETAIL, importItems = rows)
        }
        compose.setContent {
            val state by vm.uiState.collectAsState()
            val density = LocalDensity.current
            val keyboard = LocalSoftwareKeyboardController.current
            val open = WindowInsets.isImeVisible
            SideEffect { keyboardController = keyboard; keyboardOpen = open }
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, if (compact.value) 2f else density.fontScale)) {
                Box(if (compact.value) Modifier.width(320.dp) else Modifier) {
                    LolitaTheme(skinType = skinType.value, darkTheme = dark.value) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SkinArtBackground(Modifier.matchParentSize())
                            ImportDetailContent(state, vm, onBack = {})
                        }
                    }
                }
            }
        }
    }
    @After fun close() { compose.runOnIdle { store.clear() }; db.close() }
    private fun reveal(text: String) {
        compose.onNodeWithTag("importDetailList").performScrollToNode(hasText(text))
        // Locate a long lazy item, then scroll its actual descendant into the viewport.
        compose.onNodeWithText(text).performScrollTo()
    }
    @Suppress("UNCHECKED_CAST")
    private fun replaceState(update: (TaobaoImportUiState) -> TaobaoImportUiState) {
        compose.runOnIdle {
            val field = TaobaoImportViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
            val state = field.get(vm) as MutableStateFlow<TaobaoImportUiState>
            state.value = update(state.value)
        }
    }
    @Test fun keyboardKeepsInputAndFloatingActionsReachableAndHidesDeck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }
        val original = shell("settings get secure show_ime_with_hard_keyboard")
        require(original == "null" || original.matches(Regex("-?\\d+")))
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            reveal("本件分摊邮费")
            compose.onNodeWithText("本件分摊邮费").performClick()
            compose.runOnIdle { keyboardController?.show() }
            compose.waitUntil(10_000) { keyboardOpen }
            compose.onNodeWithText("本件分摊邮费").assertIsDisplayed().performTextReplacement("12..")
            compose.onNodeWithTag("importStackBack").assertDoesNotExist()
            compose.onNodeWithText("下一件").assertIsDisplayed()
            compose.onNodeWithText("核对并导入").assertIsDisplayed()
            val output = File(instrumentation.targetContext.filesDir, "order-import-floating/keyboard.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.onNodeWithText("下一件").performClick()
            compose.waitUntil(10_000) { !keyboardOpen }
            compose.onNodeWithText("2 / 3").assertExists()
            compose.runOnIdle { assertEquals("12..", vm.uiState.value.importItems[0].shippingInput) }
        } finally {
            compose.runOnIdle { keyboardController?.hide() }
            if (original == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $original")
        }
    }
    @Test fun singleStackTracksRemainingRowsAndDisappearsInBatchAndAtLastRow() {
        compose.onNodeWithTag("importFloatingDock").assertExists()
        compose.onNodeWithTag("importStackBack").assertExists()
        compose.onNodeWithText("下一件：#2 测试尾款裙 · 尾款").assertExists()
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithTag("importStackBack").assertExists()
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithTag("importStackBack").assertDoesNotExist()
        compose.onNodeWithText("上一件").performClick()
        reveal("批量编辑")
        compose.onNodeWithText("批量编辑").performClick()
        compose.onNodeWithTag("importFloatingDock").assertDoesNotExist()
        compose.onNodeWithTag("importStackBack").assertDoesNotExist()
    }
    @Test fun missingIdentityExpandsItsFieldsAndKeepsDrafts() {
        replaceState { state -> state.copy(importItems = state.importItems.map { it.copy(brandId = 0, categoryId = 0) }) }
        reveal("商品名称")
        compose.onNodeWithText("商品名称").performTextReplacement("待补充品牌的服饰")
        compose.runOnIdle { assertEquals("待补充品牌的服饰", vm.uiState.value.importItems[0].name) }
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithText("上一件").performClick()
        reveal("商品名称")
        compose.onNodeWithText("商品名称").assertTextContains("待补充品牌的服饰")
    }
    @Test fun identitySectionCanFoldWithoutLosingChanges() {
        replaceState { state -> state.copy(importItems = state.importItems.map { it.copy(brandId = 1, categoryId = 1) }) }
        reveal("展开资料")
        compose.onNodeWithText("商品名称").assertDoesNotExist()
        compose.onNodeWithText("展开资料").assertIsDisplayed().performClick()
        reveal("商品名称")
        compose.onNodeWithText("商品名称").performTextReplacement("修正后的服饰")
        reveal("收起资料")
        compose.onNodeWithText("收起资料").performClick()
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithText("上一件").performClick()
        reveal("展开资料")
        compose.onNodeWithText("展开资料").assertIsDisplayed().performClick()
        reveal("商品名称")
        compose.onNodeWithText("商品名称").assertTextContains("修正后的服饰")
    }
    @Test fun allSkinsRenderSingleStackAndDockInLightAndDark() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "order-import-floating").apply { mkdirs() }
        replaceState { state -> state.copy(importItems = state.importItems.map { it.copy(brandId = 1, categoryId = 1) }) }
        SkinType.entries.forEach { skin ->
            listOf(false, true).forEach { isDark ->
                compose.runOnIdle { skinType.value = skin; dark.value = isDark }
                compose.onNodeWithTag("importStackBack").assertExists()
                compose.onNodeWithTag("importFloatingDock").assertIsDisplayed()
                compose.onNodeWithText("下一件").assertIsDisplayed()
                if (isDark) {
                    val counter = compose.onNodeWithText("第 1 / 3 条资料").captureToImage().asAndroidBitmap()
                    val brightest = (0 until counter.height).maxOf { y ->
                        (0 until counter.width).maxOf { x -> android.graphics.Color.luminance(counter.getPixel(x, y)) }
                    }
                    assertTrue("$skin 暗色进度文字应清晰可读", brightest > 0.4f)
                }
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(folder, "${skin.name}-${if (isDark) "dark" else "light"}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }
    @Test fun incompleteMoneyTextSurvivesNextPreviousAndModeSwitch() {
        reveal("本件分摊邮费")
        compose.onNodeWithText("本件分摊邮费").performTextReplacement("12..")
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithText("上一件").performClick()
        reveal("本件分摊邮费")
        compose.onNodeWithText("本件分摊邮费").assertTextContains("12..")
        reveal("批量编辑")
        compose.onNodeWithText("批量编辑").performClick()
        compose.onNodeWithText("逐件填写").performClick()
        reveal("本件分摊邮费")
        compose.onNodeWithText("本件分摊邮费").assertTextContains("12..")
    }
    @Test fun singleCardSummaryIncludesAllChosenRowsOfItsSourceOrder() {
        replaceState { state ->
            val rows = state.importItems.map { it.copy(originalItem = it.originalItem.copy(orderId = "same-order"), shipping = 1.0, paidAmount = 50.0) }
            state.copy(importItems = rows, orders = listOf(TaobaoOrder("same-order", "2026-10-14", "交易成功", "测试店铺", 150.0, 3.0, rows.map { it.originalItem })))
        }
        reveal("本次选择 3/3 件，分摊邮费 ¥3.0，待核对实付 ¥150.0。")
        compose.onNodeWithText("本次选择 3/3 件，分摊邮费 ¥3.0，待核对实付 ¥150.0。").assertExists()
    }
    @Test fun unlinkAndChangingCounterpartRequireFreshFinancialConfirmation() {
        replaceState { state -> state.copy(importItems = state.importItems.mapIndexed { i, row ->
            row.copy(pairedWith = when(i) { 0 -> 1; 1 -> 0; else -> null }, financialConfirmed = true)
        }) }
        compose.runOnIdle {
            vm.unpair(0)
            assertFalse(vm.uiState.value.importItems[0].financialConfirmed)
            assertFalse(vm.uiState.value.importItems[1].financialConfirmed)
        }
        replaceState { state -> state.copy(importItems = state.importItems.mapIndexed { i, row ->
            row.copy(pairedWith = when(i) { 0 -> 1; 1 -> 0; else -> null }, financialConfirmed = true)
        }) }
        compose.runOnIdle {
            vm.setPaymentRole(1, null)
            assertFalse(vm.uiState.value.importItems[0].financialConfirmed)
            assertFalse(vm.uiState.value.importItems[1].financialConfirmed)
        }
    }
    @Test fun footerIsReadableOnCompactScreenWithLargeFontAndLargeBatch() {
        replaceState { state -> state.copy(importItems = List(100) { index ->
            state.importItems[2].copy(name = "测试裙$index", brandId = 1, categoryId = 1, price = 100.0, financialConfirmed = true)
        }) }
        compose.runOnIdle { compact.value = true }
        val progress = compose.onNodeWithText("100/100 条资料已完善").getUnclippedBoundsInRoot()
        val action = compose.onNodeWithText("核对并导入").getUnclippedBoundsInRoot()
        assertTrue("进度和按钮文字不得重叠: $progress / $action", progress.bottom <= action.top || progress.right <= action.left)
        assertTrue("导入按钮应保持单行文字及正常内边距: $action", action.bottom - action.top <= 64.dp)
        val next = compose.onNodeWithText("下一件").getUnclippedBoundsInRoot()
        assertTrue("下一件按钮不得挤成多行: $next", next.bottom - next.top <= 64.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "order-import-review/compact-large-font.png")
        output.parentFile!!.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun correctionAndReplacementInvalidateAllAffectedPeersButNoOpKeepsConfirmation() {
        replaceState { state -> state.copy(importItems = state.importItems.mapIndexed { i, row ->
            row.copy(paymentRole = if (i == 0) PaymentRole.DEPOSIT else PaymentRole.BALANCE,
                pairedWith = when(i) { 0 -> 1; 1 -> 0; else -> null }, financialConfirmed = true)
        }) }
        compose.runOnIdle {
            vm.setPaymentRole(0, PaymentRole.DEPOSIT)
            vm.manualPair(0, 1)
            assertTrue(vm.uiState.value.importItems[0].financialConfirmed)
            assertTrue(vm.uiState.value.importItems[1].financialConfirmed)
            vm.manualPair(0, 2)
            assertNull(vm.uiState.value.importItems[1].pairedWith)
            assertTrue(vm.uiState.value.importItems.none { it.financialConfirmed })
            val original = vm.uiState.value.importItems
            vm.manualPair(1, 2)
            assertEquals(original, vm.uiState.value.importItems)
        }
    }
    @Test fun defaultsToSingleAndRetainsEditsAcrossNavigationAndModes() {
        compose.onNodeWithText("逐件填写").assertIsSelected()
        compose.onNodeWithText("上一件").assertIsNotEnabled()
        reveal("全款")
        compose.onNodeWithText("全款", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertNull(vm.uiState.value.importItems[0].paymentRole) }
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("下一件").performClick()
        compose.onNodeWithText("下一件").assertIsNotEnabled()
        reveal("批量编辑")
        compose.onNodeWithText("批量编辑").performClick()
        compose.onNodeWithText("全款 2").assertExists()
        compose.onNodeWithText("逐件填写").performClick()
        compose.onNodeWithText("3 / 3").assertExists()
        compose.runOnIdle { assertNull(vm.uiState.value.importItems[0].paymentRole) }
    }
    @Test fun pairingShowsCounterpartAndCanBeClearedFromTheCard() {
        reveal("选择尾款配对")
        compose.onNodeWithText("选择尾款配对").performClick()
        compose.onNodeWithText("#2 测试尾款裙").performClick()
        reveal("已配对：#2 测试尾款裙")
        compose.onNodeWithText("已配对：#2 测试尾款裙").assertExists()
        compose.onNodeWithText("解除配对").performClick()
        compose.runOnIdle {
            assertNull(vm.uiState.value.importItems[0].pairedWith)
            assertNull(vm.uiState.value.importItems[1].pairedWith)
        }
        compose.onNodeWithText("选择尾款配对").assertExists()
    }
    @Test fun filteredCorrectionUsesOriginalIndexAndPairingIsReciprocal() {
        compose.onNodeWithText("批量编辑").performClick()
        compose.onNodeWithText("尾款 1").performClick()
        reveal("全款")
        compose.onNodeWithText("全款", useUnmergedTree = true).performClick()
        compose.onNodeWithText("此分类暂无商品，可切换其他分类。").assertExists()
        compose.runOnIdle {
            assertEquals(PaymentRole.DEPOSIT, vm.uiState.value.importItems[0].paymentRole)
            assertNull(vm.uiState.value.importItems[1].paymentRole)
            vm.setPaymentRole(1, PaymentRole.BALANCE)
            vm.manualPair(0, 1)
            assertEquals(1, vm.uiState.value.importItems[0].pairedWith)
            assertEquals(0, vm.uiState.value.importItems[1].pairedWith)
            vm.setPaymentRole(1, null)
            assertNull(vm.uiState.value.importItems[0].pairedWith)
            assertNull(vm.uiState.value.importItems[1].pairedWith)
        }
    }
}
