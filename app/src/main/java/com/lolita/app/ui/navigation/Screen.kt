package com.lolita.app.ui.navigation

sealed interface Screen {
    val route: String

    data object ItemList : Screen {
        override val route = "item_list"
    }

    data object ItemDetail : Screen {
        override val route = "item_detail/{itemId}"
        fun createRoute(itemId: Long) = "item_detail/$itemId"
    }

    data object ItemEdit : Screen {
        override val route = "item_edit/{itemId}"
        fun createRoute(itemId: Long? = null) = if (itemId != null) "item_edit/$itemId" else "item_edit/0"
    }

    data object Wishlist : Screen {
        override val route = "wishlist"
    }

    data object CoordinateList : Screen {
        override val route = "coordinate_list"
    }

    data object OutfitLogList : Screen {
        override val route = "outfit_log_list"
    }

    data object Search : Screen {
        override val route = "search"
    }

    data object Stats : Screen {
        override val route = "stats"
    }

    data object Settings : Screen {
        override val route = "settings"
    }

    data object BrandManage : Screen {
        override val route = "brand_manage"
    }

    data object CategoryManage : Screen {
        override val route = "category_manage"
    }
}
