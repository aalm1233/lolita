package com.lolita.app.ui.screen.tasks

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.navigation.*
import com.lolita.app.ui.screen.tryon.TryOnViewModel
import com.lolita.app.ui.screen.personalmodel.PersonalModelEditViewModel
import com.lolita.app.ui.screen.mannequin.MannequinViewModel
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.animation.SkinAnimationProvider
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule

/** Controlled durable flow models work owned outside either navigation entry. No network or real wardrobe writes. */
open class ImageTaskNavigationFixture {
    @get:Rule val compose = createComposeRule()
    lateinit var controller: NavHostController
    val tasks = MutableStateFlow<List<ImageTask>>(emptyList())
    val quick = mutableStateOf(false)
    val openTasks = mutableStateOf(false)
    var opened = 0
    var submissions = 0
    private var generatorRoute = Screen.TryOn.route
    val item = Item(id = 11, name = "导航测试裙", brandId = 1, categoryId = 1, description = "", status = ItemStatus.OWNED, imageUrls = listOf("/source.png"))
    fun start(mannequin: Boolean) {
        generatorRoute = if (mannequin) Screen.Mannequin.route else Screen.TryOn.route
        compose.setContent {
            LolitaTheme {
                val skin = LolitaSkin.current
                val still = remember(skin) { skin.copy(topBarBlurEnabled = false, navBarBlurEnabled = false,
                    animations = object : SkinAnimationProvider by skin.animations {
                        override val ambientAnimation = skin.animations.ambientAnimation.copy(backgroundEnabled = false)
                    }) }
                val nav = rememberNavController(); SideEffect { controller = nav }
                CompositionLocalProvider(LocalLolitaSkin provides still) {
                    LolitaNavHost(navController = nav, openQuickOutfit = quick.value, onQuickOutfitOpened = { quick.value = false },
                        imageTaskBarViewModel = remember { ImageTaskBarViewModel(tasks) },
                        openImageTasks = openTasks.value, onImageTasksOpened = { opened++; openTasks.value = false },
                        createTryOnViewModel = { handle -> TryOnViewModel(handle, items = flowOf(listOf(item)), categories = flowOf(listOf(Category(id = 1, name = "OP", categorySemantic = CategorySemantic.OP))), results = flowOf(emptyList()),
                            readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = tasks,
                            readRetryTask = { null }, submitTask = { _, provider, _ -> submit(ImageTaskKind.TRY_ON, provider) }, renameResult = { _, _ -> }, deleteResult = {}) },
                        createMannequinViewModel = { handle -> MannequinViewModel(handle, item = flowOf(item), categories = flowOf(listOf(Category(id = 1, name = "OP", categorySemantic = CategorySemantic.OP))),
                            readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, selectService = {}, serviceChanges = flowOf(0), saveApiKey = { _, _ -> }, clearApiKey = {}, tasks = tasks,
                            readRetryTask = { null }, submitTask = { _, provider -> submit(ImageTaskKind.MANNEQUIN, provider) }) },
                        createImageTaskViewModel = { ImageTaskViewModel(tasks, flowOf(listOf(item)), flowOf(emptyList()), {}, {}, {}, {}) },
                        createPersonalModelEditViewModel = { handle -> PersonalModelEditViewModel(handle,
                            readDraft = { null }, saveDraft = {}, deleteDraft = {}, readModel = { null },
                            readRetryTask = { id -> tasks.value.firstOrNull { it.id == id } },
                            readService = { ImageServiceStatus(ImageGenerationProvider.OPENAI, true) }, serviceChanges = flowOf(0),
                            selectService = {}, saveApiKey = { _, _ -> }, clearApiKey = {},
                            submitTask = { _, _, _ -> error("导航测试不得生成") }, imageReadable = { true },
                            copyPhoto = { _, _ -> error("导航测试不得拷贝") }, deleteImage = {}) })
                }
            }
        }
        compose.runOnIdle { controller.navigate(Screen.ItemDetail.createRoute(11)) }
        compose.runOnIdle { controller.navigate(if (mannequin) Screen.Mannequin.createRoute(11) else Screen.TryOn.createRoute(itemId = 11)) }
        compose.onNodeWithTag("globalImageTaskBar").assertDoesNotExist()
        compose.onNodeWithText(if (mannequin) "生成正面人台图" else "生成试衣图").assertIsEnabled().performClick()
        assertEquals(0, submissions)
        compose.onNodeWithText("确认并加入任务").performClick()
        compose.waitUntil(10_000) { submissions == 1 && tasks.value.size == 1 }
    }
    private fun submit(kind: ImageTaskKind, provider: ImageGenerationProvider): String {
        submissions++
        tasks.value = listOf(ImageTask("00000000-0000-0000-0000-000000000001", kind, provider, "受控任务",
            ImageTaskRequest(listOf(ImageTaskGarment(11, item.name, TryOnRole.MAIN))).encode(), item.imageUrls,
            status = ImageTaskStatus.GENERATING, requestSent = true))
        return tasks.value.single().id
    }
    fun allBottomTabsRemainAvailable() {
        listOf("穿搭" to Screen.OutfitLogList.route, "统计" to Screen.Stats.route, "个人" to Screen.Settings.route, "首页" to generatorRoute).forEach { (text, route) ->
            compose.onNodeWithText(text).assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(route, controller.currentDestination?.route) }
        }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
        assertEquals(1, submissions)
    }
    fun quickOutfitDoesNotWaitForGeneration() {
        compose.runOnIdle { quick.value = true }
        compose.waitUntil(10_000) { controller.currentDestination?.route == Screen.QuickOutfitLog.route && !quick.value }
        assertEquals(ImageTaskStatus.GENERATING, tasks.value.single().status)
    }
}
