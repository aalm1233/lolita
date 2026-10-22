package com.lolita.app.data

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.model.*
import com.lolita.app.ui.navigation.*
import com.lolita.app.ui.screen.material.MaterialBatchViewModel
import com.lolita.app.ui.screen.tasks.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class MaterialBatchNavigationTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var nav:NavHostController
    private var tasks:ImageTaskViewModel?=null
    private var batchVm:MaterialBatchViewModel?=null
    @Before fun setup(){f=MaterialTestFixture();runBlocking{f.init();f.batchItems()}}
    @After fun close(){runBlocking{f.close()}}
    @Test fun taskCenterGroupsBatchAndOpensOneWorkspaceWithIndependentNewBatchEntry(){
        val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"导航批次")
        runBlocking{f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(f.db.itemDao().getAllItemsList()),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()}
        compose.setContent{LolitaTheme{
            val controller=rememberNavController();SideEffect{nav=controller};val skin=LolitaSkin.current
            val still=remember(skin){skin.copy(topBarBlurEnabled=false,navBarBlurEnabled=false,animations=object:SkinAnimationProvider by skin.animations{override val ambientAnimation=skin.animations.ambientAnimation.copy(backgroundEnabled=false)})}
            CompositionLocalProvider(LocalLolitaSkin provides still){LolitaNavHost(navController=controller,
                imageTaskBarViewModel=remember{ImageTaskBarViewModel(f.db.imageTaskDao().observe())},
                createMaterialBatchViewModel={f.batchViewModel(it).also{batchVm=it}},
                createImageTaskViewModel={ImageTaskViewModel(f.db.imageTaskDao().observe(),f.db.itemDao().getAllItems(),f.db.tryOnDao().observe(),{},{},{},{},materials=f.db.itemTryOnMaterialDao().observeAll()).also{tasks=it}})}
        }}
        compose.runOnIdle{nav.navigate(Screen.ImageTasks.route)}
        compose.waitUntil(10000){tasks?.uiState?.value?.tasks?.size==3}
        compose.onNodeWithTag("imageTasks").performScrollToNode(hasText("查看批次"))
        compose.onAllNodesWithText("查看批次").assertCountEquals(1)
        compose.onNodeWithText("查看批次").performClick()
        compose.waitUntil(10000){batchVm?.uiState?.value?.let{!it.loading && it.jobs.size==3}==true}
        compose.onNodeWithTag("batchPrimary").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("首页").assertIsDisplayed()
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"material-batch-native").apply{mkdirs()}
        compose.onRoot().captureToImage().asAndroidBitmap().let{im->File(dir,"nav-workspace.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("imageTasks").performScrollToNode(hasText("新建展示图批次"));compose.onNodeWithText("新建展示图批次").performClick()
        compose.waitUntil(10000){batchVm?.uiState?.value?.let{!it.loading && it.draft}==true}
        assertTrue(batchVm!!.uiState.value.selected.isEmpty());assertEquals(3,runBlocking{f.db.imageTaskDao().getAll().size});assertEquals(0,f.layerCalls)
    }
}
