package com.lolita.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.LocalIsListScrolling
import com.lolita.app.ui.theme.skin.animation.SkinBackgroundAnimation
import com.lolita.app.ui.theme.skin.animation.SkinNavigationOverlay
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.lolita.app.ui.screen.item.WishlistScreen
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
import com.lolita.app.ui.screen.settings.AttributeManageScreen
import com.lolita.app.ui.screen.settings.ThemeSelectScreen
import com.lolita.app.ui.screen.`import`.TaobaoImportScreen
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
            override val screen = Screen.Wishlist
            override val iconKey = IconKey.Wishlist
            override val label = "愿望单"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LolitaNavHost() {
    val navController = rememberNavController()
    val items = BottomNavItems.items
    val snackbarHostState = remember { SnackbarHostState() }

    val skin = LolitaSkin.current
    val accent = if (isSystemInDarkTheme()) skin.accentColorDark else skin.accentColor

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(52.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = accent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            val tint = if (currentDestination?.hierarchy?.any {
                                    it.route == item.screen.route
                                } == true) accent else Color.Gray
                            SkinIcon(item.iconKey, tint = tint)
                        },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true,
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
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        val navSpec = skin.animations.navigation
        val navBackStackEntryForOverlay by navController.currentBackStackEntryAsState()
        val isNavigating = navBackStackEntryForOverlay != null

        val isListScrolling = remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalIsListScrolling provides isListScrolling) {
        Box {
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
                modifier = Modifier.padding(paddingValues),
                enterTransition = { navSpec.enterTransition },
                exitTransition = { navSpec.exitTransition },
                popEnterTransition = { navSpec.popEnterTransition },
                popExitTransition = { navSpec.popExitTransition }
            ) {
            // Item List
            composable(Screen.ItemList.route) {
                ItemListScreen(
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToEdit = { itemId ->
                        navController.navigate(Screen.ItemEdit.createRoute(itemId))
                    },
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
            }

            // Item Detail
            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.ItemEdit.createRoute(it)) },
                    onNavigateToPriceManage = {
                        navController.navigate(Screen.PriceManage.createRoute(itemId))
                    },
                    onNavigateToRecommendation = { id ->
                        navController.navigate(Screen.Recommendation.createRoute(id))
                    }
                )
            }

            // Item Edit
            composable(
                route = Screen.ItemEdit.route,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("defaultStatus") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                val defaultStatus = backStackEntry.arguments?.getString("defaultStatus") ?: ""
                ItemEditScreen(
                    itemId = if (itemId == 0L) null else itemId,
                    defaultStatus = defaultStatus,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
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
                    onNavigateToPriceEdit = { priceId ->
                        navController.navigate(Screen.PriceEdit.createRoute(itemId, priceId))
                    },
                    onNavigateToPaymentManage = { priceId ->
                        navController.navigate(Screen.PaymentManage.createRoute(priceId))
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

            // Wishlist
            composable(Screen.Wishlist.route) {
                WishlistScreen(
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToEdit = { itemId ->
                        navController.navigate(Screen.ItemEdit.createRoute(itemId, defaultStatus = "WISHED"))
                    }
                )
            }

            // Coordinate Detail
            composable(
                route = Screen.CoordinateDetail.route,
                arguments = listOf(navArgument("coordinateId") { type = NavType.LongType })
            ) { backStackEntry ->
                val coordinateId = backStackEntry.arguments?.getLong("coordinateId") ?: return@composable
                CoordinateDetailScreen(
                    coordinateId = coordinateId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.CoordinateEdit.createRoute(it)) },
                    onDelete = { navController.popBackStack() },
                    onNavigateToItem = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
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
                OutfitLogListScreen(
                    onNavigateToDetail = { logId ->
                        navController.navigate(Screen.OutfitLogDetail.createRoute(logId))
                    },
                    onNavigateToEdit = { logId ->
                        navController.navigate(Screen.OutfitLogEdit.createRoute(logId))
                    }
                )
            }

            // Outfit Log Detail
            composable(
                route = Screen.OutfitLogDetail.route,
                arguments = listOf(navArgument("logId") { type = NavType.LongType })
            ) { backStackEntry ->
                val logId = backStackEntry.arguments?.getLong("logId") ?: return@composable
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
                StatsPageScreen(
                    onNavigateToFilteredList = { filterType, filterValue, title ->
                        navController.navigate(Screen.FilteredItemList.createRoute(filterType, filterValue, title))
                    },
                    onNavigateToItemDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAttributeManage = { navController.navigate(Screen.AttributeManage.route) },
                    onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToTaobaoImport = { navController.navigate(Screen.TaobaoImport.route) },
                    onNavigateToThemeSelect = { navController.navigate(Screen.ThemeSelect.route) }
                )
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
                LocationManageScreen(onBack = { navController.popBackStack() })
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
                ThemeSelectScreen(onBack = { navController.popBackStack() })
            }

            // Backup & Restore
            composable(Screen.BackupRestore.route) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
            }

            // Taobao Import
            composable(Screen.TaobaoImport.route) {
                TaobaoImportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { _ ->
                        // Navigate back to item list after import
                        navController.popBackStack()
                    }
                )
            }

            // Location Detail
            composable(
                route = Screen.LocationDetail.route,
                arguments = listOf(navArgument("locationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val locationId = backStackEntry.arguments?.getLong("locationId") ?: return@composable
                LocationDetailScreen(
                    locationId = locationId,
                    onBack = { navController.popBackStack() },
                    onItemClick = { itemId -> navController.navigate(Screen.ItemDetail.createRoute(itemId)) }
                )
            }

            // Recommendation
            composable(
                route = Screen.Recommendation.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
                enterTransition = { fadeIn() + slideInHorizontally { it } },
                exitTransition = { fadeOut() + slideOutHorizontally { it } }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                RecommendationScreen(
                    itemId = itemId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToItem = { id -> navController.navigate(Screen.ItemDetail.createRoute(id)) }
                )
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
                FilteredItemListScreen(
                    title = title,
                    filterType = filterType,
                    filterValue = filterValue,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
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
