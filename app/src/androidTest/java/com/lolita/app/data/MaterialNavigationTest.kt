package com.lolita.app.data

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.lolita.app.ui.navigation.*
import com.lolita.app.ui.screen.tasks.*
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap

class MaterialNavigationTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var controller:NavHostController
    private var taskViewModel:ImageTaskViewModel?=null
    @Before fun setup(){f=MaterialTestFixture();runBlocking{f.init()}}
    @After fun close(){runBlocking{f.close()}}
    @Test fun pendingTaskOpensNativeReviewAndBackKeepsPendingMaterial() {
        val task=runBlocking{f.layers(f.front())}
        compose.setContent { LolitaTheme {
            val nav=rememberNavController();SideEffect{controller=nav}
            val skin=com.lolita.app.ui.theme.LolitaSkin.current
            val still=remember(skin){skin.copy(topBarBlurEnabled=false,navBarBlurEnabled=false,
                animations=object:com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider by skin.animations{
                    override val ambientAnimation=skin.animations.ambientAnimation.copy(backgroundEnabled=false)
                })}
            CompositionLocalProvider(com.lolita.app.ui.theme.LocalLolitaSkin provides still) {
            LolitaNavHost(navController=nav,
                imageTaskBarViewModel=remember{ImageTaskBarViewModel(f.db.imageTaskDao().observe())},
                createMaterialWorkspaceViewModel={f.viewModel(it)},
                createImageTaskViewModel={ImageTaskViewModel(f.db.imageTaskDao().observe(),f.db.itemDao().getAllItems(),f.db.tryOnDao().observe(),{},{},{},{},materials=f.db.itemTryOnMaterialDao().observeAll()).also{taskViewModel=it}})
            }
        } }
        compose.runOnIdle{controller.navigate(Screen.ImageTaskDetail.createRoute(task))}
        compose.waitUntil(10000){taskViewModel?.uiState?.value?.tasks?.any{it.id==task}==true}
        compose.onNodeWithTag("imageTaskDetail").performScrollToNode(hasText("核对试衣素材"))
        compose.onNodeWithText("核对试衣素材").performClick()
        compose.waitUntil(10000){controller.currentDestination?.route==Screen.TryOnMaterial.route}
        compose.onNodeWithTag("materialPrimary").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("首页").assertIsDisplayed()
        val dir=File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"material-native-screens").apply{mkdirs()}
        compose.onRoot().captureToImage().asAndroidBitmap().let{image->File(dir,"material-review-nav.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10000){controller.currentDestination?.route==Screen.ImageTaskDetail.route}
        assertEquals(com.lolita.app.domain.model.ImageTaskStatus.AWAITING_CONFIRMATION,runBlocking{f.db.imageTaskDao().get(task)!!.status})
        assertEquals(0,f.fusionCalls)
    }
}
