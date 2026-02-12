package com.lolita.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lolita.app.ui.screen.coordinate.CoordinateListScreen
import com.lolita.app.ui.screen.item.ItemDetailScreen
import com.lolita.app.ui.screen.item.ItemEditScreen
import com.lolita.app.ui.screen.item.ItemListScreen
import com.lolita.app.ui.screen.item.WishlistScreen
import com.lolita.app.ui.screen.outfit.OutfitLogListScreen
import com.lolita.app.ui.screen.search.SearchScreen
import com.lolita.app.ui.screen.settings.SettingsScreen
import com.lolita.app.ui.screen.stats.StatsScreen

sealed interface BottomNavItem {
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
            override val screen = Screen.Search
            override val icon = Icons.Filled.Search
            override val label = "搜索"
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

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.ItemList.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Item List Screen
            composable(Screen.ItemList.route) {
                ItemListScreen(
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onNavigateToEdit = { itemId ->
                        navController.navigate(Screen.ItemEdit.createRoute(itemId))
                    },
                    onNavigateToWishlist = {
                        navController.navigate(Screen.Wishlist.route)
                    }
                )
            }

            // Item Detail Screen
            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { editItemId ->
                        navController.navigate(Screen.ItemEdit.createRoute(editItemId))
                    }
                )
            }

            // Item Edit Screen
            composable(
                route = Screen.ItemEdit.route,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                ItemEditScreen(
                    itemId = if (itemId == 0L) null else itemId,
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

            // Wishlist Screen
            composable(Screen.Wishlist.route) {
                WishlistScreen(
                    onNavigateToDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    }
                )
            }

            // Coordinate List Screen
            composable(Screen.CoordinateList.route) {
                CoordinateListScreen(onNavigateToDetail = { /* TODO */ })
            }

            // Outfit Log List Screen
            composable(Screen.OutfitLogList.route) {
                OutfitLogListScreen(onNavigateToDetail = { /* TODO */ })
            }

            // Search Screen
            composable(Screen.Search.route) {
                SearchScreen(onNavigateToItem = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                })
            }

            // Stats Screen
            composable(Screen.Stats.route) {
                StatsScreen()
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToBrand = {
                        navController.navigate(Screen.BrandManage.route)
                    },
                    onNavigateToCategory = {
                        navController.navigate(Screen.CategoryManage.route)
                    }
                )
            }

            // Brand Manage Screen
            composable(Screen.BrandManage.route) {
                Text("品牌管理 - 开发中")
            }

            // Category Manage Screen
            composable(Screen.CategoryManage.route) {
                Text("类型管理 - 开发中")
            }
        }
    }
}
