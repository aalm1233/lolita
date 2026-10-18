package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class WardrobeTest {
    @Test fun renamedFullWigKeepsHeadFormAndWigRole() {
        val renamed = Category(name = "奶茶卷", categorySemantic = CategorySemantic.FULL_WIG).copy(name = "我的收藏")
        assertEquals(WardrobeFamily.WIG, renamed.categorySemantic.family)
        assertEquals(WardrobeDisplayMode.WIG_HEAD, renamed.categorySemantic.displayMode)
        assertEquals("WIG", renamed.categorySemantic.defaultWearRole.name)
        assertEquals(WardrobeDisplayMode.STANDALONE, CategorySemantic.HAIR_PIECE.displayMode)
    }
    @Test fun historicalNamesRemainUnconfirmed() {
        assertEquals(CategorySemantic.UNCONFIRMED, Category(name = "全头假发", isPreset = true).categorySemantic)
        assertEquals(WardrobeFamily.UNCONFIRMED, CategorySemantic.UNCONFIRMED.family)
    }
    @Test fun rawShoeAndSockSizesAndBothProfilesSurviveRoundTrip() {
        val data = WardrobeSpecifications(
            footwear = FootwearSpecifications(sizeSystem = "EU", sizeText = "EU 37", heelHeightCm = "3.5"),
            hosiery = HosierySpecifications(sizeText = "23 至 25 cm", denier = "30D"),
            wig = WigSpecifications(lengthCm = "1.")
        )
        val restored = WardrobeSpecifications.decode(data.encode())
        assertEquals("EU 37", restored.footwear.sizeText)
        assertEquals("23 至 25 cm", restored.hosiery.sizeText)
        assertEquals("1.", restored.wig.lengthCm)
        assertEquals(data, restored)
    }
    @Test fun copiedRegistrationKeepsSpecsAndRawSizeWithoutPurchaseOrOwnershipLinks() {
        val original = Item(id = 7, name = "鞋", description = "资料", brandId = 4, categoryId = 3,
            status = ItemStatus.PENDING_PAYMENT, parentItemId = 9, coordinateId = 8, locationId = 6,
            includedInParentPrice = true, importToken = "unique-purchase", size = "EU 37",
            specificationsJson = WardrobeSpecifications(footwear = FootwearSpecifications(sizeText = "EU 37")).encode())
        val draft = original.registrationCopy()
        assertEquals("EU 37", draft.size)
        assertEquals("EU 37", WardrobeSpecifications.decode(draft.specificationsJson).footwear.sizeText)
        assertNull(draft.parentItemId); assertNull(draft.coordinateId); assertNull(draft.importToken)
        assertNull(draft.locationId); assertFalse(draft.includedInParentPrice)
        assertEquals(ItemStatus.OWNED, draft.status)
    }
    @Test fun emptyOptionalValuesAndLegacyEmptyPayloadAreValid() {
        assertEquals(WardrobeSpecifications(), WardrobeSpecifications.decode(""))
        assertEquals(WardrobeSpecifications(), WardrobeSpecifications.decode(WardrobeSpecifications().encode()))
    }
    @Test fun futureVersionAndUnknownFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { WardrobeSpecifications.decode("{\"version\":2}") }
        assertThrows(IllegalArgumentException::class.java) { WardrobeSpecifications.decode("{\"version\":1,\"secret\":\"x\"}") }
        assertThrows(IllegalArgumentException::class.java) { WardrobeSpecifications.decode("{\"version\":1,\"wig\":{\"secret\":\"x\"}}") }
    }
}
