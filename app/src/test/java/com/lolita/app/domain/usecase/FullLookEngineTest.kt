package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class FullLookEngineTest {
    private fun item(id: Long, category: Long, status: ItemStatus = ItemStatus.OWNED) =
        Item(id = id, categoryId = category, brandId = 1, name = "服饰$id", description = "", status = status,
            style = "自定义风格", colors = listOf("自定义颜色"), season = "自定义季节")
    private val categories = listOf(Category(id = 1, name = "JSK"), Category(id = 2, name = "衬衫"),
        Category(id = 3, name = "KC"), Category(id = 4, name = "发带"), Category(id = 5, name = "OP"),
        Category(id = 6, name = "自定义饰品"))
    @Test fun jskCanRecommendAnotherClothingCategoryAndCustomAttributes() {
        val groups = FullLookEngine.build(item(1, 1), listOf(item(2, 2), item(3, 3), item(4, 6)), categories, emptySet())
        assertEquals(setOf(2L, 3L, 4L), groups.values.flatten().map { it.item.id }.toSet())
        assertTrue(groups.values.flatten().all { "风格相同" in it.reasons })
    }
    @Test fun opExcludesTopAndOnlyOwnedCandidatesAreUsed() {
        val groups = FullLookEngine.build(item(1, 5), listOf(item(2, 2), item(3, 3, ItemStatus.WISHED), item(4, 4)), categories, emptySet())
        assertEquals(listOf(4L), groups.values.flatten().map { it.item.id })
    }
    @Test fun headwearAlternativesShareOneSlotAndHistoryRanksHigher() {
        val groups = FullLookEngine.build(item(1, 1), listOf(item(2, 3), item(3, 4)), categories, setOf(3))
        assertEquals(1, groups.size)
        assertEquals(3L, groups.values.single().first().item.id)
    }
    @Test fun lockedAndSkippedSlotsSurviveShuffle() {
        val groups = FullLookEngine.build(item(1, 1), listOf(item(2, 3), item(3, 4), item(4, 2)), categories, emptySet())
        val selection = LookSelection.initial(groups)
        val head = groups.keys.first { groups.getValue(it).size == 2 }
        val top = groups.keys.first { it != head }
        val locked = selection.copy(locked = setOf(head), selected = selection.selected + (top to null))
        val next = locked.next(groups)
        assertEquals(locked.selected, next.selected)
    }
}
