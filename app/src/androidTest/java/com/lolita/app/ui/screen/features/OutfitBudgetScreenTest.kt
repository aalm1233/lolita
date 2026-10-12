package com.lolita.app.ui.screen.features

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.ui.screen.budget.*
import com.lolita.app.ui.screen.look.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OutfitBudgetScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private val store = ViewModelStore()
    private lateinit var budget: BudgetViewModel
    private lateinit var look: LookBuilderViewModel
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private var savedId: Long? = null
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        directory = File(context.cacheDir, "feature-ui-${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(directory, "settings.preferences_pb") }))
        preferences.setShowTotalPrice(true)
        db.brandDao().insertBrand(Brand(id = 1, name = "品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        db.categoryDao().insertCategory(Category(id = 2, name = "衬衫"))
        db.categoryDao().insertCategory(Category(id = 3, name = "KC"))
        (1L..3L).forEach { id -> db.itemDao().insertItem(Item(id = id, brandId = 1, categoryId = id,
            name = listOf("茶会主裙", "蕾丝衬衫", "蝴蝶结头饰")[id.toInt()-1], description = "", status = ItemStatus.OWNED, style = "古典")) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            budget = BudgetViewModel(BudgetRepository(db.budgetDao()), preferences)
            look = LookBuilderViewModel(RecommendationRepository(db.itemDao(), db.outfitLogDao(), db.coordinateDao()), CategoryRepository(db.categoryDao(), db.itemDao(), db), LookRepository(db))
            store.put("budget", budget); store.put("look", look)
        }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }; scope.cancel(); db.close(); directory.deleteRecursively()
    }
    @Test fun budgetSetsEditsRemovesAndHonorsLivePrivacy() {
        compose.setContent { FeatureTheme() { BudgetContent(budget) } }
        compose.waitUntil(10_000) { !budget.state.value.loading }
        compose.onNodeWithText("设置").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("123.45")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { budget.state.value.limitCents == 12345L }
        compose.onNodeWithText("¥123.45").assertIsDisplayed()
        capture("budget-default-light")
        runBlocking { preferences.setShowTotalPrice(false) }
        compose.waitUntil(10_000) { !budget.state.value.showMoney }
        compose.onNodeWithText("金额已隐藏").assertIsDisplayed()
        compose.onNodeWithText("¥123.45").assertDoesNotExist()
        runBlocking { preferences.setShowTotalPrice(true) }
        compose.waitUntil(10_000) { budget.state.value.showMoney }
        compose.onNodeWithText("修改").performClick()
        compose.onNodeWithText("取消本月预算").performClick()
        compose.waitUntil(10_000) { budget.state.value.limitCents == null && !budget.state.value.saving }
        compose.onNodeWithText("尚未设置预算").assertIsDisplayed()
        assertTrue(runBlocking { db.budgetDao().getAll().isEmpty() })
    }
    @Test fun lookCanSkipRestoreLockAndSaveAfterMoveConfirmation() {
        val old = runBlocking {
            val id = db.coordinateDao().insertCoordinate(Coordinate(name = "原套装", description = ""))
            db.itemDao().updateItem(db.itemDao().getItemById(2)!!.copy(coordinateId = id)); id
        }
        compose.setContent { FeatureTheme { LookBuilderScreen(1, {}, { savedId = it }, {}, look) } }
        compose.waitUntil(10_000) { !look.state.value.loading }
        compose.onNodeWithText("蕾丝衬衫").assertIsDisplayed()
        compose.onAllNodesWithText("锁定这件")[0].performScrollTo()
        compose.onAllNodes(isToggleable())[0].performClick()
        assertTrue(look.state.value.selection.locked.isNotEmpty())
        compose.onNodeWithText("换一套").performClick()
        compose.onNodeWithText("保存为套装").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("茶会搭配")
        compose.onNodeWithText("以下服饰已属于其他套装，保存会将它们移入新套装：蕾丝衬衫。原套装会保留。").assertIsDisplayed()
        compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(10_000) { savedId != null }
        val saved = runBlocking { db.coordinateDao().getCoordinateWithItemsList(savedId!!)!! }
        assertEquals("茶会搭配", saved.coordinate.name)
        assertEquals(3, saved.items.size)
        assertNotNull(runBlocking { db.coordinateDao().getCoordinateById(old) })
    }
    @Test fun chosenLookSurvivesDetailRoundTripAndCanRefresh() {
        val builderVisible = mutableStateOf(true)
        compose.setContent { FeatureTheme {
            if (builderVisible.value) LookBuilderScreen(1, {}, {}, {}, look)
            else androidx.compose.material3.Text("服饰详情")
        } }
        compose.waitUntil(10_000) { !look.state.value.loading }
        compose.runOnIdle { look.choose("上衣", null); look.lock("头饰"); builderVisible.value = false }
        compose.onNodeWithText("服饰详情").assertIsDisplayed()
        compose.runOnIdle { builderVisible.value = true }
        compose.waitForIdle()
        assertNull(look.state.value.selection.selected["上衣"])
        assertTrue("头饰" in look.state.value.selection.locked)
        compose.runOnIdle { look.load(1, force = true) }
        compose.waitUntil(10_000) { !look.state.value.loading }
        assertEquals(2L, look.state.value.selection.selected["上衣"])
    }
    @Test fun transientBudgetQueryFailureCanRetryAndObserveAgain() {
        val dao = db.budgetDao()
        var failOnce = true
        val fault = object : com.lolita.app.data.local.dao.BudgetDao by dao {
            override fun actualPayments(): kotlinx.coroutines.flow.Flow<List<com.lolita.app.domain.usecase.BudgetEntry>> = kotlinx.coroutines.flow.flow {
                if (failOnce) { failOnce = false; throw IllegalStateException("测试读取失败") }
                dao.actualPayments().collect { emit(it) }
            }
        }
        lateinit var recovering: BudgetViewModel
        compose.runOnIdle {
            recovering = BudgetViewModel(BudgetRepository(fault), preferences)
            store.put("recovering", recovering)
        }
        compose.setContent { FeatureTheme { BudgetContent(recovering) } }
        compose.waitUntil(10_000) { recovering.state.value.error != null }
        compose.onNodeWithText("重新加载预算").performClick()
        compose.waitUntil(10_000) { recovering.state.value.summary != null && !recovering.state.value.loading }
        runBlocking { BudgetRepository(dao).save(YearMonth.now(), "25.00") }
        compose.waitUntil(10_000) { recovering.state.value.limitCents == 2500L }
        compose.onNodeWithText("¥25.00").assertIsDisplayed()
    }
    @Test fun sharePreviewWaitsForGenerationAndRegeneratesWhenBrandsAreHidden() {
        val main = runBlocking { db.itemDao().getItemById(1)!! }
        compose.setContent { FeatureTheme {
            com.lolita.app.ui.screen.share.ShareCardDialog("茶会搭配", emptyList(), listOf(main), brandNames = mapOf(1L to "测试品牌"), onDismiss = {})
        } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("分享图片").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) &&
                    it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }
        }
        compose.onNodeWithText("分享图片").assertIsEnabled()
        compose.onAllNodesWithContentDescription("分享图片预览")[0].assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(10_000) {
            compose.onNodeWithText("分享图片").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
        }
        compose.onNodeWithText("分享图片").assertIsEnabled()
        capture("share-dialog-default-light")
    }
    @Test fun budgetAndLookUseSevenSkinsInLightAndDark() {
        runBlocking { BudgetRepository(db.budgetDao()).save(YearMonth.now(), "1000.00") }
        val page = mutableStateOf(false)
        compose.setContent { FeatureTheme() {
            if (page.value) LookBuilderScreen(1, {}, {}, {}, look) else BudgetContent(budget)
        } }
        compose.waitUntil(10_000) { !budget.state.value.loading }
        for (type in SkinType.entries) for (isDark in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = isDark }
            compose.waitForIdle()
            compose.onNodeWithText("月度预算").assertIsDisplayed()
            capture("budget-${type.name.lowercase()}-${if (isDark) "dark" else "light"}")
        }
        compose.runOnIdle { page.value = true }
        compose.waitUntil(10_000) { !look.state.value.loading }
        for (type in SkinType.entries) for (isDark in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = isDark }
            compose.waitForIdle()
            compose.onNodeWithText("整套搭配").assertIsDisplayed()
            capture("look-${type.name.lowercase()}-${if (isDark) "dark" else "light"}")
        }
    }
    @Composable
    private fun FeatureTheme(content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            LolitaTheme(skinType = skin.value, darkTheme = dark.value) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
    private fun capture(name: String) {
        val folder = File(context.filesDir, "feature-previews").apply { mkdirs() }
        (if (name.startsWith("share-dialog")) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
