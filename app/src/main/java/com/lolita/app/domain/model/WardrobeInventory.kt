package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item

data class WardrobeInventoryCounts(val allItemCount: Int, val mainItemCount: Int, val byFamily: Map<WardrobeFamily, Int>) {
    companion object {
        fun from(items: List<Item>, categories: List<Category>): WardrobeInventoryCounts {
            val unique = items.distinctBy { it.id }
            val semantics = categories.associate { it.id to it.categorySemantic }
            val counts = unique.groupingBy { semantics[it.categoryId]?.family ?: WardrobeFamily.UNCONFIRMED }.eachCount()
            return WardrobeInventoryCounts(unique.size, unique.count { it.parentItemId == null },
                WardrobeFamily.entries.associateWith { counts[it] ?: 0 })
        }
    }
}
