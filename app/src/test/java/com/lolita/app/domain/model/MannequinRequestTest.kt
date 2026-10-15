package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import org.junit.Assert.*
import org.junit.Test

class MannequinRequestTest {
    @Test fun onlyExplicitClothingDressCategoriesAreEligible() {
        for (name in listOf("JSK", " op ", "s K", "裙子", "连衣裙", "吊带裙", "背带裙", "半身裙", "长裙", "短裙")) {
            assertTrue(name, supportsMannequin(Category(name = name)))
            assertFalse(name, supportsMannequin(Category(name = name, group = CategoryGroup.ACCESSORY)))
        }
        for (name in listOf("", "裙撑", "OP配件", "上衣", "TOP", "JSK套装", "短裙配件", "连 衣裙", "\uFF2F\uFF30")) {
            assertFalse(name, supportsMannequin(Category(name = name)))
        }
        assertFalse(supportsMannequin(null))
    }

    @Test fun invalidIdentityReferenceAndOverlongInstructionsAreRejected() {
        for (block in listOf<() -> Unit>(
            { MannequinRequest(0, "/photo.png") },
            { MannequinRequest(-1, "/photo.png") },
            { MannequinRequest(1, " ") },
            { MannequinRequest(1, "/photo.png", "字".repeat(201)) }
        )) {
            try { block(); fail("invalid request accepted") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(MannequinRequest(1, "/photo.png", "字".repeat(200)).prompt().contains("字".repeat(200)))
    }

    @Test fun fixedPromptUsesOnlyTemplateAndSelectedReferenceWithOptionalInstruction() {
        val plain = MannequinRequest(12, "/private/image.png").prompt()
        assertTrue(plain.startsWith("Use case: compositing.\nEdit image 1"))
        assertTrue(plain.contains("Image 2 is the only authority for garment identity."))
        assertTrue(plain.contains("No face, head, hair, human skin, arms, hands or legs."))
        assertTrue(plain.endsWith("Only one garment on one fixed front mannequin."))
        assertFalse(plain.contains("/private"))
        assertEquals(plain + "\nAdditional user instruction about the garment: 排除独立手袖", MannequinRequest(12, "/photo.png", "排除独立手袖").prompt())
    }
}
