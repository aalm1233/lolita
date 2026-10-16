package com.lolita.app.ui.screen.tasks

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real NavController bundles from the exact 2.50 patterns; only route/draft restoration is claimed. */
@RunWith(AndroidJUnit4::class)
class ImageTaskUpgradeNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stores = mutableListOf<ViewModelStore>()
    private val selectionJson = """[{"itemId":11,"name":"旧版用户搭配草稿","imagePath":"/preserved-source.png","role":"MAIN"}]"""
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }
    private fun controller(current: Boolean, saved: Bundle? = null): NavHostController =
        NavHostController(instrumentation.targetContext).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setViewModelStore(ViewModelStore().also { stores += it })
            setLifecycleOwner(Owner())
            saved?.let(::restoreState)
            graph = createGraph(startDestination = Screen.ItemList.route) {
                composable(Screen.ItemList.route) {}
                composable(if (current) Screen.Mannequin.route else "mannequin/{itemId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType })) {}
                composable(if (current) Screen.TryOn.route else "virtual_try_on?itemId={itemId}&coordinateId={coordinateId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("coordinateId") { type = NavType.LongType; defaultValue = 0L })) {}
            }
        }
    @After fun cleanup() { instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) } }

    @Test fun oldMannequinBackStackRestoresSourceInstructionsAndLegacyPreviewKeys() = runBlocking {
        withContext(Dispatchers.Main) {
            val old = controller(current = false)
            old.navigate("mannequin/11")
            old.currentBackStackEntry!!.savedStateHandle.apply {
                set("mannequinSource", "/preserved-source.png")
                set("mannequinInstructions", "旧版用户已编辑说明")
                set("mannequinPreview", "/preserved-legacy-preview.png")
                set("mannequinPreviewItem", 11L)
            }
            val restored = controller(current = true, saved = requireNotNull(old.saveState()))
            val entry = requireNotNull(restored.currentBackStackEntry)
            assertEquals(Screen.Mannequin.route, entry.destination.route)
            assertEquals(11L, entry.arguments!!.getLong("itemId"))
            assertEquals("/preserved-source.png", entry.savedStateHandle.get<String>("mannequinSource"))
            assertEquals("旧版用户已编辑说明", entry.savedStateHandle.get<String>("mannequinInstructions"))
            assertEquals("/preserved-legacy-preview.png", entry.savedStateHandle.get<String>("mannequinPreview"))
            assertEquals(11L, entry.savedStateHandle.get<Long>("mannequinPreviewItem"))
        }
    }
    @Test fun oldTryOnBackStackRestoresItsEditedSelectionAndEntryFlag() = runBlocking {
        withContext(Dispatchers.Main) {
            val old = controller(current = false)
            old.navigate("virtual_try_on?itemId=11&coordinateId=0")
            old.currentBackStackEntry!!.savedStateHandle.apply {
                set("tryOnSelections", selectionJson)
                set("tryOnEntryConsumed", true)
            }
            val restored = controller(current = true, saved = requireNotNull(old.saveState()))
            val entry = requireNotNull(restored.currentBackStackEntry)
            assertEquals(Screen.TryOn.route, entry.destination.route)
            assertEquals(11L, entry.arguments!!.getLong("itemId"))
            assertEquals(selectionJson, entry.savedStateHandle.get<String>("tryOnSelections"))
            assertEquals(true, entry.savedStateHandle.get<Boolean>("tryOnEntryConsumed"))
        }
    }
}
