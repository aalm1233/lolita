package com.lolita.app.data.model

/**
 * 淘宝订单（一个订单可包含多个商品）
 */
data class TaobaoOrder(
    val orderId: String,
    val orderTime: String,
    val orderStatus: String,
    val shopName: String,
    val totalPaid: Double,
    val shipping: Double,
    val items: List<TaobaoOrderItem>
)

/**
 * 淘宝订单中的单个商品
 */
data class TaobaoOrderItem(
    val name: String,
    val productUrl: String,
    val styleSpec: String,      // 型号款式原始值
    val quantity: Int,
    val price: Double,
    // 从型号款式解析出的字段
    val parsedType: String?,    // OP/SK/衬衫 等
    val parsedSize: String?,    // Lady80/Lady85/均码
    val parsedColor: String?,   // 颜色
    // 所属订单信息（冗余，方便后续处理）
    val orderId: String,
    val orderTime: String,
    val shopName: String
)
