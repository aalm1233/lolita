package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class WardrobeInventoryTest {
    @Test fun summariesCountEachIdentityOnceAndIncludeChildrenWithoutInventingLegacyKinds() {
        val parent = Item(id = 1, name = "裙子", brandId = 1, categoryId = 1, description = "", status = ItemStatus.OWNED)
        val shoe = Item(id = 2, name = "鞋", brandId = 1, categoryId = 2, description = "", status = ItemStatus.OWNED, parentItemId = 1)
        val legacy = parent.copy(id = 3, categoryId = 3, name = "假发")
        val summary = WardrobeInventoryCounts.from(listOf(parent, shoe, shoe, legacy), listOf(
            Category(id = 1, name = "改名后的裙子", categorySemantic = CategorySemantic.JSK),
            Category(id = 2, name = "改名后的鞋", categorySemantic = CategorySemantic.MARY_JANE),
            Category(id = 3, name = "全头假发")))
        assertEquals(3, summary.allItemCount)
        assertEquals(2, summary.mainItemCount)
        assertEquals(1, summary.byFamily[WardrobeFamily.FOOTWEAR])
        assertEquals(1, summary.byFamily[WardrobeFamily.CLOTHING])
        assertEquals(1, summary.byFamily[WardrobeFamily.UNCONFIRMED])
        assertEquals(0, summary.byFamily[WardrobeFamily.WIG])
    }
}
