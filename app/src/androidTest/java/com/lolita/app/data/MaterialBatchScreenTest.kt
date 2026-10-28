package com.lolita.app.data

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.material.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class MaterialBatchScreenTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var vm:MaterialBatchViewModel
    private val store=ViewModelStore()
    private val skin=mutableStateOf(SkinType.DEFAULT);private val dark=mutableStateOf(false)
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var host:android.view.View
    @Before fun setup(){
        f=MaterialTestFixture();runBlocking{f.init();val items=f.categoryItems();items.drop(3).forEach{f.db.itemDao().deleteItem(it)}}
        f.displayOutput={f.receivedImages.last()}
        InstrumentationRegistry.getInstrumentation().runOnMainSync{vm=f.batchViewModel(SavedStateHandle());store.put("vm",vm)}
        compose.setContent{
            val view=androidx.compose.ui.platform.LocalView.current
            SideEffect{host=view;(view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
            val config=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides config){LolitaTheme(skin.value,dark.value){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){key(skin.value,dark.value){MaterialBatchScreen(vm,{},{},{},{},{})}}}}
        }
        compose.waitUntil(10000){!vm.uiState.value.loading && vm.uiState.value.keyLoaded && vm.uiState.value.rows.size==3}
    }
    @After fun close(){compose.runOnIdle{store.clear()};runBlocking{f.close()}}
    private fun screenshot(name:String){
        compose.waitForIdle();val fee=vm.uiState.value.fee!=null
        val node=if(fee)compose.onNodeWithTag("batchFeeRoot") else compose.onRoot()
        // Coil's local file decode runs outside Compose idling. Wait for visible fixture foregrounds.
        var captureDetails=""
        try{if(!dark.value)compose.waitUntil(10000){
            compose.mainClock.advanceTimeByFrame()
            InstrumentationRegistry.getInstrumentation().runOnMainSync{host.invalidate()}
            val bounds=node.fetchSemanticsNode().boundsInRoot
            val prefix=if(fee)"放大实际输入" else "放大第"
            val firstRow=vm.uiState.value.visibleRows.firstOrNull()
            val imageMatcher=SemanticsMatcher("fixture image"){it.config.getOrNull(SemanticsProperties.ContentDescription)?.any{s->s.startsWith(prefix) || !fee && s in setOf("本次原图完整预览","生成图完整预览")}==true}
            val images=compose.onAllNodes(if(!fee && firstRow!=null)imageMatcher and hasAnyAncestor(hasTestTag("batchRow${firstRow.itemId}")) else imageMatcher)
            val nodes=images.fetchSemanticsNodes()
            val visible=nodes.indices.filter{nodes[it].boundsInRoot.let{r->r.top>=bounds.top && r.bottom<=bounds.bottom && r.height>=nodes[it].size.height-1 && r.width>=nodes[it].size.width-1}}
            val counts=visible.map{index->
                // Capture the image node itself: root/window origins differ with system insets.
                val bitmap=images[index].captureToImage().asAndroidBitmap()
                var foreground=0
                for(y in 2..18)for(x in 2..18){val color=bitmap.getPixel(bitmap.width*x/20,bitmap.height*y/20)
                    val channels=listOf(android.graphics.Color.red(color),android.graphics.Color.green(color),android.graphics.Color.blue(color));if(channels.max()-channels.min()>15 || channels.min()<150)foreground++}
                bitmap.recycle();foreground
            }
            captureDetails="nodes=${nodes.size}, visible=$visible, foreground=$counts"
            counts.all{it>=10}
        }}catch(e:androidx.compose.ui.test.ComposeTimeoutException){
            val fail=File(context.filesDir,"display-comparison-native-20261028").apply{mkdirs()}
            node.captureToImage().asAndroidBitmap().let{im->File(fail,"failed-$name.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
            val source=vm.uiState.value.visibleRows.firstOrNull()?.source
            val sourceBitmap=source?.let(android.graphics.BitmapFactory::decodeFile)
            val center=sourceBitmap?.getPixel(sourceBitmap.width/2,sourceBitmap.height/2);sourceBitmap?.recycle()
            throw AssertionError("Image capture not ready: $name $captureDetails source=${source?.let{File(it).exists()}} center=$center",e)
        }
        val dir=File(context.filesDir,"display-comparison-native-20261028").apply{mkdirs()};node.captureToImage().asAndroidBitmap().let{im->File(dir,"$name.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    private fun skins(stage:String){for(night in listOf(false,true))for(type in SkinType.entries){compose.runOnIdle{skin.value=type;dark.value=night};compose.onNodeWithTag("batchPrimary").assertIsDisplayed();screenshot("$stage-${type.name.lowercase()}-${if(night)"dark" else "light"}");vm.uiState.value.visibleRows.firstOrNull()?.let{row->compose.onNodeWithTag("materialBatch").performScrollToNode(hasTestTag("batchRow${row.itemId}"));screenshot("$stage-images-${type.name.lowercase()}-${if(night)"dark" else "light"}")}}}
    private fun feeSend(){
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy}
        compose.onNodeWithTag("batchFeeSend").assertIsNotEnabled()
        compose.onNodeWithTag("batchFeeInputs").performScrollToNode(hasText("已核对本批全部实际输入、接收方和费用范围"))
        compose.onNodeWithText("已核对本批全部实际输入、接收方和费用范围").performClick()
        compose.onNodeWithTag("batchFeeSend").assertIsEnabled().performClick()
        compose.waitUntil(10000){vm.uiState.value.fee==null && !vm.uiState.value.busy}
    }
    @Test fun batchWholeFlowAcrossSevenSkinsKeepsEachItemIndependentAndSavesTogether(){
        compose.onNodeWithTag("batchPrimary").assertIsNotEnabled();skins("inputs")
        compose.onNodeWithText("透明层").assertDoesNotExist()
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.uiState.value.rows.forEach{vm.toggle(it.key,true)};vm.prepareFee()}
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy};screenshot("fee-display")
        assertEquals(0,f.displayCalls);feeSend();screenshot("queue-display")
        assertEquals(3,runBlocking{f.db.imageTaskDao().getAll().size});assertEquals(0,f.displayCalls)
        f.displayOutput={if(f.displayCalls==2)throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL) else f.receivedImages.last()}
        runBlocking{f.runner.runQueue()};compose.waitUntil(10000){vm.uiState.value.rows.count{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION}==2}
        skins("review")
        val first=vm.uiState.value.visibleRows.first{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION}
        val oldSelection=vm.uiState.value.selected
        compose.onNodeWithTag("materialBatch").performScrollToNode(hasTestTag("compareOpen${first.task!!.id}"))
        compose.onNodeWithTag("compareOpen${first.task!!.id}").performClick()
        compose.onNodeWithTag("displayComparison").assertIsDisplayed()
        compose.onNode(hasContentDescription("本次原图完整预览") and hasAnyAncestor(hasTestTag("displayComparison"))).assertIsDisplayed()
        compose.onNode(hasContentDescription("生成图完整预览") and hasAnyAncestor(hasTestTag("displayComparison"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭对比").performClick()
        assertEquals(oldSelection,vm.uiState.value.selected)
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.selectReady()}
        compose.onNodeWithTag("batchPrimary").assertIsNotEnabled()
        for(label in listOf("组成与附属部件完整","颜色、造型与数量正确","无多余文字或其他物品，人台符合所选类型")){
            compose.onNodeWithTag("materialBatch").performScrollToNode(hasText(label));compose.onNodeWithText(label).performClick()
        }
        compose.onNodeWithTag("batchPrimary").assertIsEnabled().performClick()
        compose.waitUntil(10000){vm.uiState.value.phase==MaterialBatchPhase.SAVED && vm.uiState.value.rows.size==2 && !vm.uiState.value.busy}
        skins("saved")
        assertTrue(runBlocking{f.db.itemTryOnMaterialDao().getAll().isEmpty()});assertEquals(3,f.displayCalls);assertEquals(0,f.layerCalls);assertEquals(0,f.fusionCalls)
        assertEquals(2,runBlocking{f.db.imageProvenanceDao().getAll().size})
        compose.runOnIdle{vm.phase(MaterialBatchPhase.INPUTS);vm.selectFailed();vm.prepareFee()}
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy}
        assertEquals(1,vm.uiState.value.fee!!.rows.size);feeSend()
        f.displayOutput={f.receivedImages.last()};runBlocking{f.runner.runQueue()}
        compose.waitUntil(10000){vm.uiState.value.rows.any{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION}}
        assertEquals(4,f.displayCalls);assertEquals(0,f.layerCalls)
    }
    @Test fun realSmallDeviceLargeFontSearchKeyboardAndFeeRemainReachable(){
        val config=context.resources.configuration;Assume.assumeTrue(config.screenWidthDp<=320 && config.fontScale>=1.99f)
        compose.runOnIdle{vm.uiState.value.rows.forEach{vm.toggle(it.key,true)}};screenshot("small-inputs")
        compose.onNodeWithTag("batchSearch").performScrollTo().performClick().performTextInput("示例衬衫")
        compose.waitUntil(10000){androidx.core.view.ViewCompat.getRootWindowInsets(host)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        assertEquals(3,vm.uiState.value.selectedRows.size);compose.onNodeWithTag("batchPrimary").assertIsDisplayed()
        val dir=File(context.filesDir,"batch-display-native-20261027");InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{im->File(dir,"small-ime.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
        compose.onNodeWithTag("batchPrimary").performClick()
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy};assertEquals(3,vm.uiState.value.fee!!.rows.size)
        compose.onNodeWithTag("batchFeeSend").assertIsDisplayed().assertIsNotEnabled();screenshot("small-fee")
        compose.runOnIdle{vm.dismissFee()}
    }
}
