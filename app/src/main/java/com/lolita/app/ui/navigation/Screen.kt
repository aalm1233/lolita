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
        override val route = "item_edit/{itemId}?defaultStatus={defaultStatus}"
        fun createRoute(itemId: Long? = null, defaultStatus: String? = null): String {
            val id = itemId ?: 0L
            return if (defaultStatus != null) "item_edit/$id?defaultStatus=$defaultStatus"
            else "item_edit/$id"
        }
    }

    data object PriceManage : Screen {
        override val route = "price_manage/{itemId}"
        fun createRoute(itemId: Long) = "price_manage/$itemId"
    }

    data object PriceEdit : Screen {
        override val route = "price_edit/{itemId}/{priceId}"
        fun createRoute(itemId: Long, priceId: Long? = null) = if (priceId != null) "price_edit/$itemId/$priceId" else "price_edit/$itemId/0"
    }

    data object PaymentManage : Screen {
        override val route = "payment_manage/{priceId}"
        fun createRoute(priceId: Long) = "payment_manage/$priceId"
    }

    data object PaymentEdit : Screen {
        override val route = "payment_edit/{priceId}/{paymentId}"
        fun createRoute(priceId: Long, paymentId: Long? = null) = if (paymentId != null) "payment_edit/$priceId/$paymentId" else "payment_edit/$priceId/0"
    }

    data object Wishlist : Screen {
        override val route = "wishlist"
    }

    data object CoordinateDetail : Screen {
        override val route = "coordinate_detail/{coordinateId}"
        fun createRoute(coordinateId: Long) = "coordinate_detail/$coordinateId"
    }

    data object CoordinateEdit : Screen {
        override val route = "coordinate_edit/{coordinateId}"
        fun createRoute(coordinateId: Long? = null) = if (coordinateId != null) "coordinate_edit/$coordinateId" else "coordinate_edit/0"
    }

    data object OutfitLogList : Screen {
        override val route = "outfit_log_list"
    }

    data object OutfitLogDetail : Screen {
        override val route = "outfit_log_detail/{logId}"
        fun createRoute(logId: Long) = "outfit_log_detail/$logId"
    }

    data object OutfitLogEdit : Screen {
        override val route = "outfit_log_edit/{logId}"
        fun createRoute(logId: Long?) = if (logId != null) "outfit_log_edit/$logId" else "outfit_log_edit/0"
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

    data object BackupRestore : Screen {
        override val route = "backup_restore"
    }

    data object StyleManage : Screen {
        override val route = "style_manage"
    }

    data object SeasonManage : Screen {
        override val route = "season_manage"
    }

    data object TaobaoImport : Screen {
        override val route = "taobao_import"
    }

    data object Recommendation : Screen {
        override val route = "recommendation/{itemId}"
        fun createRoute(itemId: Long) = "recommendation/$itemId"
    }

    data object QuickOutfitLog : Screen {
        override val route = "quick_outfit_log"
    }

    data object ThemeSelect : Screen {
        override val route = "theme_select"
    }

    data object FilteredItemList : Screen {
        override val route = "filtered_item_list?filterType={filterType}&filterValue={filterValue}&title={title}"
        fun createRoute(filterType: String, filterValue: String, title: String): String {
            return "filtered_item_list?filterType=$filterType&filterValue=${android.net.Uri.encode(filterValue)}&title=${android.net.Uri.encode(title)}"
        }
    }

    data object LocationManage : Screen {
        override val route = "location_manage"
    }

    data object LocationDetail : Screen {
        override val route = "location_detail/{locationId}"
        fun createRoute(locationId: Long): String = "location_detail/$locationId"
    }
}
