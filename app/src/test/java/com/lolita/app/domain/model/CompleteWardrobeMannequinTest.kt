package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import org.junit.Assert.*
import org.junit.Test

class CompleteWardrobeMannequinTest {
    @Test fun renamedWigUsesHeadModeAndUnknownHistoricalTypeRequiresConfirmation() {
        val renamed = Category(id = 2, name = "自定义名称", categorySemantic = CategorySemantic.FULL_WIG)
        assertEquals(MannequinPresentation.WIG_HEAD, mannequinSubject(renamed, confirmedSemantics = true)!!.presentation)
        assertNull(mannequinSubject(Category(id = 2, name = "全头假发"), confirmedSemantics = true))
        assertNull(mannequinSubject(Category(id = 2, name = "上装", categorySemantic = CategorySemantic.BLOUSE), confirmedSemantics = true))
    }
    @Test fun wigHeadPromptPreservesDollFaceBaseAndAllTargetHair() {
        val request = MannequinRequest(1, "/wig", subject = MannequinSubject(2, "假发", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG))
        assertTrue(request.prompt().contains("Image 1 is the selected bald head template"))
        assertTrue(request.prompt().contains("necessary base"))
        assertTrue(request.prompt().contains("Keep every long hair tip in frame"))
        assertFalse(request.prompt().contains("No face, head, hair"))
    }
    @Test fun hairPieceNeverBecomesFullWigAndFootwearNeverInventsItsMissingPair() {
        val piece = MannequinRequest(1, "/piece", subject = MannequinSubject(2, "发片", MannequinPresentation.ACCESSORY, CategorySemantic.HAIR_PIECE)).prompt()
        assertTrue(piece.contains("Preserve all target hair fibers"))
        assertTrue(piece.contains("never complete it into a full-head wig"))
        val shoe = MannequinRequest(1, "/shoe", subject = MannequinSubject(2, "鞋", MannequinPresentation.ACCESSORY, CategorySemantic.MARY_JANE)).prompt()
        assertTrue(shoe.contains("one visible shoe or sock must remain one"))
        assertTrue(shoe.contains("do not invent hidden soles"))
    }
    @Test fun changingCategoryLabelDoesNotChangeConfirmedSemanticPrompt() {
        val original = MannequinSubject(2, "假发", MannequinPresentation.ACCESSORY, CategorySemantic.PONYTAIL)
        assertEquals(MannequinRequest(1, "/photo", subject = original).prompt(), MannequinRequest(1, "/photo", subject = original.copy(categoryName = "我的别名")).prompt())
    }
}
