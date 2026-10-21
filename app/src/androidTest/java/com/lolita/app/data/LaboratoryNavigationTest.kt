package com.lolita.app.data

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.lolita.app.ui.navigation.*
import com.lolita.app.ui.screen.material.MaterialBatchViewModel
import com.lolita.app.ui.screen.settings.LaboratoryViewModel
import com.lolita.app.ui.screen.tasks.ImageTaskBarViewModel
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class LaboratoryNavigationTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var nav:NavHostController
    private var batch:MaterialBatchViewModel?=null
    @Before fun setup(){f=MaterialTestFixture();runBlocking{f.init();f.categoryItems()}}
    @After fun close(){runBlocking{f.close()}}
    @Test fun personalLaboratoryProvidesBatchAndAllAiRoutesWithoutSubmitting() {
        compose.setContent { LolitaTheme {
            val controller=rememberNavController();SideEffect{nav=controller};val skin=LolitaSkin.current
            val still=remember(skin){skin.copy(topBarBlurEnabled=false,navBarBlurEnabled=false,animations=object:SkinAnimationProvider by skin.animations{override val ambientAnimation=skin.animations.ambientAnimation.copy(backgroundEnabled=false)})}
            CompositionLocalProvider(LocalLolitaSkin provides still) { LolitaNavHost(navController=controller,
                imageTaskBarViewModel=remember{ImageTaskBarViewModel(f.db.imageTaskDao().observe())},
                createLaboratoryViewModel={LaboratoryViewModel(f.db.itemDao().getAllItems(),f.db.categoryDao().getAllCategories())},
                createMaterialBatchViewModel={f.batchViewModel(it).also{batch=it}}) }
        } }
        compose.runOnIdle{nav.navigate(Screen.Settings.route)}
        compose.onNodeWithTag("laboratoryEntry").performScrollTo().performClick()
        compose.waitUntil(10_000){nav.currentDestination?.route==Screen.Laboratory.route}
        compose.onNodeWithTag("laboratoryBatch").performClick()
        compose.waitUntil(10_000){batch?.uiState?.value?.let{!it.loading && it.draft}==true}
        assertTrue(batch!!.uiState.value.selected.isEmpty())
        assertEquals(10,batch!!.uiState.value.rows.size)
        compose.onNodeWithContentDescription("返回").performClick()
        for ((label,route) in listOf("虚拟试衣" to Screen.TryOn.route,"我的模特" to Screen.PersonalModels.route,
            "识图录入" to Screen.ItemEdit.route,"任务中心" to Screen.ImageTasks.route,"API 配置" to Screen.AiConfiguration.route)) {
            compose.onNodeWithTag("laboratory").performScrollToNode(hasText(label));compose.onNodeWithText(label).performClick()
            compose.waitUntil(10_000){nav.currentDestination?.route==route}
            compose.runOnIdle{nav.popBackStack()}
        }
        compose.onNodeWithTag("laboratory").performScrollToNode(hasText("假发头模图"));compose.onNodeWithText("假发头模图").performClick()
        compose.onNodeWithText("示例全头假发").performClick()
        compose.waitUntil(10_000){nav.currentDestination?.route==Screen.Mannequin.route}
        assertEquals(0,f.serviceStarts)
        assertTrue(runBlocking{f.db.imageTaskDao().getAll().isEmpty()})
    }
}
