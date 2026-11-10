package com.lolita.app.ui.screen.square

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.di.AppModule
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.navigation.Screen
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.FileInputStream

/** Full production NavHost. Must only run on this task's fresh, dedicated AVD. */
class SquareBrowseNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private fun <T> io(block: suspend () -> T) = runBlocking(Dispatchers.IO) { block() }
    private var baseline = emptySet<Long>()
    private var permitted = false
    private var activity: androidx.activity.ComponentActivity? = null
    @Before fun setup() {
        val fd = inst.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        val name = FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }; fd.close()
        Assume.assumeTrue("Only the task's dedicated AVD", name.trim() in setOf("Lolita_Square_Browse_20261106", "Lolita_Square_Polish_20261110"))
        baseline = io { AppModule.database().itemDao().getAllItemsList().map { it.id }.toSet() }; permitted = true
        val file = File(context.cacheDir, "browse-public-fixture.zip")
        if (!file.exists()) {
            val input = inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-browse.zip")
            FileInputStream(input.fileDescriptor).use { source -> file.outputStream().use { source.copyTo(it) } }; input.close()
        }
        io { val r = AppModule.squareRepository(); r.load(); val preview = r.preview(Uri.fromFile(file)) {}; r.apply(preview) }
    }
    @After fun cleanup() {
        if (!permitted) return
        inst.runOnMainSync { activity?.viewModelStore?.clear() }
        io { AppModule.squareRepository().load() }
        io { AppModule.database().itemDao().getAllItemsList().filter { it.id !in baseline && it.importToken?.startsWith("square:wish:") == true }.forEach { AppModule.itemRepository().deleteItem(it) } }
    }
    private fun shot(name: String) {
        compose.waitForIdle(); inst.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val dir = File(context.filesDir, "square-browse-native").apply { mkdirs() }
        val image = inst.uiAutomation.takeScreenshot(); File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    private fun back() { inst.uiAutomation.executeShellCommand("input keyevent 4").close() }
    @Test fun realNavigationDirectWishGroupExistingWishlistAndReturn() {
        lateinit var nav: NavHostController
        compose.setContent { LolitaTheme(SkinType.CLASSIC, false) {
            val controller = rememberNavController(); val view = androidx.compose.ui.platform.LocalView.current
            SideEffect { nav = controller; activity = view.context as? androidx.activity.ComponentActivity }
            val active = com.lolita.app.ui.theme.LolitaSkin.current
            val still = remember(active) { active.copy(topBarBlurEnabled=false,navBarBlurEnabled=false,
                animations=object:com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider by active.animations {
                    override val ambientAnimation=active.animations.ambientAnimation.copy(backgroundEnabled=false)
                }) }
            CompositionLocalProvider(com.lolita.app.ui.theme.LocalLolitaSkin provides still) { LolitaNavHost(navController = controller) }
        } }
        compose.onNode(isSelectable() and hasText("广场")).performClick()
        compose.onNodeWithTag("square-feed").performScrollToNode(hasTestTag("square-event-dorothy-cotton"))
        shot("app-feed")
        compose.onNodeWithTag("square-event-dorothy-cotton").performClick()
        compose.onNodeWithTag("square-product").assertExists(); shot("app-product")
        compose.onNodeWithTag("square-size:L").performScrollTo().performClick()
        compose.onNodeWithTag("square-color:黑色").performScrollTo().performClick()
        compose.onNodeWithTag("square-add-wish").performClick()
        compose.waitUntil(15000) { io { AppModule.database().itemDao().getAllItemsList().any { it.id !in baseline && it.size == "L" && it.colors == listOf("黑色") } } }
        compose.onNodeWithTag("square-add-wish").assertTextContains("已在心愿", substring = true).performClick()
        compose.waitUntil(10000) { nav.currentDestination?.route == Screen.ItemDetail.route }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("square-item-members", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("square-item-members", useUnmergedTree = true).performScrollTo().assertIsDisplayed(); shot("app-wish-parameters")
        back(); compose.waitUntil(10000) { nav.currentDestination?.route == Screen.Square.route }
        compose.onNodeWithTag("square-color:黑色").assertIsSelected()
        compose.onNodeWithTag("square-size:L").assertIsSelected()
        compose.onNodeWithText("已在心愿 · 查看").assertExists()
        compose.onNodeWithTag("square-product").performScrollToNode(hasTestTag("square-product-images"))
        compose.onNodeWithTag("square-product-images").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("关闭图片预览").assertIsDisplayed(); shot("app-original-image")
        compose.onNodeWithContentDescription("关闭图片预览").performClick()
        compose.onNodeWithTag("square-color:蓝色").performScrollTo().performClick()
        compose.onNodeWithText("选多件").performClick()
        compose.onNodeWithTag("square-wish-options").performScrollToNode(hasTestTag("square-wish-part-KC"))
        compose.onNodeWithTag("square-wish-part-KC").performClick()
        compose.onNodeWithTag("square-wish-options").performScrollToNode(hasText("合为一件"))
        compose.onNodeWithText("合为一件").performClick(); shot("app-group-selection")
        compose.onNodeWithTag("square-save-wishes").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("查看心愿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看心愿").assertExists(); shot("app-group-saved")
        compose.onNodeWithText("查看心愿").performClick()
        compose.waitUntil(10000) { nav.currentDestination?.route == Screen.Wishlist.route }
        compose.onNode(isSelectable() and hasText("心愿")).assertIsSelected(); shot("app-wishlist")
        back(); compose.waitUntil(10000) { nav.currentDestination?.route == Screen.Square.route }
        compose.onNodeWithTag("square-color:蓝色").assertIsSelected(); compose.onNodeWithTag("square-size:L").assertIsSelected()
        val group = io { val source = AppModule.database().orderImportDao().sources().first { record ->
            if(record.kind!="SQUARE_WISH")false else {
                val s=com.lolita.app.data.square.SquareWishSnapshot.decode(record.snapshotJson,record.imagePaths.size)
                s.grouped && s.members.size==2 && s.members.any{it.partKey=="JSK"&&it.color=="蓝色"&&it.size=="L"} && s.members.any{it.partKey=="KC"&&it.size.isEmpty()}
            }
        }; AppModule.database().itemDao().getItemById(source.itemId)!! }
        compose.runOnIdle { nav.navigate(Screen.ItemDetail.createRoute(group.id)) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("square-item-members", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("square-item-members", useUnmergedTree = true).performScrollTo().assertIsDisplayed(); shot("app-group-details")
        assertNull(group.size); assertTrue(group.colors.isEmpty())
        val source = io { AppModule.database().orderImportDao().sources().single { it.itemId == group.id } }
        val snapshot = com.lolita.app.data.square.SquareWishSnapshot.decode(source.snapshotJson, source.imagePaths.size)
        assertEquals("L", snapshot.members.first { it.partKey == "JSK" }.size)
        assertEquals("", snapshot.members.first { it.partKey == "KC" }.size)
    }
}
