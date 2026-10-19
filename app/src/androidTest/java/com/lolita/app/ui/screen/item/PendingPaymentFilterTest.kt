package com.lolita.app.ui.screen.item

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.ui.screen.common.SortOption
import com.lolita.app.ui.screen.common.ViewMode
import com.lolita.app.ui.screen.coordinate.CoordinateListViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Real list ViewModel/page with independent Room and settings; never uses the app's stored data. */
class PendingPaymentFilterTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LolitaDatabase
    private lateinit var directory: File
    private lateinit var preferencesScope: CoroutineScope
    private lateinit var preferences: AppPreferences
    private lateinit var store: ViewModelStore
    private lateinit var vm: ItemListViewModel
    private lateinit var items: ItemRepository
    private lateinit var orders: OrderRepository
    private val pending = setOf(1L, 2L, 6L, 7L, 8L)
    private val wardrobeRoots = setOf(1L, 2L, 3L, 4L, 8L)

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        directory = File(context.cacheDir, "pending-filter-${UUID.randomUUID()}").apply { mkdirs() }
        preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = preferencesScope,
            produceFile = { File(directory, "settings.preferences_pb") }))
        preferences.setViewMode(ViewMode.LIST)
        val photo = File(directory, "item.png")
        Bitmap.createBitmap(120, 150, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(199, 158, 181))
            photo.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        database.brandDao().insertBrand(Brand(id = 1, name = "待付款测试品牌"))
        database.brandDao().insertBrand(Brand(id = 2, name = "另一品牌"))
        database.categoryDao().insertCategory(Category(id = 1, name = "JSK", group = CategoryGroup.CLOTHING))
        database.categoryDao().insertCategory(Category(id = 2, name = "头饰", group = CategoryGroup.ACCESSORY))
        val names = listOf("普通待付款", "定金尾款未付", "已拥有", "购买资料待核对", "心愿服饰", "独立待付款小物", "共价待付尾款小物", "另一品牌待付款")
        val statuses = listOf(ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE, ItemStatus.OWNED,
            ItemStatus.PENDING_REVIEW, ItemStatus.WISHED, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE, ItemStatus.PENDING_PAYMENT)
        names.forEachIndexed { index, name -> database.itemDao().insertItem(Item(
            id = index + 1L, name = name, description = "独立回归资料", brandId = if (index == 7) 2 else 1,
            categoryId = if (index in 5..6) 2 else 1, status = statuses[index], imageUrls = listOf(photo.absolutePath),
            parentItemId = when (index) { 5 -> 3; 6 -> 2; else -> null }, includedInParentPrice = index == 6,
            season = if (index == 7) "夏" else "春", style = if (index == 7) "古典" else "甜美", colors = listOf(if (index == 7) "绿" else "粉")
        )) }
        items = ItemRepository(database.itemDao(), database = database)
        orders = OrderRepository(database, context)
        store = ViewModelStore()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val prices = PriceRepository(database.priceDao(), database.paymentDao(), context, database)
            vm = ItemListViewModel(items, BrandRepository(database.brandDao(), database.itemDao(), database),
                CategoryRepository(database.categoryDao(), database.itemDao(), database), prices, preferences,
                LocationRepository(database.locationDao(), database.itemDao()), OutfitLogRepository(database.outfitLogDao(), database))
            store.put("pending-filter", vm)
            val coordinates = CoordinateListViewModel(CoordinateRepository(database.coordinateDao(), database.itemDao(), database), items, prices, preferences)
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == CoordinateListViewModel::class.java)
                    @Suppress("UNCHECKED_CAST") return coordinates as T
                }
            })[CoordinateListViewModel::class.java]
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.items.size == 8 } }
        compose.runOnIdle { vm.filterByStatuses(setOf(ItemStatus.OWNED, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE, ItemStatus.PENDING_REVIEW)) }
    }
    @After fun close() {
        if (::store.isInitialized) compose.runOnIdle { store.clear() }
        if (::preferencesScope.isInitialized) preferencesScope.cancel()
        if (::database.isInitialized) database.close()
        if (::directory.isInitialized) directory.deleteRecursively()
    }
    private fun assertIds(expected: Set<Long>) = runBlocking {
        val state = withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.filteredItems.map { row -> row.id }.toSet() == expected } }
        assertEquals(expected, state.itemCardDataList.map { it.item.id }.toSet())
        if (state.viewMode == ViewMode.GALLERY) assertEquals(expected, state.galleryCardDataList.map { it.item.id }.toSet())
    }
    @Test fun includesBothPendingStatusesAndAccessoriesWithoutChangingStoredRows() = runBlocking {
        val original = database.itemDao().getAllItemsList()
        compose.runOnIdle {
            vm.togglePendingPaymentOnly()
            assertEquals(pending, vm.uiState.value.filteredItems.map { it.id }.toSet())
        }
        assertIds(pending)
        assertEquals(original, database.itemDao().getAllItemsList())
        compose.runOnIdle { vm.togglePendingPaymentOnly() }
        assertIds(wardrobeRoots)
    }
    @Test fun paymentFilterCombinesWithAttributesSearchSortingAndAllViewModes() {
        compose.runOnIdle { vm.togglePendingPaymentOnly(); vm.filterByGroup(CategoryGroup.CLOTHING); vm.filterByBrand(1) }
        assertIds(setOf(1, 2))
        compose.runOnIdle { vm.filterBySeason("春"); vm.filterByStyle("甜美"); vm.filterByColor("粉"); vm.search("普通") }
        assertIds(setOf(1))
        compose.runOnIdle { vm.search("") }
        assertIds(setOf(1, 2))
        for (mode in listOf(ViewMode.LIST, ViewMode.GRID, ViewMode.GALLERY)) {
            compose.runOnIdle { vm.setViewMode(mode); vm.setSortOption(SortOption.DATE_ASC) }
            assertIds(setOf(1, 2))
        }
        compose.runOnIdle { vm.filterByGroup(CategoryGroup.ACCESSORY) }
        assertIds(setOf(6, 7))
        compose.runOnIdle { vm.togglePendingPaymentOnly() }
        assertIds(setOf(6, 7)) // Explicit category/attribute filters continue to include matching attached parts.
    }
    @Test fun payingAndUndoingPaymentsRefreshesBothStatusesAndSharedAccessories() = runBlocking {
        val full = orders.createOrder(Price(itemId = 1, type = PriceType.FULL, totalPrice = 100.0), listOf(PaymentStage(priceId = 0, name = "全款", amount = 100.0)))
        val tail = orders.createOrder(Price(itemId = 2, type = PriceType.DEPOSIT_BALANCE, totalPrice = 100.0, deposit = 20.0, balance = 80.0),
            listOf(PaymentStage(priceId = 0, name = "定金", amount = 20.0), PaymentStage(priceId = 0, name = "尾款", amount = 80.0)))
        compose.runOnIdle { vm.togglePendingPaymentOnly() }
        assertIds(pending)
        val fullPaid = orders.recordPayment(full, 100.0, 1_700_000_000_000)
        assertIds(setOf(2, 6, 7, 8))
        orders.recordPayment(tail, 20.0, 1_700_000_000_000)
        assertIds(setOf(2, 6, 7, 8))
        val tailPaid = orders.recordPayment(tail, 80.0, 1_700_000_000_000)
        assertIds(setOf(6, 8))
        orders.deletePayment(fullPaid)
        assertIds(setOf(1, 6, 8))
        orders.deletePayment(tailPaid)
        assertIds(pending)
    }
    @Test fun actualWardrobeChipSaysPendingPaymentAndTogglesBothKinds() {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        compose.setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            LolitaTheme { Surface { ItemListScreen(onNavigateToDetail = {}, onNavigateToEdit = {}, viewModel = vm) } }
        } }
        compose.onNode(hasText("服饰") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        val chip = compose.onNode(hasText("待付款") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        chip.performScrollTo().assertIsDisplayed().assertIsNotSelected().performClick().assertIsSelected()
        assertIds(pending)
        val output = File(context.filesDir, "pending-payment-filter/chip.png")
        output.parentFile!!.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        chip.performClick().assertIsNotSelected()
        assertIds(wardrobeRoots)
    }
    @Test fun todayOutfitAppearsOnlyOnLocationTab() {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        compose.setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            LolitaTheme { Surface { ItemListScreen(onNavigateToDetail = {}, onNavigateToEdit = {}, viewModel = vm) } }
        } }
        compose.onNodeWithText("今日穿搭").assertIsDisplayed()
        fun capture(tab: String) {
            val file = File(context.filesDir, "daily-price-tasks/home-$tab.png").apply { parentFile!!.mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        capture("location")
        listOf("服饰", "套装", "心愿").forEach { tab ->
            compose.onNode(hasText(tab) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("今日穿搭").assertDoesNotExist()
            capture(tab)
        }
        compose.onNode(hasText("位置") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        compose.onNodeWithText("今日穿搭").assertIsDisplayed()
    }

    @Test fun reviewReminderFollowsPricesAcrossHomeTabsWithoutChangingStoredReviews() {
        val original = runBlocking { database.itemDao().getAllItemsList() }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val skin = mutableStateOf(SkinType.DEFAULT)
        val dark = mutableStateOf(false)
        val routes = mutableListOf<Triple<String, String, String>>()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner,
                LocalConfiguration provides configuration, LocalDensity provides Density(density, 2f)) {
                LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                    Surface { Box(Modifier.size(320.dp, 600.dp)) {
                        ItemListScreen(onNavigateToDetail = {}, onNavigateToEdit = {}, viewModel = vm,
                            onNavigateToFilteredList = { type, value, title -> routes += Triple(type, value, title) })
                    } }
                }
            }
        }
        fun awaitPrices(show: Boolean) {
            runBlocking { withTimeout(10_000) {
                vm.uiState.first { !it.isLoading && it.showTotalPrice == show && it.purchaseReviews.isNotEmpty() }
            } }
            compose.waitForIdle()
        }
        fun capture(name: String) {
            val file = File(context.filesDir, "review-price-visibility/home-$name.png").apply { parentFile!!.mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        awaitPrices(false)
        val reviews = vm.uiState.value.purchaseReviews
        compose.onNodeWithTag("purchaseReviewShortcut").assertDoesNotExist()
        runBlocking { preferences.setShowTotalPrice(true) }
        awaitPrices(true)
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("status_review", "", "待核对购买资料")), routes) }
        capture("on")
        runBlocking { preferences.setShowTotalPrice(false) }
        awaitPrices(false)
        listOf("位置", "服饰", "套装", "心愿").forEach { tab ->
            compose.onNode(hasText(tab) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
            compose.onNodeWithTag("purchaseReviewShortcut").assertDoesNotExist()
        }
        compose.onNode(hasText("位置") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        for (type in SkinType.entries) for (isDark in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = isDark }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("purchaseReviewShortcut").assertDoesNotExist()
            compose.onNodeWithText("今日穿搭").assertIsDisplayed()
            capture("off-${type.name}-${if (isDark) "dark" else "light"}")
        }
        runBlocking { preferences.setShowTotalPrice(true) }
        awaitPrices(true)
        compose.onNodeWithTag("purchaseReviewShortcut").assertIsDisplayed()
            .assertTextContains("${reviews.size} 件服饰与小物 · 查看清单")
        assertEquals(reviews, vm.uiState.value.purchaseReviews)
        runBlocking { assertEquals(original, database.itemDao().getAllItemsList()) }
    }
}
