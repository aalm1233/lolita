package com.lolita.app.data

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.item.ItemDisplaySection
import com.lolita.app.ui.screen.material.*
import com.lolita.app.ui.screen.settings.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LaboratoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var f: MaterialTestFixture
    private val store = ViewModelStore()
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    private val page = mutableStateOf("lab")
    private lateinit var lab: LaboratoryViewModel
    private lateinit var workspace: MaterialWorkspaceViewModel
    private lateinit var batch: MaterialBatchViewModel
    private lateinit var settings: SettingsViewModel
    private lateinit var updates: AppUpdateViewModel
    private lateinit var preferences: AppPreferences
    private lateinit var host: android.view.View
    private lateinit var item: com.lolita.app.data.local.entity.Item
    private lateinit var material: com.lolita.app.data.local.entity.ItemTryOnMaterial
    private lateinit var category: com.lolita.app.data.local.entity.Category
    private var selectedWig = 0L
    private var generated = 0L
    private var viewed = 0L
    @Before fun setup() {
        f = MaterialTestFixture()
        runBlocking {
            f.init(); val items = f.categoryItems(); f.echoCategoryImages()
            item = items[0]; category = f.db.categoryDao().getCategoryById(item.categoryId)!!
            val identity = MaterialBatchIdentity(UUID.randomUUID().toString(), "展示图与封面")
            val fronts = f.coordinator.submitMaterialBatch(identity, UUID.randomUUID().toString(), f.batchInputs(listOf(item)), ImageGenerationProvider.SEEDREAM); f.runner.runQueue()
            val layers = f.coordinator.submitLayerBatch(identity, UUID.randomUUID().toString(), f.batchLayers(fronts)); f.runner.runQueue()
            val task = f.db.imageTaskDao().get(layers.single())!!
            f.coordinator.confirmMaterial(task.id, task.resultImagePath!!, null, true)
            material = f.db.itemTryOnMaterialDao().getForItem(item.id)!!
            preferences = AppPreferences(f.context, PreferenceDataStoreFactory.create(scope = f.scope) { File(f.root,"settings.preferences_pb") })
            withContext(Dispatchers.Main) {
                lab = LaboratoryViewModel(f.db.itemDao().getAllItems(), f.db.categoryDao().getAllCategories()).also { store.put("lab", it) }
                workspace = f.viewModel(SavedStateHandle(mapOf("itemId" to item.id))).also { store.put("workspace", it) }
                batch = f.batchViewModel(SavedStateHandle(mapOf("batchId" to identity.id))).also { store.put("batch", it) }
                settings = SettingsViewModel(preferences, ItemRepository(f.db.itemDao(),database=f.db), CoordinateRepository(f.db.coordinateDao(),f.db.itemDao(),f.db), PriceRepository(f.db.priceDao(),f.db.paymentDao(),f.context,f.db)).also { store.put("settings",it) }
                updates = AppUpdateViewModel(checkUpdate = { error("No network in UI tests") }).also { store.put("updates",it) }
            }
        }
        compose.setContent {
            val view=LocalView.current
            SideEffect { host=view; (view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            val config=Configuration(LocalConfiguration.current).apply { uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config) { LolitaTheme(skin.value,dark.value) { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) { key(page.value,skin.value,dark.value) {
                when(page.value) {
                    "personal" -> SettingsScreen({}, {}, onNavigateToLaboratory={page.value="lab"},appPreferences=preferences,settingsViewModel=settings,appUpdateViewModel=updates)
                    "lab" -> LaboratoryScreen({}, {page.value="batch"},{},{},{selectedWig=it},{},{},{},lab)
                    "workspace" -> MaterialWorkspaceScreen(workspace,false,{},{},{},{},{generated=it},{},{},{})
                    "batch" -> MaterialBatchScreen(batch,{},{},{},{},{})
                    "detail" -> Column(Modifier.fillMaxWidth()) { ItemDisplaySection(item,category,material,{generated=it},{viewed=it}) }
                }
            } } } }
        }
        compose.waitUntil(10_000) { !workspace.uiState.value.loading && !batch.uiState.value.loading }
    }
    @After fun close() { compose.runOnIdle { store.clear() }; runBlocking { f.close() } }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val imageLabel = when(page.value) {"workspace" -> "放大当前确认素材"; "batch" -> "放大第1件：${item.name}"; "detail" -> "查看已确认展示图";else -> null}
        if(imageLabel!=null) compose.waitUntil(10_000) {
            val n=compose.onNodeWithContentDescription(imageLabel)
            if(!n.isDisplayed()) false else n.captureToImage().asAndroidBitmap().let { bitmap ->
                var count=0
                for(y in 2..8) for(x in 2..8) { val c=bitmap.getPixel(bitmap.width*x/10,bitmap.height*y/10); if(android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)<650)count++ }
                bitmap.recycle();count>3
            }
        }
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"laboratory-native").apply{mkdirs()}
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
    @Test fun actualPagesAcrossSevenSkinsAndCoverActionsUseSameConfirmedImage() {
        Assume.assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale<1.5)
        for (mode in listOf("personal","lab","detail","workspace","batch")) {
            for(night in listOf(false,true)) for(type in SkinType.entries) {
                compose.runOnIdle { page.value=mode;skin.value=type;dark.value=night }
                when(mode) {
                    "personal" -> compose.onNodeWithTag("laboratoryEntry").performScrollTo().assertIsDisplayed()
                    "lab" -> compose.onNodeWithTag("laboratoryBatch").assertIsDisplayed()
                    "workspace" -> compose.onNodeWithTag("materialWorkspace").performScrollToNode(hasTestTag("materialSetCover"))
                    "batch" -> compose.onNodeWithTag("materialBatch").performScrollToNode(hasTestTag("batchSetCover${item.id}"))
                }
                screenshot("$mode-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            }
        }
        compose.runOnIdle { page.value="detail";skin.value=SkinType.DEFAULT;dark.value=false }
        compose.onAllNodesWithText("生成展示图").assertCountEquals(1)
        compose.onNodeWithText("生成展示图").performClick();assertEquals(item.id,generated)
        compose.onNodeWithContentDescription("查看已确认展示图").performClick();assertEquals(item.id,viewed)
        compose.runOnIdle { page.value="batch" }
        compose.onNodeWithTag("materialBatch").performScrollToNode(hasTestTag("batchSetCover${item.id}"))
        compose.onNodeWithTag("batchSetCover${item.id}").performClick()
        compose.waitUntil(10_000) { runBlocking { f.db.itemDao().getItemById(item.id)!!.imageUrls.first()==material.imagePath } }
        compose.onNodeWithTag("batchSetCover${item.id}").assertIsNotEnabled()
        compose.runOnIdle { page.value="workspace" }
        compose.onNodeWithTag("materialWorkspace").performScrollToNode(hasTestTag("materialSetCover"))
        compose.onNodeWithTag("materialSetCover").assertIsNotEnabled()
        assertEquals(2,f.serviceStarts)
        screenshot("cover-selected")
        compose.onNodeWithTag("materialPrimary").assertTextContains("重新生成展示图").performClick()
        assertEquals(item.id,generated)
        assertNull(workspace.uiState.value.fee)
    }
    @Test fun realSmallLargeFontLabAndWigSearchWorkWithKeyboard() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(context.resources.configuration.screenWidthDp<=320 && context.resources.configuration.fontScale>=1.99f)
        compose.runOnIdle { page.value="personal" }
        compose.onNodeWithTag("laboratoryEntry").performScrollTo().performClick()
        compose.onNodeWithTag("laboratoryBatch").assertIsDisplayed();screenshot("small-lab")
        compose.onNodeWithTag("laboratory").performScrollToNode(hasText("API 配置"));compose.onNodeWithText("API 配置").assertIsDisplayed();screenshot("small-services")
        compose.onNodeWithTag("laboratory").performScrollToNode(hasText("假发头模图"));compose.onNodeWithText("假发头模图").performClick()
        compose.onNodeWithTag("laboratoryWigSearch").performClick().performTextInput("假发")
        compose.waitUntil(10_000) { androidx.core.view.ViewCompat.getRootWindowInsets(host)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true }
        compose.onNodeWithTag("laboratoryWigSearch").assertIsDisplayed().assertTextContains("假发")
        compose.onNodeWithTag("laboratoryWigs").performScrollToNode(hasText("示例全头假发"))
        compose.onNodeWithText("示例全头假发").assertIsDisplayed()
        val dir=File(context.filesDir,"laboratory-native")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap -> File(dir,"small-wig-ime.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        compose.onNodeWithText("示例全头假发").performClick();assertTrue(selectedWig>0)
        compose.runOnIdle { page.value="workspace" }
        compose.onNodeWithTag("materialWorkspace").performScrollToNode(hasTestTag("materialSetCover"));compose.onNodeWithTag("materialSetCover").assertIsDisplayed();screenshot("small-cover")
        compose.onNodeWithTag("materialPrimary").assertIsDisplayed().assertTextContains("重新生成展示图")
        compose.runOnIdle { page.value="batch" }
        compose.onNodeWithTag("materialBatch").performScrollToNode(hasTestTag("batchSetCover${item.id}"))
        compose.onNodeWithTag("batchSetCover${item.id}").performScrollTo().assertIsDisplayed();screenshot("small-batch-cover")
        compose.runOnIdle { page.value="detail" }
        compose.onNodeWithText("生成展示图").assertIsDisplayed();screenshot("small-detail")
    }
}
