package com.lolita.app.ui.screen.stats

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OrderBinding
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.PurchaseRecordStatus
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.CoordinateRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real overview, its Room flows and privacy setting without the application's DB. */
@RunWith(AndroidJUnit4::class)
class StatsOverviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var preferencesScope: CoroutineScope
    private lateinit var preferencesDirectory: File
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: StatsViewModel
    private val presentation = mutableStateOf(Presentation())
    private val routes = mutableListOf<Route>()
    private val itemDestinations = mutableListOf<Long>()
    private val longBrand = "长品牌 / Rose & Ribbon? Classic Lolita 蕾丝花园与古典洋装收藏系列"

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        preferencesDirectory = File(context.cacheDir, "stats-overview-${UUID.randomUUID()}").apply { mkdirs() }
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferencesScope,
            produceFile = { File(preferencesDirectory, "settings.preferences_pb") }))
        preferences.setShowTotalPrice(true)
        database.brandDao().insertBrand(Brand(id = 101, name = longBrand))
        database.categoryDao().insertCategory(Category(id = 301, name = "主裙"))
        viewModelStore = ViewModelStore()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = StatsViewModel(
                itemRepository = ItemRepository(database.itemDao(), database = database),
                coordinateRepository = CoordinateRepository(database.coordinateDao(), database.itemDao(), database),
                outfitLogRepository = OutfitLogRepository(database.outfitLogDao(), database),
                priceRepository = PriceRepository(database.priceDao(), database.paymentDao(), context, database),
                appPreferences = preferences
            )
            viewModelStore.put("overview", viewModel)
        }
        Unit
    }

    @After fun tearDown() {
        if (::viewModelStore.isInitialized) compose.runOnIdle { viewModelStore.clear() }
        if (::preferencesScope.isInitialized) preferencesScope.cancel()
        if (::database.isInitialized) database.close()
        if (::preferencesDirectory.isInitialized) preferencesDirectory.deleteRecursively()
    }

    @Test fun currentSpendingIsFullyVisibleOnTheFirstCompactScreen() {
        runBlocking {
            paidItem("已付款主裙", 1_234_567.89)
            item("愿望裙", ItemStatus.WISHED)
        }
        showOverview()
        awaitState { it.totalSpending == 1_234_567.89 && it.purchaseReviews.isEmpty() }
        settleAnimation()
        val amount = firstAmount("¥1,234,567.89")
        amount.assertIsDisplayed()
        val viewport = compose.onNodeWithTag("statsTestViewport").getUnclippedBoundsInRoot()
        val bounds = amount.getUnclippedBoundsInRoot()
        assertTrue("360dp 内容宽度下，当前实付应在首屏前 300dp 内出现", bounds.top <= viewport.top + 300.dp)
        assertTrue("首屏应完整展示当前实付金额，无需越过多排大卡片滚动", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        assertNoTextOverflow(amount)
        capture("compact-default-light")
    }

    @Test fun reviewEntryDisappearsAtZeroAndReappearsFromTheSameRoomFlow() {
        val reviewId = runBlocking {
            paidItem("账本完整主裙", 12.34)
            item("购买资料待核对", ItemStatus.PENDING_REVIEW)
        }
        showOverview()
        awaitState { it.purchaseReviews.size == 1 }
        compose.onNodeWithTag("purchaseReviewShortcut").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(listOf(Route("status_review", "", "待核对购买资料")), routes) }

        runBlocking {
            val review = database.itemDao().getItemById(reviewId)!!
            database.itemDao().updateItem(review.copy(status = ItemStatus.WISHED))
        }
        awaitState { it.purchaseReviews.isEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.TestTag, "purchaseReviewShortcut")).fetchSemanticsNodes().isEmpty() }

        runBlocking {
            val review = database.itemDao().getItemById(reviewId)!!
            database.itemDao().updateItem(review.copy(status = ItemStatus.OWNED))
        }
        awaitState { it.purchaseReviews.size == 1 }
        compose.waitUntil(10_000) { compose.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.TestTag, "purchaseReviewShortcut")).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("purchaseReviewShortcut").performClick()
        compose.runOnIdle { assertEquals(2, routes.size) }
        runBlocking { assertEquals(1, database.priceDao().getAllPricesList().size) }
    }

    @Test fun hidingAmountsRemovesEveryMoneyNodeIncludingTheHighestSpendingCard() {
        runBlocking {
            val itemId = paidItem("金额隐私主裙", 1_234.56)
            archivedActual(itemId, 2_222.22)
        }
        showOverview()
        awaitState { it.totalSpending == 1_234.56 && it.allRecordedSpending == 3_456.78 && it.showTotalPrice }
        settleAnimation()
        assertTrue(moneyNodes().isNotEmpty())
        compose.onNodeWithText("当前订单实付最高").performScrollTo().assertIsDisplayed()

        runBlocking { preferences.setShowTotalPrice(false) }
        awaitState { !it.showTotalPrice }
        compose.waitUntil(10_000) { moneyNodes().isEmpty() }
        assertTrue("整棵未合并语义树也不得泄漏金额", moneyNodes().isEmpty())
        assertFalse(compose.onAllNodesWithText("当前订单实付最高", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("已拥有").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(longBrand).performScrollTo().assertIsDisplayed()
    }

    @Test fun reviewReminderFollowsPricesWithoutClearingThePurchaseReviewQueue() {
        runBlocking {
            paidItem("已录金额主裙", 123.45)
            item("待补资料主裙", ItemStatus.PENDING_REVIEW)
            preferences.setShowTotalPrice(false)
        }
        val original = runBlocking { database.itemDao().getAllItemsList() }
        presentation.value = Presentation(width = 320, fontScale = 2f)
        showOverview()
        awaitState { !it.showTotalPrice && it.purchaseReviews.size == 1 }
        val reviews = viewModel.uiState.value.purchaseReviews
        compose.onNodeWithTag("purchaseReviewShortcut").assertDoesNotExist()
        runBlocking { preferences.setShowTotalPrice(true) }
        awaitState { it.showTotalPrice }
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(Route("status_review", "", "待核对购买资料")), routes) }
        capture("review-prices-on")
        runBlocking { preferences.setShowTotalPrice(false) }
        awaitState { !it.showTotalPrice }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(width = 320, fontScale = 2f, skin = skin, dark = dark) }
            settleAnimation()
            compose.onNodeWithTag("purchaseReviewShortcut").assertDoesNotExist()
            compose.onNodeWithText("衣橱概况").assertIsDisplayed()
            assertTrue(moneyNodes().isEmpty())
            capture("review-prices-off-${skin.name}-${if (dark) "dark" else "light"}")
        }
        runBlocking { preferences.setShowTotalPrice(true) }
        awaitState { it.showTotalPrice }
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed()
            .assertTextContains("1 件服饰与小物 · 查看清单")
        assertEquals(reviews, viewModel.uiState.value.purchaseReviews)
        runBlocking { assertEquals(original, database.itemDao().getAllItemsList()) }
    }

    @Test fun compactCountsBrandAndHighestSpendingKeepTheirOriginalNavigationArguments() {
        val highestId = runBlocking {
            val id = paidItem("导航目标主裙", 123.45)
            item("愿望裙", ItemStatus.WISHED)
            id
        }
        showOverview()
        awaitState { it.ownedCount == 1 && it.wishedCount == 1 && it.mostExpensiveItem?.itemId == highestId }
        settleAnimation()
        compose.onNodeWithText("已拥有").performScrollTo().performClick()
        compose.onNodeWithText("心愿").performScrollTo().performClick()
        compose.onNodeWithText(longBrand).performScrollTo().performClick()
        compose.onNodeWithText("当前订单实付最高").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Route("status_owned", "", "已拥有"), Route("status_wished", "", "心愿"),
                Route("brand", longBrand, longBrand)), routes)
            assertEquals(listOf(highestId), itemDestinations)
        }
    }

    @Test fun largeAmountsAndLongBrandsRemainCompleteAcrossSevenLightAndDarkSkins() {
        val itemId = runBlocking { paidItem("长金额排版主裙", 1_234_567.89) }
        presentation.value = Presentation(width = 320, fontScale = 2f)
        showOverview()
        for ((amount, text, caseName) in listOf(
            Triple(1_234_567.89, "¥1,234,567.89", "million"),
            Triple(999_999_999.99, "¥999,999,999.99", "billion")
        )) {
            runBlocking {
                val price = database.priceDao().getPricesByItemList(itemId).single()
                database.priceDao().updatePrice(price.copy(totalPrice = amount))
                val payment = database.paymentDao().getPaymentsByPriceList(price.id).single()
                database.paymentDao().updatePayment(payment.copy(amount = amount))
                val stage = database.orderDao().getStages(price.id).single()
                database.orderDao().updateStage(stage.copy(amount = amount))
            }
            awaitState { it.totalSpending == amount && it.averagePrice == amount && it.allRecordedSpending == amount }
            for (skin in SkinType.entries) for (dark in listOf(false, true)) {
                compose.runOnIdle { presentation.value = Presentation(320, 2f, skin, dark) }
                settleAnimation()
                val mainAmount = firstAmount(text).performScrollTo().assertIsDisplayed()
                capture("${caseName}-${skin.name.lowercase()}-${if (dark) "dark" else "light"}-amount")
                assertNoTextOverflow(mainAmount)
                val brand = compose.onNodeWithText(longBrand, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                capture("${caseName}-${skin.name.lowercase()}-${if (dark) "dark" else "light"}-brand")
                assertNoTextOverflow(brand)
            }
        }
        for (skin in SkinType.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { presentation.value = Presentation(360, 1f, skin, dark) }
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, -100_000f)
            }
            settleAnimation()
            capture("normal-${skin.name.lowercase()}-${if (dark) "dark" else "light"}")
        }
    }

    private fun showOverview() {
        compose.setContent {
            val state = presentation.value
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (state.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                screenWidthDp = state.width
            }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density, state.fontScale)) {
                LolitaTheme(skinType = state.skin, darkTheme = state.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(state.width.dp, 600.dp).testTag("statsTestViewport")) {
                            StatsContent(viewModel = viewModel,
                                onNavigateToFilteredList = { type, value, title -> routes += Route(type, value, title) },
                                onNavigateToItemDetail = { itemDestinations += it })
                        }
                    }
                }
            }
        }
    }

    private suspend fun item(name: String, status: ItemStatus): Long = database.itemDao().insertItem(
        Item(brandId = 101, categoryId = 301, name = name, description = "独立统计回归资料", status = status))

    private suspend fun paidItem(name: String, amount: Double): Long {
        val itemId = item(name, ItemStatus.OWNED)
        val priceId = database.priceDao().insertPrice(Price(itemId = itemId, type = PriceType.FULL,
            totalPrice = amount, purchaseRecordStatus = PurchaseRecordStatus.TRACKED))
        database.orderDao().insertBinding(OrderBinding(itemId, priceId, PurchaseRecordStatus.TRACKED))
        database.orderDao().insertStage(PaymentStage(priceId = priceId, name = "全款", amount = amount))
        database.paymentDao().insertPayment(Payment(priceId = priceId, amount = amount, isPaid = true,
            dueDate = 1_700_000_000_000, paidDate = 1_700_000_000_000))
        return itemId
    }

    private suspend fun archivedActual(itemId: Long, amount: Double) {
        val priceId = database.priceDao().insertPrice(Price(itemId = itemId, type = PriceType.FULL,
            totalPrice = amount, purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED))
        database.paymentDao().insertPayment(Payment(priceId = priceId, amount = amount, isPaid = true,
            dueDate = 1_690_000_000_000, paidDate = 1_690_000_000_000))
    }

    private fun awaitState(predicate: (StatsUiState) -> Boolean) {
        runBlocking { withTimeout(10_000) { viewModel.uiState.first { !it.isLoading && predicate(it) } } }
        compose.waitForIdle()
    }

    private fun settleAnimation() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun firstAmount(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)[0]

    private fun moneyNodes() = compose.onAllNodes(SemanticsMatcher("金额或货币无障碍内容") { node ->
        val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        values.any { it.contains('¥') || it.contains('￥') || Regex("\\d[\\d,]*\\.\\d{2}").containsMatchIn(it) }
    }, useUnmergedTree = true).fetchSemanticsNodes()

    private fun assertNoTextOverflow(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("必须检查实际文本布局，而非仅检查完整的语义文本", layouts.isNotEmpty())
        assertTrue("金额或品牌不得裁切、省略或溢出", layouts.none { it.hasVisualOverflow })
    }

    private fun capture(name: String) {
        val directory = File(context.filesDir, "stats-overview-previews").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private data class Presentation(val width: Int = 360, val fontScale: Float = 1f,
        val skin: SkinType = SkinType.DEFAULT, val dark: Boolean = false)
    private data class Route(val type: String, val value: String, val title: String)
}
