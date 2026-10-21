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
        f=MaterialTestFixture();runBlocking{f.init();f.batchItems(3)}
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
        if(!dark.value)compose.waitUntil(10000){
            val bounds=node.fetchSemanticsNode().boundsInRoot
            val prefix=if(fee)"放大实际输入" else "放大第"
            val images=compose.onAllNodes(SemanticsMatcher("fixture image"){it.config.getOrNull(SemanticsProperties.ContentDescription)?.any{s->s.startsWith(prefix)}==true})
            val nodes=images.fetchSemanticsNodes()
            nodes.indices.filter{nodes[it].boundsInRoot.let{r->r.top>=bounds.top && r.bottom<=bounds.bottom && r.height>=nodes[it].size.height-1 && r.width>=nodes[it].size.width-1}}.all{index->
                // Capture the image node itself: root/window origins differ with system insets.
                val bitmap=images[index].captureToImage().asAndroidBitmap()
                var foreground=0
                for(y in 2..8)for(x in 2..8){val color=bitmap.getPixel(bitmap.width*x/10,bitmap.height*y/10)
                    if(android.graphics.Color.red(color)<150 && android.graphics.Color.green(color)<150)foreground++}
                bitmap.recycle();foreground>=4
            }
        }
        val dir=File(context.filesDir,"material-batch-native").apply{mkdirs()};node.captureToImage().asAndroidBitmap().let{im->File(dir,"$name.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    private fun skins(stage:String){for(night in listOf(false,true))for(type in SkinType.entries){compose.runOnIdle{skin.value=type;dark.value=night};compose.onNodeWithTag("batchPrimary").assertIsDisplayed();screenshot("$stage-${type.name.lowercase()}-${if(night)"dark" else "light"}")}}
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
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.uiState.value.rows.forEach{vm.toggle(it.key,true)};vm.prepareFee()}
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy};screenshot("fee-front")
        assertEquals(0,f.frontCalls);feeSend();screenshot("queue-front")
        assertEquals(3,runBlocking{f.db.imageTaskDao().getAll().size});assertEquals(0,f.frontCalls)
        f.frontOutput={if(f.frontCalls==2)throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL) else f.opaque}
        runBlocking{f.runner.runQueue()};compose.waitUntil(10000){vm.uiState.value.rows.count{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION}==2}
        skins("front")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.selectReady();vm.prepareFee()}
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy}
        assertEquals(2,vm.uiState.value.fee!!.rows.size);screenshot("fee-layers");feeSend()
        runBlocking{f.runner.runQueue()};compose.waitUntil(10000){vm.uiState.value.phase==MaterialBatchPhase.LAYERS && vm.uiState.value.rows.size==2 && vm.uiState.value.rows.all{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION}}
        skins("layers")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.selectReady()}
        compose.onNodeWithTag("batchPrimary").assertIsNotEnabled()
        for(label in listOf("各自只包含该物品及附属部件","造型、颜色和关键五金正确","没有残留人台、文字或其他服饰")){
            compose.onNodeWithTag("materialBatch").performScrollToNode(hasText(label));compose.onNodeWithText(label).performClick()
        }
        compose.onNodeWithTag("batchPrimary").assertIsEnabled().performClick()
        compose.waitUntil(10000){vm.uiState.value.phase==MaterialBatchPhase.SAVED && vm.uiState.value.rows.size==2 && !vm.uiState.value.busy}
        skins("saved");assertEquals(2,runBlocking{f.db.itemTryOnMaterialDao().getAll().size});assertEquals(3,f.frontCalls);assertEquals(2,f.layerCalls);assertEquals(0,f.fusionCalls)
    }
    @Test fun realSmallDeviceLargeFontSearchKeyboardAndFeeRemainReachable(){
        val config=context.resources.configuration;Assume.assumeTrue(config.screenWidthDp<=320 && config.fontScale>=1.99f)
        compose.runOnIdle{vm.uiState.value.rows.forEach{vm.toggle(it.key,true)}};screenshot("small-inputs")
        compose.onNodeWithTag("batchSearch").performScrollTo().performClick().performTextInput("批量腰饰2")
        compose.waitUntil(10000){androidx.core.view.ViewCompat.getRootWindowInsets(host)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        assertEquals(3,vm.uiState.value.selectedRows.size);compose.onNodeWithTag("batchPrimary").assertIsDisplayed()
        val dir=File(context.filesDir,"material-batch-native");InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{im->File(dir,"small-ime.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
        compose.onNodeWithTag("batchPrimary").performClick()
        compose.waitUntil(10000){vm.uiState.value.fee!=null && !vm.uiState.value.busy};assertEquals(3,vm.uiState.value.fee!!.rows.size)
        compose.onNodeWithTag("batchFeeSend").assertIsDisplayed().assertIsNotEnabled();screenshot("small-fee")
        compose.runOnIdle{vm.dismissFee()}
    }
}
