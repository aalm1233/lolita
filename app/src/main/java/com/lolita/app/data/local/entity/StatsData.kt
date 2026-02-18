package com.lolita.app.data.local.entity

data class CategorySpending(
    val name: String,
    val totalSpending: Double
)

data class BrandSpending(
    val name: String,
    val totalSpending: Double
)

data class StyleSpending(
    val style: String,
    val totalSpending: Double
)

data class MonthlySpending(
    val yearMonth: String,
    val totalSpending: Double
)

data class ItemWithSpending(
    val itemId: Long,
    val itemName: String,
    val imageUrl: String?,
    val totalSpending: Double
)

data class BrandItemCount(
    val brandName: String,
    val itemCount: Int
)

data class PriorityStats(
    val priority: String,
    val itemCount: Int,
    val totalBudget: Double
)
