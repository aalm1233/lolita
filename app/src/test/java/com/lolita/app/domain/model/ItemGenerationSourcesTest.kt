package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import org.junit.Assert.*
import org.junit.Test

class ItemGenerationSourcesTest {
    @Test fun virtualTryOnIncludesOwnedAndWishedWithoutAdmittingOtherPurchaseStates() {
        assertEquals(setOf(ItemStatus.OWNED, ItemStatus.WISHED), ItemStatus.entries.filter { it.supportsVirtualTryOn }.toSet())
    }

    @Test fun productReferencesKeepAlbumThenLegacyAndAdditionalDetailsWithoutDuplicatesOrBlankChoices() {
        val item = Item(brandId = 1, categoryId = 1, name = "测试袜子", description = "", status = ItemStatus.WISHED,
            imageUrls = listOf("/album.jpg", ""), sizeChartImageUrl = "/legacy-detail.jpg",
            sizeChartImageUrls = listOf("/legacy-detail.jpg", "/new-detail.jpg", "/album.jpg", " "))
        assertEquals(listOf("/album.jpg", "/legacy-detail.jpg", "/new-detail.jpg"), item.productReferenceImages)
        assertEquals(listOf("/legacy-detail.jpg", "/new-detail.jpg"), item.productDetailImages)
        assertEquals(listOf("/legacy-detail.jpg", "/new-detail.jpg", "/album.jpg"), item.copy(imageUrls = emptyList()).productReferenceImages)
        assertEquals(ItemStatus.WISHED, item.status)
        assertEquals(listOf("/album.jpg", ""), item.imageUrls)
    }
}
