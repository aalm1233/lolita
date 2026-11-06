package com.lolita.app.ui.screen.square

import androidx.compose.runtime.*
import com.lolita.app.ui.theme.LocalLolitaSkin
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.ui.theme.LolitaTheme
import org.junit.*
import org.junit.Assert.*

class SquareNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun fiveDestinationsAndSquareImportUseActualAppNavigation(){
        lateinit var nav:NavHostController
        compose.setContent{LolitaTheme{val n=rememberNavController();SideEffect{nav=n}
            val active=LolitaSkin.current
            val still=remember(active){active.copy(topBarBlurEnabled=false,navBarBlurEnabled=false,
                animations=object:SkinAnimationProvider by active.animations {override val ambientAnimation=active.animations.ambientAnimation.copy(backgroundEnabled=false)})}
            CompositionLocalProvider(LocalLolitaSkin provides still){LolitaNavHost(navController=n)}}}
        for(label in listOf("首页","广场","穿搭","统计","个人")){
            compose.onNode(isSelectable() and hasText(label)).assertIsDisplayed().performClick().assertIsSelected()
        }
        compose.onNode(isSelectable() and hasText("广场")).performClick()
        compose.waitUntil(10000){nav.currentDestination?.route==Screen.Square.route}
        compose.onNodeWithText("导入资料包").performScrollTo().assertIsDisplayed()
        assertEquals(5,com.lolita.app.ui.navigation.BottomNavItems.items.size)
        compose.onNodeWithText("导入资料包").performClick()
        val inst=InstrumentationRegistry.getInstrumentation()
        var pickerVisible=false
        repeat(50) {
            if(inst.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.google.android.documentsui")pickerVisible=true
            if(!pickerVisible)android.os.SystemClock.sleep(100)
        }
        assertTrue("System document picker must open",pickerVisible)
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        android.os.SystemClock.sleep(300)
        compose.onNodeWithText("导入资料包").assertExists()
    }
}
