package com.lolita.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.ui.theme.skin.animation.LocalSkinScrollActivity
import com.lolita.app.ui.theme.skin.animation.SkinScrollActivity
import com.lolita.app.ui.theme.skin.animation.LocalIsListScrolling
import com.lolita.app.ui.theme.skin.animation.SkinBackgroundAnimation
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import com.lolita.app.ui.theme.skin.animation.SkinNavigationOverlay
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import androidx.compose.foundation.isSystemInDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.rememberHazeState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lolita.app.ui.screen.coordinate.CoordinateDetailScreen
import com.lolita.app.ui.screen.coordinate.CoordinateEditScreen
import com.lolita.app.ui.screen.price.PriceEditScreen
import com.lolita.app.ui.screen.price.PriceManageScreen
import com.lolita.app.ui.screen.price.PaymentManageScreen
import com.lolita.app.ui.screen.price.PaymentEditScreen
import com.lolita.app.ui.screen.item.ItemDetailScreen
import com.lolita.app.ui.screen.item.RecommendationScreen
import com.lolita.app.ui.screen.item.ItemEditScreen
import com.lolita.app.ui.screen.item.FilteredItemListScreen
import com.lolita.app.ui.screen.item.ItemListScreen
import com.lolita.app.ui.screen.outfit.OutfitLogListScreen
import com.lolita.app.ui.screen.outfit.OutfitLogDetailScreen
import com.lolita.app.ui.screen.outfit.OutfitLogEditScreen
import com.lolita.app.ui.screen.outfit.QuickOutfitLogScreen
import com.lolita.app.ui.screen.settings.BackupRestoreScreen
import com.lolita.app.ui.screen.settings.BrandManageScreen
import com.lolita.app.ui.screen.settings.CategoryManageScreen
import com.lolita.app.ui.screen.settings.StyleManageScreen
import com.lolita.app.ui.screen.settings.SeasonManageScreen
import com.lolita.app.ui.screen.settings.SettingsScreen
import com.lolita.app.ui.screen.settings.AppUpdateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lolita.app.ui.screen.mannequin.MannequinScreen
import com.lolita.app.ui.screen.mannequin.MannequinViewModel
import com.lolita.app.ui.screen.tasks.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.ui.screen.tryon.TryOnScreen
import com.lolita.app.ui.screen.personalmodel.*
import com.lolita.app.ui.screen.tryon.TryOnViewModel
import com.lolita.app.ui.screen.settings.AttributeManageScreen
import com.lolita.app.ui.screen.settings.ThemeSelectScreen
import com.lolita.app.ui.screen.`import`.TaobaoImportScreen
import com.lolita.app.ui.screen.`import`.TaobaoImportGuideScreen
import com.lolita.app.ui.screen.stats.StatsPageScreen
import com.lolita.app.ui.screen.item.LocationDetailScreen
import com.lolita.app.ui.screen.settings.LocationManageScreen
import com.lolita.app.ui.screen.settings.SourceManageScreen


interface BottomNavItem {
    val screen: Screen
    val iconKey: IconKey
    val label: String
}

data object BottomNavItems {
    val items = listOf(
        object : BottomNavItem {
            override val screen = Screen.ItemList
            override val iconKey = IconKey.Home
            override val label = "首页"
        },
        object : BottomNavItem {
            override val screen = Screen.OutfitLogList
            override val iconKey = IconKey.Outfit
            override val label = "穿搭"
        },
        object : BottomNavItem {
            override val screen = Screen.Stats
            override val iconKey = IconKey.Stats
            override val label = "统计"
        },
        object : BottomNavItem {
            override val screen = Screen.Settings
            override val iconKey = IconKey.Settings
            override val label = "个人"
        }
    )
}

