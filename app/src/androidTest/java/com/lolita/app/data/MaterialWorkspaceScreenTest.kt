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
import com.lolita.app.ui.screen.material.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Compose pages and native images with controlled transport. No network/paid calls. */
class MaterialWorkspaceScreenTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private val store=ViewModelStore()
    private val vm=mutableStateOf<MaterialWorkspaceViewModel?>(null)
    private val skin=mutableStateOf(SkinType.DEFAULT)
    private val dark=mutableStateOf(false)
    private val adding=mutableStateOf(false)
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private var openedTask:String?=null
    private lateinit var hostView:android.view.View
    private fun show(taskId:String?=null,baseId:Long?=null) {
        compose.runOnIdle {
            store.clear()
            vm.value=f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId,"taskId" to taskId,"baseResultId" to baseId))).also{store.put("vm",it)}
        }
        compose.waitUntil(10000){vm.value?.uiState?.value?.let{!it.loading && it.keyLoaded}==true}
    }
    @Before fun setup() {
        f=MaterialTestFixture();runBlocking{f.init()}
        compose.setContent {
            val view=androidx.compose.ui.platform.LocalView.current
            SideEffect { hostView=view; (view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            val config=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides config) {
                LolitaTheme(skin.value,dark.value) { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    vm.value?.let { model -> key(model,skin.value,dark.value) { MaterialWorkspaceScreen(model,adding.value,{},{openedTask=it},{},{},{},{},{},{}) } }
                } }
            }
        }
        show()
    }
    @After fun close() { compose.runOnIdle{vm.value=null;store.clear()};runBlocking{f.close()} }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val dir=File(context.filesDir,"material-native-screens").apply{mkdirs()}
        val node=if(vm.value?.uiState?.value?.fee!=null) compose.onNodeWithTag("materialFeeRoot") else compose.onRoot()
        node.captureToImage().asAndroidBitmap().let { image -> File(dir,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)} }
    }
    private fun allSkins(stage:String) {
        for(night in listOf(false,true)) for(type in SkinType.entries) {
            compose.runOnIdle{skin.value=type;dark.value=night}
            compose.onNodeWithTag("materialPrimary").assertIsDisplayed()
            screenshot("$stage-${type.name.lowercase()}-${if(night)"dark" else "light"}")
        }
    }
    @Test fun realPagesAcrossSevenSkinsLightDarkAndExplicitFeeReview() {
        allSkins("prepare")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.value!!.prepareFee(MaterialFeeAction.PREPARE)}
        compose.waitUntil(10000){vm.value!!.uiState.value.fee!=null && !vm.value!!.uiState.value.busy}
        compose.onNodeWithTag("materialFeeSend").assertIsNotEnabled()
        screenshot("fee-prepare-default-light")
        compose.runOnIdle{vm.value!!.dismissFee()}
        val front=runBlocking{f.front()};show(front)
        compose.onNodeWithTag("materialPrimary").assertIsNotEnabled();allSkins("front")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.value!!.frontConfirmed(true);vm.value!!.prepareFee(MaterialFeeAction.DECOMPOSE)}
        compose.waitUntil(10000){vm.value!!.uiState.value.fee!=null && !vm.value!!.uiState.value.busy}
        compose.onNodeWithTag("materialFeeSend").assertIsNotEnabled();screenshot("fee-layer-default-light")
        compose.runOnIdle{vm.value!!.dismissFee()}
        val layers=runBlocking{f.layers(front)};show(layers)
        compose.onNodeWithTag("materialPrimary").assertIsNotEnabled();allSkins("layers")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false}
        compose.onNodeWithContentDescription("放大待核对图片").performClick()
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed().performClick()
        compose.runOnIdle{(0..2).forEach{vm.value!!.check(it,true)}}
        compose.onNodeWithTag("materialPrimary").assertIsEnabled().performClick()
        compose.waitUntil(10000){vm.value!!.uiState.value.saved}
        show();allSkins("saved")
        val base=runBlocking{f.base()};compose.runOnIdle{adding.value=true};show(baseId=base.id)
        compose.waitUntil(10000){vm.value!!.uiState.value.selectedBase!=null}
        compose.onNodeWithTag("materialPrimary").assertIsNotEnabled();allSkins("accessory")
        compose.runOnIdle{skin.value=SkinType.DEFAULT;dark.value=false;vm.value!!.clothingConfirmed(true);vm.value!!.prepareFee(MaterialFeeAction.ADD_ACCESSORY)}
        compose.waitUntil(10000){vm.value!!.uiState.value.fee!=null && !vm.value!!.uiState.value.busy}
        compose.onNodeWithTag("materialFeeSend").assertIsNotEnabled();screenshot("fee-accessory-default-light")
        assertEquals(1,f.frontCalls);assertEquals(1,f.layerCalls);assertEquals(0,f.fusionCalls);assertNull(openedTask)
        compose.runOnIdle{vm.value!!.dismissFee()}
    }
    @Test fun actualSmallDeviceLargeFontAndKeyboardKeepsPrimaryReachable() {
        val config=context.resources.configuration
        // This method is invoked separately on a real 320dp / 2x-font AVD override.
        Assume.assumeTrue(config.screenWidthDp<=320 && config.fontScale>=1.99f)
        screenshot("small-prepare")
        compose.onNodeWithTag("materialWorkspace").performScrollToNode(hasTestTag("materialInstructions"))
        compose.onNodeWithTag("materialInstructions").performScrollTo().performClick().performTextInput("两枚扣具及细链都保留")
        compose.waitUntil(10000){androidx.core.view.ViewCompat.getRootWindowInsets(hostView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        compose.waitForIdle();compose.onNodeWithTag("materialPrimary").assertIsDisplayed();screenshot("small-prepare-ime")
        val dir=File(context.filesDir,"material-native-screens");InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{image->File(dir,"small-ime-full-device.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        compose.onNodeWithTag("materialPrimary").performClick()
        compose.waitUntil(10000){vm.value!!.uiState.value.fee!=null && !vm.value!!.uiState.value.busy}
        compose.onNodeWithTag("materialFeeSend").assertIsDisplayed().assertIsNotEnabled();screenshot("small-fee")
        compose.runOnIdle{vm.value!!.dismissFee()}
    }
}
