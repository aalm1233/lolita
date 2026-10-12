package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class ShareCardContentTest {
    @Test fun exportOnlyContainsPublicPresentationFieldsAndBrandsCanBeHidden() {
        val item = Item(id = 1, brandId = 2, categoryId = 3, name = "裙子", description = "私人备注", source = "私密渠道", locationId = 9,
            status = ItemStatus.OWNED, imageUrls = listOf("/local/photo.png"))
        val card = ShareCardContent.create("我的搭配", emptyList(), listOf(item), mapOf(2L to "品牌"), false)
        assertNull(card.items.single().brand)
        assertEquals("裙子", card.items.single().name)
        assertFalse(card.toString().contains("私人备注"))
        assertFalse(card.toString().contains("私密渠道"))
        assertEquals("品牌", ShareCardContent.create("标题", emptyList(), listOf(item), mapOf(2L to "品牌"), true).items.single().brand)
    }
}