val LocalHazeState = compositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LolitaNavHost(
    openQuickOutfit: Boolean = false,
    openImageTasks: Boolean = false,
    onImageTasksOpened: () -> Unit = {},
    onQuickOutfitOpened: () -> Unit = {},
    appUpdateViewModel: AppUpdateViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    createTryOnViewModel: (SavedStateHandle) -> TryOnViewModel = { TryOnViewModel(it,
        personalModels = AppModule.personalModelRepository().observe(),
        defaultPersonalModelId = AppModule.appPreferences().defaultPersonalModelId,
        materials = AppModule.itemTryOnMaterialRepository().materials) },
    createMannequinViewModel: (SavedStateHandle) -> MannequinViewModel = { MannequinViewModel(it) },
    createImageTaskViewModel: () -> ImageTaskViewModel = { ImageTaskViewModel(personalModels = AppModule.personalModelRepository().observe(), materials = AppModule.itemTryOnMaterialRepository().materials) },
    createPersonalModelViewModel: () -> PersonalModelViewModel = { PersonalModelViewModel() },
    createPersonalModelEditViewModel: (SavedStateHandle) -> PersonalModelEditViewModel = { PersonalModelEditViewModel(it) },
    createMaterialWorkspaceViewModel: (SavedStateHandle) -> com.lolita.app.ui.screen.material.MaterialWorkspaceViewModel = { com.lolita.app.ui.screen.material.MaterialWorkspaceViewModel(it) },
    createMaterialBatchViewModel: (SavedStateHandle) -> com.lolita.app.ui.screen.material.MaterialBatchViewModel = { com.lolita.app.ui.screen.material.MaterialBatchViewModel(it) },
    createLaboratoryViewModel: () -> com.lolita.app.ui.screen.settings.LaboratoryViewModel = { com.lolita.app.ui.screen.settings.LaboratoryViewModel() },
    imageTaskBarViewModel: ImageTaskBarViewModel = viewModel()
) {
    val activeEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(openQuickOutfit, activeEntry?.id) {
        if (openQuickOutfit && activeEntry != null) {
            navController.navigate(Screen.QuickOutfitLog.route) { launchSingleTop = true }
            onQuickOutfitOpened()
        }
    }
    LaunchedEffect(openImageTasks, activeEntry?.id) {
        if (openImageTasks && activeEntry != null) {
            navController.navigate(Screen.ImageTasks.route) { launchSingleTop = true }
            onImageTasksOpened()
        }
    }
    val items = BottomNavItems.items
    val globalTasks by imageTaskBarViewModel.tasks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor
    val hazeState = rememberHazeState()

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
            ImageTaskBar(globalTasks, onOpenTasks = {
                navController.navigate(Screen.ImageTasks.route) { launchSingleTop = true }
            })
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(with(LocalDensity.current) { 48.dp + MaterialTheme.typography.labelMedium.lineHeight.toDp() })
                    .then(
                        if (skin.navBarBlurEnabled) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint,
                                    tint = HazeTint(
                                        (if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint)
                                            .copy(alpha = skin.navBarBlurAlpha)
                                    ),
                                    blurRadius = 20.dp
                                )
                            )
                        } else {
                            Modifier
                        }
                    ),
                containerColor = if (skin.navBarBlurEnabled) {
                    (if (isSystemInDarkTheme()) skin.navBarBlurTintDark else skin.navBarBlurTint)
                        .copy(alpha = skin.navBarBlurAlpha)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = accent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true
                    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    NavigationBarItem(
                        icon = {
                            SkinIcon(item.iconKey, modifier = Modifier.size(26.dp), tint = tint)
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            indicatorColor = if (skin.skinType == SkinType.COUNTRY) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
            }
        }
    ) { paddingValues ->
        val navSpec = skin.animations.navigation
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val isNavigating = currentBackStackEntry != null && navController.previousBackStackEntry != null

        val scrollActivity = remember { SkinScrollActivity() }
        CompositionLocalProvider(
            LocalSkinScrollActivity provides scrollActivity,
            LocalIsListScrolling provides scrollActivity.isScrolling
        ) {
        Box(modifier = Modifier.hazeSource(state = hazeState)) {
            // Window-fixed illustration below the existing, scroll-aware ambient particles.
            SkinArtBackground(modifier = Modifier.fillMaxSize())
            // Bottom layer: ambient background animation
            SkinBackgroundAnimation(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            // Middle layer: main content
            NavHost(
                navController = navController,
                startDestination = Screen.ItemList.route,
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
                enterTransition = { navSpec.enterTransition },
                exitTransition = { navSpec.exitTransition },
                popEnterTransition = { navSpec.popEnterTransition },
                popExitTransition = { navSpec.popExitTransition }
            ) {
            // Item List
            composable(Screen.ItemList.route) {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                ItemListScreen(
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToEdit = { itemId ->
                        navController.navigate(Screen.ItemEdit.createRoute(itemId))
                    },
                    onAddItem = { family -> navController.navigate(Screen.ItemEdit.createRoute(defaultFamily = family)) },
                    onImportOrders = { navController.navigate(Screen.TaobaoImport.route) },
                    onImportProduct = { navController.navigate(Screen.TaobaoProductImport.route) },
                    onRestoreBackup = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToCoordinateDetail = { coordinateId ->
                        navController.navigate(Screen.CoordinateDetail.createRoute(coordinateId))
                    },
                    onNavigateToCoordinateAdd = {
                        navController.navigate(Screen.CoordinateEdit.createRoute(null))
                    },
                    onNavigateToCoordinateEdit = { coordinateId ->
                        navController.navigate(Screen.CoordinateEdit.createRoute(coordinateId))
                    },
                    onNavigateToQuickOutfit = {
                        navController.navigate(Screen.QuickOutfitLog.route)
                    },
                    onNavigateToLocationDetail = { locationId ->
                        navController.navigate(Screen.LocationDetail.createRoute(locationId))
                    },
                    onNavigateToFilteredList = { filterType, filterValue, title ->
                        navController.navigate(Screen.FilteredItemList.createRoute(filterType, filterValue, title))
                    }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Item Detail
            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                val material by remember(itemId) { AppModule.itemTryOnMaterialRepository().observe(itemId) }.collectAsState(initial = null)
                ItemDetailScreen(
                    itemId = itemId, currentMaterial = material,
                    onNavigateToMaterial = { id -> navController.navigate(Screen.MaterialBatch.createRoute(itemId=id)) },
                    onViewMaterial = { id -> navController.navigate(Screen.TryOnMaterial.createRoute(id)) },
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.ItemEdit.createRoute(it)) },
                    onNavigateToItem = { navController.navigate(Screen.ItemDetail.createRoute(it)) },
                    onCreateChild = { navController.navigate(Screen.ItemEdit.createRoute(parentId = it)) },
                    onNavigateToPriceManage = {
                        navController.navigate(Screen.PriceManage.createRoute(itemId))
                    },
                    onNavigateToParentPriceManage = { parentId ->
                        navController.navigate(Screen.PriceManage.createRoute(parentId))
                    },
                    onNavigateToRecommendation = { id ->
                        navController.navigate(Screen.Recommendation.createRoute(id))
                    },
                    onNavigateToTryOn = { id ->
                        navController.navigate(Screen.TryOn.createRoute(itemId = id))
                    },
                    onNavigateToMannequin = { id -> navController.navigate(Screen.Mannequin.createRoute(id)) },
                    onNavigateToLocation = { id -> navController.navigate(Screen.LocationDetail.createRoute(id)) },
                    onCopyToNew = { id -> navController.navigate(Screen.ItemEdit.createRoute(draftId = id)) }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Item Edit
            composable(
                route = Screen.ItemEdit.route,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("defaultStatus") { type = NavType.StringType; defaultValue = "" },
                    navArgument("parentId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("draftId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("defaultLocationId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("defaultStorageNodeId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("defaultFamily") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                val defaultStatus = backStackEntry.arguments?.getString("defaultStatus") ?: ""
                val productDraft = backStackEntry.savedStateHandle.get<String>("taobao_product_draft")
                val productToken = productDraft?.let { json -> runCatching {
                    com.google.gson.Gson().fromJson(json, com.lolita.app.data.file.TaobaoProductDraft::class.java).let { it.editorToken ?: it.importToken }
                }.getOrNull() }
                fun finishProductEditor(saved: Boolean) {
                    if (productToken != null && navController.previousBackStackEntry?.destination?.route == Screen.TaobaoProductImport.route) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("taobao_product_editor_result", "$productToken:${if (saved) "saved" else "discarded"}")
                    }
                    navController.popBackStack()
                }
                val itemEditorFactory = remember(backStackEntry) { viewModelFactory {
                    initializer { com.lolita.app.ui.screen.item.ItemEditViewModel(savedStateHandle = createSavedStateHandle()) }
                } }
                val itemEditor: com.lolita.app.ui.screen.item.ItemEditViewModel = viewModel(backStackEntry, factory = itemEditorFactory)
                ItemEditScreen(
                    itemId = if (itemId == 0L) null else itemId,
                    draftId = backStackEntry.arguments?.getString("draftId"), viewModel = itemEditor,
                    defaultStatus = defaultStatus,
                    importedDraftJson = productDraft,
                    defaultParentItemId = backStackEntry.arguments?.getLong("parentId")?.takeIf { it != 0L },
                    defaultLocationId = backStackEntry.arguments?.getLong("defaultLocationId")?.takeIf { it > 0 },
                    defaultStorageNodeId = backStackEntry.arguments?.getLong("defaultStorageNodeId")?.takeIf { it > 0 },
                    defaultFamily = backStackEntry.arguments?.getString("defaultFamily")?.let { name ->
                        com.lolita.app.domain.model.WardrobeFamily.entries.find { it.name == name }
                    },
                    onBack = { finishProductEditor(false) },
                    onSaveSuccess = { finishProductEditor(true) },
                    onConfigureApi = { navController.navigate(Screen.AiConfiguration.route) }
                )
            }

            // Price Manage
            composable(
                route = Screen.PriceManage.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                PriceManageScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onNavigateToItemEdit = { id ->
                        navController.navigate(Screen.ItemEdit.createRoute(id))
                    },
                    onNavigateToPriceEdit = { priceId ->
                        navController.navigate(Screen.PriceEdit.createRoute(itemId, priceId))
                    },
                    onNavigateToPaymentManage = { priceId ->
                        navController.navigate(Screen.PaymentManage.createRoute(priceId))
                    },
                    onNavigateToParentPriceManage = { parentId ->
                        navController.navigate(Screen.PriceManage.createRoute(parentId))
                    }
                )
            }

            // Price Edit
            composable(
                route = Screen.PriceEdit.route,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType },
                    navArgument("priceId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                val priceId = backStackEntry.arguments?.getLong("priceId") ?: 0L
                PriceEditScreen(
                    itemId = itemId,
                    priceId = if (priceId == 0L) null else priceId,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

            // Payment Manage
            composable(
                route = Screen.PaymentManage.route,
                arguments = listOf(navArgument("priceId") { type = NavType.LongType })
            ) { backStackEntry ->
                val priceId = backStackEntry.arguments?.getLong("priceId") ?: return@composable
                PaymentManageScreen(
                    priceId = priceId,
                    onBack = { navController.popBackStack() },
                    onNavigateToPaymentEdit = { paymentId ->
                        navController.navigate(Screen.PaymentEdit.createRoute(priceId, paymentId))
                    }
                )
            }
            // Payment Edit
            composable(
                route = Screen.PaymentEdit.route,
                arguments = listOf(
                    navArgument("priceId") { type = NavType.LongType },
                    navArgument("paymentId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) { backStackEntry ->
                val priceId = backStackEntry.arguments?.getLong("priceId") ?: return@composable
                val paymentId = backStackEntry.arguments?.getLong("paymentId") ?: 0L
                PaymentEditScreen(
                    priceId = priceId,
                    paymentId = if (paymentId == 0L) null else paymentId,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

            // Coordinate Detail
            composable(
                route = Screen.CoordinateDetail.route,
                arguments = listOf(navArgument("coordinateId") { type = NavType.LongType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val coordinateId = backStackEntry.arguments?.getLong("coordinateId") ?: return@composable
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                CoordinateDetailScreen(
                    coordinateId = coordinateId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.CoordinateEdit.createRoute(it)) },
                    onDelete = { navController.popBackStack() },
                    onNavigateToItem = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToTryOn = { id ->
                        navController.navigate(Screen.TryOn.createRoute(coordinateId = id))
                    }
                )
                } // LocalNavAnimatedVisibilityScope
            }
            // Coordinate Edit
            composable(
                route = Screen.CoordinateEdit.route,
                arguments = listOf(navArgument("coordinateId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val coordinateId = backStackEntry.arguments?.getLong("coordinateId") ?: 0L
                CoordinateEditScreen(
                    coordinateId = if (coordinateId == 0L) null else coordinateId,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

            // Outfit Log List
            composable(Screen.OutfitLogList.route) {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                OutfitLogListScreen(
                    onNavigateToDetail = { logId ->
                        navController.navigate(Screen.OutfitLogDetail.createRoute(logId))
                    },
                    onNavigateToEdit = { logId ->
                        navController.navigate(Screen.OutfitLogEdit.createRoute(logId))
                    }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Outfit Log Detail
            composable(
                route = Screen.OutfitLogDetail.route,
                arguments = listOf(navArgument("logId") { type = NavType.LongType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val logId = backStackEntry.arguments?.getLong("logId") ?: return@composable
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                OutfitLogDetailScreen(
                    logId = logId,
                    onBack = { navController.popBackStack() },
                    onNavigateToEdit = {
                        navController.navigate(Screen.OutfitLogEdit.createRoute(logId))
                    },
                    onNavigateToItem = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Outfit Log Edit
            composable(
                route = Screen.OutfitLogEdit.route,
                arguments = listOf(navArgument("logId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val logId = backStackEntry.arguments?.getLong("logId") ?: 0L
                OutfitLogEditScreen(
                    logId = if (logId == 0L) null else logId,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

            // Stats
            composable(Screen.Stats.route) {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                StatsPageScreen(
                    onNavigateToFilteredList = { filterType, filterValue, title ->
                        navController.navigate(Screen.FilteredItemList.createRoute(filterType, filterValue, title))
                    },
                    onNavigateToItemDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAttributeManage = { navController.navigate(Screen.AttributeManage.route) },
                    onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToTaobaoImport = { navController.navigate(Screen.TaobaoImport.route) },
                    onNavigateToThemeSelect = { navController.navigate(Screen.ThemeSelect.route) },
                    onNavigateToLaboratory = { navController.navigate(Screen.Laboratory.route) },
                    appUpdateViewModel = appUpdateViewModel
                )
            }

            composable(Screen.Laboratory.route) { entry ->
                val factory = remember(entry) { viewModelFactory { initializer { createLaboratoryViewModel() } } }
                val laboratory: com.lolita.app.ui.screen.settings.LaboratoryViewModel = viewModel(entry, factory = factory)
                com.lolita.app.ui.screen.settings.LaboratoryScreen(
                    onBack = { navController.popBackStack() },
                    onBatch = { navController.navigate(Screen.MaterialBatch.createRoute()) },
                    onTryOn = { navController.navigate(Screen.TryOn.createRoute()) },
                    onModels = { navController.navigate(Screen.PersonalModels.route) },
                    onWigHead = { navController.navigate(Screen.Mannequin.createRoute(it)) },
                    onRecognize = { navController.navigate(Screen.ItemEdit.createRoute()) },
                    onTasks = { navController.navigate(Screen.ImageTasks.route) },
                    onConfigure = { navController.navigate(Screen.AiConfiguration.route) }, viewModel = laboratory)
            }

            composable(Screen.AiConfiguration.route) {
                com.lolita.app.ui.screen.settings.AiConfigurationScreen(onBack = { navController.popBackStack() })
            }

            // Brand Manage
            composable(Screen.BrandManage.route) {
                BrandManageScreen(onBack = { navController.popBackStack() })
            }

            // Category Manage
            composable(Screen.CategoryManage.route) {
                CategoryManageScreen(onBack = { navController.popBackStack() })
            }

            // Style Manage
            composable(Screen.StyleManage.route) {
                StyleManageScreen(onBack = { navController.popBackStack() })
            }

            // Season Manage
            composable(Screen.SeasonManage.route) {
                SeasonManageScreen(onBack = { navController.popBackStack() })
            }

            // Location Manage
            composable(Screen.LocationManage.route) {
                LocationManageScreen(onBack = { navController.popBackStack() },
                    onLocationClick = { navController.navigate(Screen.LocationDetail.createRoute(it)) })
            }

            // Source Manage
            composable(Screen.SourceManage.route) {
                SourceManageScreen(onBack = { navController.popBackStack() })
            }

            // Attribute Manage
            composable(Screen.AttributeManage.route) {
                AttributeManageScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToBrand = { navController.navigate(Screen.BrandManage.route) },
                    onNavigateToCategory = { navController.navigate(Screen.CategoryManage.route) },
                    onNavigateToStyle = { navController.navigate(Screen.StyleManage.route) },
                    onNavigateToSeason = { navController.navigate(Screen.SeasonManage.route) },
                    onNavigateToLocation = { navController.navigate(Screen.LocationManage.route) },
                    onNavigateToSource = { navController.navigate(Screen.SourceManage.route) }
                )
            }

            // Theme Select
            composable(Screen.ThemeSelect.route) {
                ThemeSelectScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Backup & Restore
            composable(Screen.BackupRestore.route) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
            }


            composable(Screen.TaobaoProductImport.route) { backStackEntry ->
                val editorResult by backStackEntry.savedStateHandle.getStateFlow<String?>("taobao_product_editor_result", null).collectAsState()
                com.lolita.app.ui.screen.`import`.TaobaoProductScreen(
                    onBack = { navController.popBackStack() },
                    editorResult = editorResult,
                    onConsumeEditorResult = { backStackEntry.savedStateHandle["taobao_product_editor_result"] = null },
                    onEdit = { json ->
                        navController.navigate(Screen.ItemEdit.createRoute(defaultStatus = "WISHED"))
                        navController.currentBackStackEntry?.savedStateHandle?.set("taobao_product_draft", json)
                    }
                )
            }

            // Taobao Import
            composable(Screen.TaobaoImport.route) {
                TaobaoImportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToProduct = { navController.navigate(Screen.TaobaoProductImport.route) },
                    onNavigateToGuide = { navController.navigate(Screen.TaobaoImportGuide.route) }
                )
            }

            // Taobao Import Guide
            composable(Screen.TaobaoImportGuide.route) {
                TaobaoImportGuideScreen(
                    onBack = { navController.popBackStack() }
                )
            }


            // Location Detail
            composable(
                route = Screen.LocationDetail.route,
                arguments = listOf(navArgument("locationId") { type = NavType.LongType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val locationId = backStackEntry.arguments?.getLong("locationId") ?: return@composable
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                LocationDetailScreen(
                    locationId = locationId,
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate(Screen.ItemDetail.createRoute(itemId)) },
                    onAddItem = { roomId, nodeId -> navController.navigate(Screen.ItemEdit.createRoute(
                        defaultLocationId = roomId, defaultStorageNodeId = nodeId)) }
                )
                } // LocalNavAnimatedVisibilityScope
            }

            // Recommendation
            composable(
                route = Screen.Recommendation.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
                enterTransition = { fadeIn() + slideInHorizontally { it } },
                exitTransition = { fadeOut() + slideOutHorizontally { it } }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                com.lolita.app.ui.screen.look.LookBuilderScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onItem = { id -> navController.navigate(Screen.ItemDetail.createRoute(id)) },
                    onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate(Screen.CoordinateDetail.createRoute(id))
                    }
                )
            }

            composable(Screen.MaterialBatch.route,arguments=listOf(
                navArgument("batchId"){type=NavType.StringType;defaultValue=""},
                navArgument("itemId"){type=NavType.LongType;defaultValue=0L},
                navArgument("phase"){type=NavType.StringType;defaultValue=""}
            )){entry->
                val factory=remember(entry){viewModelFactory{initializer{createMaterialBatchViewModel(createSavedStateHandle())}}}
                com.lolita.app.ui.screen.material.MaterialBatchScreen(viewModel=viewModel(viewModelStoreOwner=entry,factory=factory),
                    onBack={navController.popBackStack()},onTasks={navController.navigate(Screen.ImageTasks.route)},
                    onConfigure={navController.navigate(Screen.AiConfiguration.route)},
                    onNewBatch={navController.navigate(Screen.MaterialBatch.createRoute()){popUpTo(Screen.MaterialBatch.route){inclusive=true}}},
                    onOpenTask={navController.navigate(Screen.ImageTaskDetail.createRoute(it))})
            }
            listOf(Screen.TryOnMaterial.route, Screen.AddAccessory.route).forEach { route ->
                composable(route, arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType },
                    navArgument("taskId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("baseResultId") { type = NavType.LongType; defaultValue = 0L }
                )) { entry ->
                    val factory = remember(entry) { viewModelFactory { initializer { createMaterialWorkspaceViewModel(createSavedStateHandle()) } } }
                    com.lolita.app.ui.screen.material.MaterialWorkspaceScreen(
                        viewModel = viewModel(viewModelStoreOwner = entry, factory = factory), addAccessory = route == Screen.AddAccessory.route,
                        onBack = { navController.popBackStack() }, onTasks = { navController.navigate(Screen.ImageTasks.route) },
                        onConfigure = { navController.navigate(Screen.AiConfiguration.route) },
                        onTask = { id -> navController.navigate(Screen.ImageTaskDetail.createRoute(id)) { popUpTo(route) { inclusive = true } } },
                        onNewMaterial = { id -> navController.navigate(Screen.TryOnMaterial.createRoute(id)) },
                        onFullTryOn = { id -> navController.navigate(Screen.TryOn.createRoute(itemId = id, useMaterial = true)) },
                        onAddAccessory = { id -> navController.navigate(Screen.AddAccessory.createRoute(id)) },
                        onModels = { navController.navigate(Screen.PersonalModels.route) })
                }
            }

            listOf(Screen.Mannequin.route, Screen.MannequinRetry.route).forEach { route ->
                composable(
                    route = route,
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType },
                        navArgument("retryTaskId") { type = NavType.StringType; defaultValue = "" })
                ) { entry ->
                    val factory = remember(entry) {
                        viewModelFactory { initializer { createMannequinViewModel(createSavedStateHandle()) } }
                    }
                    MannequinScreen(onNavigateBack = {
                        // A departing page may remain composed during an exit animation.
                        // Its completion must never pop a newer destination.
                        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                    },
                        viewModel = viewModel(viewModelStoreOwner = entry, factory = factory),
                        onNavigateToTasks = { navController.navigate(Screen.ImageTasks.route) },
                        onTaskSubmitted = { navController.navigate(Screen.ImageTaskDetail.createRoute(it)) },
                        onConfigureApi = { navController.navigate(Screen.AiConfiguration.route) })
                }
            }

            listOf(Screen.TryOn.route, Screen.TryOnRetry.route).forEach { route ->
                composable(
                    route = route,
                    arguments = listOf(
                        navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("coordinateId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("useMaterial") { type = NavType.BoolType; defaultValue = false },
                        navArgument("retryTaskId") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    val factory = remember(entry) {
                        viewModelFactory { initializer { createTryOnViewModel(createSavedStateHandle()) } }
                    }
                    TryOnScreen(
                        onNavigateBack = { navController.popBackStack() },
                        viewModel = viewModel(viewModelStoreOwner = entry, factory = factory),
                        onNavigateToTasks = { navController.navigate(Screen.ImageTasks.route) },
                        onTaskSubmitted = { navController.navigate(Screen.ImageTaskDetail.createRoute(it)) },
                        onManagePersonalModels = { navController.navigate(Screen.PersonalModels.route) },
                        onConfigureApi = { navController.navigate(Screen.AiConfiguration.route) },
                        onAddAccessory = { itemId, resultId -> navController.navigate(Screen.AddAccessory.createRoute(itemId, resultId)) }
                    )
                }
            }

            composable(Screen.PersonalModels.route) { entry ->
                val factory = remember(entry) { viewModelFactory { initializer { createPersonalModelViewModel() } } }
                PersonalModelScreen(onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(Screen.PersonalModelEdit.createRoute()) },
                    onAdjust = { navController.navigate(Screen.PersonalModelEdit.createRoute(modelId = it)) },
                    viewModel = viewModel(viewModelStoreOwner = entry, factory = factory))
            }
            composable(Screen.PersonalModelEdit.route, arguments = listOf(
                navArgument("modelId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("retryTaskId") { type = NavType.StringType; defaultValue = "" }
            )) { entry ->
                val factory = remember(entry) { viewModelFactory { initializer { createPersonalModelEditViewModel(createSavedStateHandle()) } } }
                PersonalModelEditScreen(onBack = { navController.popBackStack() }, onTask = { id ->
                    navController.navigate(Screen.ImageTaskDetail.createRoute(id)) {
                        popUpTo(Screen.PersonalModelEdit.route) { inclusive = true }
                    }
                }, viewModel = viewModel(viewModelStoreOwner = entry, factory = factory),
                    onConfigureApi = { navController.navigate(Screen.AiConfiguration.route) })
            }

            composable(Screen.ImageTasks.route) { entry ->
                val factory = remember(entry) { viewModelFactory { initializer { createImageTaskViewModel() } } }
                ImageTaskScreen(onNavigateBack = { navController.popBackStack() },
                    onOpenTask = { navController.navigate(Screen.ImageTaskDetail.createRoute(it)) },
                    onNewMaterialBatch={navController.navigate(Screen.MaterialBatch.createRoute())},
                    onOpenMaterialBatch={navController.navigate(Screen.MaterialBatch.createRoute(batchId=it))},
                    viewModel = viewModel(viewModelStoreOwner = entry, factory = factory))
            }
            composable(Screen.ImageTaskDetail.route, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { entry ->
                val factory = remember(entry) { viewModelFactory { initializer { createImageTaskViewModel() } } }
                ImageTaskDetailScreen(taskId = entry.arguments?.getString("taskId").orEmpty(),
                    onNavigateBack = { navController.popBackStack() }, onRetry = { task ->
                        val route = when (task.kind) {
                            ImageTaskKind.SINGLE_ITEM_MATERIAL -> if(task.batchId!=null)Screen.MaterialBatch.createRoute(task.batchId,phase=if(com.lolita.app.domain.model.ImageTaskRequest.decode(task.requestJson).materialSpec?.stage==com.lolita.app.domain.model.MaterialStage.FRONT_VIEW)"INPUTS" else "LAYERS") else Screen.TryOnMaterial.createRoute(task.garmentId ?: 0L, task.id)
                            ImageTaskKind.TRY_ON -> {
                                val base = runCatching { com.lolita.app.domain.model.ImageTaskRequest.decode(task.requestJson).accessoryBase }.getOrNull()
                                if (base != null) Screen.AddAccessory.createRoute(task.garmentId ?: 0L, base.id.takeIf { base.type == com.lolita.app.domain.model.AccessoryBaseType.TRY_ON_RESULT }, task.id)
                                else Screen.TryOnRetry.createRoute(task.id)
                            }
                            ImageTaskKind.MANNEQUIN -> Screen.MannequinRetry.createRoute(task.garmentId ?: 0L, task.id)
                            ImageTaskKind.PERSONAL_MODEL -> Screen.PersonalModelEdit.createRoute(retryTaskId = task.id)
                        }
                        navController.navigate(route)
                    }, viewModel = viewModel(viewModelStoreOwner = entry, factory = factory),
                    onOpenPersonalModels = { navController.navigate(Screen.PersonalModels.route) },
                    onOpenMaterial = { task -> navController.navigate(if(task.batchId!=null)Screen.MaterialBatch.createRoute(task.batchId) else Screen.TryOnMaterial.createRoute(task.garmentId ?: 0L, task.id)) })
            }

            // Quick Outfit Log
            composable(
                route = Screen.QuickOutfitLog.route,
                enterTransition = { fadeIn() + slideInHorizontally { it } },
                exitTransition = { fadeOut() + slideOutHorizontally { it } }
            ) {
                QuickOutfitLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Filtered Item List
            composable(
                route = Screen.FilteredItemList.route,
                arguments = listOf(
                    navArgument("filterType") { type = NavType.StringType; defaultValue = "" },
                    navArgument("filterValue") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val filterType = backStackEntry.arguments?.getString("filterType") ?: ""
                val filterValue = backStackEntry.arguments?.getString("filterValue") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                FilteredItemListScreen(
                    title = title,
                    filterType = filterType,
                    filterValue = filterValue,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onAddItem = { family -> navController.navigate(Screen.ItemEdit.createRoute(defaultFamily = family)) }
                )
                } // LocalNavAnimatedVisibilityScope
            }
        }

            // Top layer: navigation transition overlay
            SkinNavigationOverlay(
                isTransitioning = isNavigating,
                skinType = skin.skinType
            )
        }
        }
    }
    }
    } // LocalSharedTransitionScope
    } // SharedTransitionLayout
}
