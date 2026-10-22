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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import org.json.JSONObject
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
    @Test fun laboratoryEntryMatchesPeerAndNavigationTitlesStayCompactAcrossSkins() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(context.resources.configuration.fontScale<1.5f)
        val measurements=JSONArray()
        fun textLayout(node:SemanticsNodeInteraction):TextLayoutResult {
            val layouts=mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            return layouts.single()
        }
        for(night in listOf(false,true))for(type in SkinType.entries) {
            compose.runOnIdle { page.value="personal";skin.value=type;dark.value=night }
            compose.onNodeWithTag("laboratoryEntry").performScrollTo().assertIsDisplayed()
            val entry=textLayout(compose.onNode(hasText("实验室") and hasAnyAncestor(hasTestTag("laboratoryEntry")),useUnmergedTree=true)).layoutInput.style
            val peer=textLayout(compose.onNodeWithText("属性管理",useUnmergedTree=true)).layoutInput.style
            assertEquals(peer.fontSize,entry.fontSize)
            assertEquals(peer.lineHeight,entry.lineHeight)
            assertEquals(peer.fontWeight,entry.fontWeight)
            assertEquals(peer.fontFamily,entry.fontFamily)
            assertEquals(16f,entry.fontSize.value)
            fun textNode(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)
            val titleBounds = textNode("实验室").fetchSemanticsNode().boundsInRoot
            val descriptionBounds = textNode("展示图、试衣与识图").fetchSemanticsNode().boundsInRoot
            val peerTitle = textNode("属性管理").fetchSemanticsNode().boundsInRoot
            val peerDescription = textNode("品牌、类型与收纳等").fetchSemanticsNode().boundsInRoot
            assertEquals(titleBounds.left, descriptionBounds.left, 1f)
            assertEquals(peerTitle.left, titleBounds.left, 1f)
            assertEquals(peerDescription.left, descriptionBounds.left, 1f)
            val icon = compose.onNodeWithTag("settingsIcon-Laboratory", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val peerIcon = compose.onNodeWithTag("settingsIcon-Category", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(peerIcon.left, icon.left, 1f)
            assertEquals(peerIcon.right, icon.right, 1f)
            val subtitle = textLayout(textNode("展示图、试衣与识图")).layoutInput.style
            val peerSubtitle = textLayout(textNode("品牌、类型与收纳等")).layoutInput.style
            assertEquals(peerSubtitle, subtitle)
            screenshot("compact-entry-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            compose.onNodeWithTag("laboratoryEntry").performClick()
            compose.onNodeWithTag("laboratoryBatch").assertIsDisplayed()
            val titleLayout=textLayout(compose.onNodeWithText("实验室",useUnmergedTree=true))
            val title=titleLayout.layoutInput.style
            assertEquals(18f,title.fontSize.value)
            assertEquals(26f,title.lineHeight.value)
            assertEquals(FontWeight.Medium,title.fontWeight)
            assertEquals(titleLayout.layoutInput.text.length,titleLayout.getLineEnd(titleLayout.lineCount-1))
            assertTrue((0 until titleLayout.lineCount).all { line ->
                !titleLayout.isLineEllipsized(line) && titleLayout.getLineRight(line)<=titleLayout.size.width+1f &&
                    titleLayout.getLineBottom(line)<=titleLayout.size.height+1f
            })
            screenshot("compact-page-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            measurements.put(JSONObject().put("skin",type.name).put("dark",night)
                .put("entry_sp",entry.fontSize.value).put("entry_weight",entry.fontWeight!!.weight)
                .put("same_as_peer",true).put("top_bar_sp",title.fontSize.value).put("top_bar_weight",title.fontWeight!!.weight))
        }
        File(context.filesDir,"laboratory-native/compact-title-measurements.json").writeText(measurements.toString(2))
        compose.onNodeWithTag("laboratoryBatch").performClick()
        compose.onNodeWithTag("materialBatch").assertIsDisplayed()
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
        compose.onNodeWithTag("laboratoryEntry").performScrollTo().assertIsDisplayed()
        val title=compose.onNodeWithText("实验室",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val description=compose.onNodeWithText("展示图、试衣与识图",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val peer=compose.onNodeWithText("品牌、类型与收纳等",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(title.left,description.left,1f);assertEquals(peer.left,description.left,1f)
        compose.onNodeWithTag("settingsIcon-Laboratory", useUnmergedTree = true).assertIsDisplayed()
        screenshot("small-aligned-entry")
        compose.onNodeWithTag("laboratoryEntry").performClick()
        compose.onNodeWithTag("laboratory").performScrollToNode(hasTestTag("laboratoryBatch"))
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
