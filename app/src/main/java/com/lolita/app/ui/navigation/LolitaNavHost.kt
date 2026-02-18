package com.lolita.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lolita.app.ui.theme.LolitaSkin
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
import com.lolita.app.ui.screen.`import`.TaobaoImportScreen
import com.lolita.app.ui.screen.stats.StatsPageScreen

interface BottomNavItem {
    val screen: Screen
    val icon: ImageVector
    val label: String
}

data object BottomNavItems {
    val items = listOf(
        object : BottomNavItem {
            override val screen = Screen.ItemList
            override val icon = Icons.Filled.Home
            override val label = "服饰"
        },
        object : BottomNavItem {
            override val screen = Screen.Wishlist
            override val icon = Icons.Filled.Favorite
            override val label = "愿望单"
        },
        object : BottomNavItem {
            override val screen = Screen.OutfitLogList
            override val icon = Icons.Filled.DateRange
            override val label = "穿搭"
        },
        object : BottomNavItem {
            override val screen = Screen.Stats
            override val icon = Icons.Filled.Info
            override val label = "统计"
        },
        object : BottomNavItem {
            override val screen = Screen.Settings
            override val icon = Icons.Filled.Settings
            override val label = "设置"
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
                modifier = Modifier.height(64.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = accent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
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
        NavHost(
            navController = navController,
            startDestination = Screen.ItemList.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn() + slideInHorizontally { it / 4 } },
            exitTransition = { fadeOut() + slideOutHorizontally { -it / 4 } },
            popEnterTransition = { fadeIn() + slideInHorizontally { -it / 4 } },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 4 } }
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
                    onDelete = { navController.popBackStack() }
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
                StatsPageScreen()
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToBrand = { navController.navigate(Screen.BrandManage.route) },
                    onNavigateToCategory = { navController.navigate(Screen.CategoryManage.route) },
                    onNavigateToStyle = { navController.navigate(Screen.StyleManage.route) },
                    onNavigateToSeason = { navController.navigate(Screen.SeasonManage.route) },
                    onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToTaobaoImport = { navController.navigate(Screen.TaobaoImport.route) }
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
        }
    }
}
