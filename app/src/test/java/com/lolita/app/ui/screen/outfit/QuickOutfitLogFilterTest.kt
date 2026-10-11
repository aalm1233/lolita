package com.lolita.app.ui.screen.outfit

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickOutfitLogFilterTest {
    private val dress = Item(id = 1, brandId = 101, categoryId = 307,
        name = "月光花园", description = "", status = ItemStatus.OWNED)
    private val socks = Item(id = 2, brandId = 101, categoryId = 308,
        name = "日常袜子", description = "", status = ItemStatus.OWNED)
    private val bonnet = Item(id = 3, brandId = 102, categoryId = 308,
        name = "月光Bonnet", description = "", status = ItemStatus.OWNED)
    private val historical = Item(id = 4, brandId = 102, categoryId = 308,
        name = "历史记录中的服饰", description = "", status = ItemStatus.WISHED)
    private val state = QuickOutfitLogUiState(
        items = listOf(dress, socks, bonnet),
        brandNames = mapOf(101L to "Classic Alice", 102L to "月光洋装"),
        categories = listOf(Category(id = 307, name = "JSK"), Category(id = 308, name = "配饰")),
        selectedItemIds = setOf(dress.id, historical.id),
        existingLogItems = listOf(dress, historical),
        isLoadingItems = false,
        isLoadingTodayLog = false
    )

    @Test
    fun brandSearchAndCategoryIntersectWhilePreservingSelectionOutsideTheGrid() {
        val filtered = state.copy(searchQuery = " cLaSsIc ", selectedCategoryId = 308)
        assertEquals(listOf(socks.id), filtered.visibleItems.map { it.id })
        assertEquals(setOf(dress.id, historical.id), filtered.selectedItemIds)
        assertEquals(listOf(dress.id, historical.id), filtered.selectedItems.map { it.id })
    }

    @Test
    fun nameSearchFindsMatchingClothingAndEmptyResultsKeepThePreview() {
        val byName = state.copy(searchQuery = "花园")
        assertEquals(listOf(dress.id), byName.visibleItems.map { it.id })
        val unmatched = byName.copy(selectedCategoryId = 308)
        assertTrue(unmatched.visibleItems.isEmpty())
        assertEquals(state.selectedItemIds, unmatched.selectedItemIds)
        assertEquals(listOf(dress.id, historical.id), unmatched.selectedItems.map { it.id })
    }
}
