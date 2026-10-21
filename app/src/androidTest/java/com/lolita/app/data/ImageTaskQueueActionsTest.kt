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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.material.MaterialBatchScreen
import com.lolita.app.ui.screen.material.MaterialBatchViewModel
import com.lolita.app.ui.screen.tasks.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ImageTaskQueueActionsTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var tasks:ImageTaskViewModel
    private lateinit var batchVm:MaterialBatchViewModel
    private lateinit var ids:List<String>
    private val store=ViewModelStore()
    private val batchPage=mutableStateOf(false)
    private val skin=mutableStateOf(SkinType.DEFAULT)
    private val dark=mutableStateOf(false)
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        f=MaterialTestFixture();val group=MaterialBatchIdentity(UUID.randomUUID().toString(),"秋日服饰展示图")
        runBlocking {
            f.init();val items=f.batchItems(4)
            ids=f.coordinator.submitMaterialBatch(group,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
            f.db.imageTaskDao().get(ids[2])!!.let{f.db.imageTaskDao().update(it.copy(status=ImageTaskStatus.QUEUED,requestSent=false,resultImagePath=null))}
            f.db.imageTaskDao().get(ids[3])!!.let{f.db.imageTaskDao().update(it.copy(status=ImageTaskStatus.GENERATING,requestSent=true,resultImagePath=null))}
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            tasks=ImageTaskViewModel(f.db.imageTaskDao().observe(),f.db.itemDao().getAllItems(),f.db.tryOnDao().observe(),
                cancelTask=f.coordinator::cancel,discardTask=f.coordinator::discard,deleteTask=f.coordinator::delete,
                stopQueuedTasks={f.coordinator.stopQueued(it)},discardPendingTasks={f.coordinator.discardPending(it)})
            batchVm=f.batchViewModel(SavedStateHandle(mapOf("batchId" to group.id,"batchPhase" to "FRONT")))
            store.put("tasks",tasks);store.put("batch",batchVm)
        }
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides config) {
                LolitaTheme(skin.value,dark.value) {
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                        key(batchPage.value,skin.value,dark.value) {
                            if(batchPage.value) MaterialBatchScreen(batchVm,{},{},{},{},{}) else ImageTaskScreen({},{},tasks)
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000){!tasks.uiState.value.isLoading && !batchVm.uiState.value.loading}
    }
    @After fun close(){compose.runOnIdle{store.clear()};runBlocking{f.close()}}
    private fun scroll(tag:String){compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()}
    private fun screenshot(name:String,dialog:Boolean=false){
        val dir=File(context.filesDir,"task-queue-native").apply{mkdirs()}
        val node=if(dialog)compose.onNodeWithTag("discardPendingDialog") else compose.onRoot()
        node.captureToImage().asAndroidBitmap().let{im->File(dir,"$name.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
    }
    @Test fun taskCenterStopIncludesFilteredOutQueueAndKeepsCurrent() {
        compose.runOnIdle{tasks.filter(ImageTaskFilter.PENDING)}
        scroll("stopQueued");compose.onNodeWithTag("stopQueued").performClick()
        compose.onNodeWithText("确认停止后续").performClick()
        compose.waitUntil(10_000){tasks.uiState.value.tasks.any{it.id==ids[2] && it.status==ImageTaskStatus.CANCELLED}}
        assertEquals(ImageTaskStatus.GENERATING,tasks.uiState.value.tasks.single{it.id==ids[3]}.status)
        assertEquals(4,f.frontCalls)
    }
    @Test fun selectionZoomDismissAndExplicitDiscardPreserveOtherResults() {
        scroll("discardPending");compose.onNodeWithTag("discardPending").performClick()
        compose.onNodeWithTag("confirmDiscardPending").assertIsNotEnabled()
        compose.onNodeWithTag("pendingChoice${ids[0]}").performScrollTo().performClick()
        val title=tasks.uiState.value.tasks.single{it.id==ids[0]}.title
        compose.onNodeWithContentDescription("放大待确认结果：$title").performScrollTo().performClick()
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed().performClick()
        compose.onNodeWithText("返回核对").performClick()
        assertTrue(tasks.uiState.value.tasks.filter{it.id in ids.take(2)}.all{it.status==ImageTaskStatus.AWAITING_CONFIRMATION})
        compose.onNodeWithTag("discardPending").performClick()
        compose.onNodeWithTag("confirmDiscardPending").assertIsNotEnabled()
        compose.onNodeWithTag("pendingChoice${ids[0]}").performScrollTo().performClick()
        compose.onNodeWithTag("confirmDiscardPending").performClick()
        compose.waitUntil(10_000){tasks.uiState.value.tasks.any{it.id==ids[0] && it.status==ImageTaskStatus.DISCARDED}}
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,tasks.uiState.value.tasks.single{it.id==ids[1]}.status)
        assertEquals(4,f.frontCalls)
    }
    @Test fun batchPageSelectAllDiscardsOnlyPendingAndStopTailKeepsCurrent() {
        compose.runOnIdle{batchPage.value=true}
        scroll("discardPending");compose.onNodeWithTag("discardPending").performClick()
        compose.onNodeWithTag("selectAllPending").performClick();compose.onNodeWithTag("confirmDiscardPending").performClick()
        compose.waitUntil(10_000){batchVm.uiState.value.jobs.count{it.status==ImageTaskStatus.DISCARDED}==2}
        scroll("stopQueued");compose.onNodeWithTag("stopQueued").performClick();compose.onNodeWithText("确认停止后续").performClick()
        compose.waitUntil(10_000){batchVm.uiState.value.jobs.any{it.id==ids[2] && it.status==ImageTaskStatus.CANCELLED}}
        assertEquals(ImageTaskStatus.GENERATING,batchVm.uiState.value.jobs.single{it.id==ids[3]}.status)
        assertEquals(4,f.frontCalls)
    }
    @Test fun realPagesAndSelectionAcrossSevenSkinsLightDark() {
        for(night in listOf(false,true))for(type in SkinType.entries){
            compose.runOnIdle{skin.value=type;dark.value=night;batchPage.value=false}
            scroll("stopQueued");screenshot("center-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            compose.onNodeWithTag("discardPending").performScrollTo().performClick()
            compose.onNodeWithTag("confirmDiscardPending").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag("selectAllPending").performClick()
            compose.onNodeWithTag("confirmDiscardPending").assertIsEnabled()
            screenshot("selection-${type.name.lowercase()}-${if(night)"dark" else "light"}",true)
            compose.onNodeWithText("返回核对").performClick()
            compose.runOnIdle{batchPage.value=true}
            scroll("stopQueued");screenshot("batch-${type.name.lowercase()}-${if(night)"dark" else "light"}")
        }
    }
    @Test fun real320dpDoubleFontKeepsBothPagesAndDialogActionsReachable() {
        val config=context.resources.configuration;Assume.assumeTrue(config.screenWidthDp<=320 && config.fontScale>=1.99f)
        for(batch in listOf(false,true)) {
            compose.runOnIdle{batchPage.value=batch}
            scroll("stopQueued");screenshot("small-${if(batch)"batch" else "center"}")
            compose.onNodeWithTag("discardPending").performScrollTo().performClick()
            compose.onNodeWithTag("selectAllPending").assertIsDisplayed().performClick()
            compose.onNodeWithTag("confirmDiscardPending").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("pendingChoice${ids[1]}").performScrollTo().assertIsDisplayed()
            val title=tasks.uiState.value.tasks.single{it.id==ids[1]}.title
            compose.onNodeWithContentDescription("放大待确认结果：$title").performScrollTo().assertIsDisplayed()
            screenshot("small-${if(batch)"batch" else "center"}-selection",true)
            compose.onNodeWithText("返回核对").performClick()
        }
    }
}
