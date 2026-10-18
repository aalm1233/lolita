package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.RecognitionTask
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecognitionReviewSafetyTest {
    private fun candidate(id: String = "one", field: RecognitionField = RecognitionField.NAME,
        value: String = "商品名", quote: String = "商品名", size: String? = null) =
        RecognitionCandidate(id, field, value, RecognitionSource.TEXT, listOf("region"), quote, null, size)

    @Test fun textWithoutAQuoteIsNeverPreselectedOrTreatedAsAFact() {
        val row = candidate(quote = "")
        assertFalse(row.preselect(""))
        assertFalse(row.eligible(null, null))
    }
    @Test fun unitWordsWithoutANumericMeasurementRemainUnconfirmed() {
        assertFalse(candidate(field = RecognitionField.SHOE_HEEL, value = "未提供 cm", quote = "未提供 cm").eligible(null, null))
        assertFalse(candidate(field = RecognitionField.SHOE_HEEL, value = "ACME 42", quote = "ACME 42").eligible(null, null))
    }
    @Test fun footFitCannotBeAcceptedAsShoeInnerLength() {
        assertFalse(candidate(field = RecognitionField.SHOE_INNER_LENGTH, value = "适合足长 23 cm", quote = "适合足长 23 cm").eligible(null, null))
    }
    @Test fun changingTheSelectedRowExcludesItsPreviouslySelectedDependencies() {
        val rows = listOf(candidate("m", RecognitionField.NAME, "M码鞋", "M码鞋", "M"),
            candidate("l", RecognitionField.NAME, "L码鞋", "L码鞋", "L"))
        val merged = RecognitionMerge.apply("a", "a", emptyMap(), emptyMap(), rows, setOf("m", "l"), selectedSize = "L")
        assertEquals(setOf("l"), merged.acceptedIds)
        assertTrue(merged.conflicts.isEmpty())
        assertEquals("L码鞋", merged.values[RecognitionField.NAME])
    }
    @Test fun restoredTasksRejectMalformedOrForeignReviewStateBeforeComposition() {
        val draft = UUID.randomUUID().toString()
        val request = RecognitionRequest(draftId = draft, targetItemId = null,
            regions = listOf(RecognitionRegion("region", "0".repeat(64), 8, 8)), baseline = emptyMap())
        val task = RecognitionTask(UUID.randomUUID().toString(), draft, null, request.encode(), listOf("/region.jpg"),
            status = RecognitionStatus.AWAITING_REVIEW, resultJson = RecognitionResult(candidates = listOf(candidate())).encode())
        assertThrows(IllegalArgumentException::class.java) { task.copy(reviewJson = "{bad").validate() }
        assertThrows(IllegalArgumentException::class.java) { task.copy(reviewJson = RecognitionReview(selectedIds = setOf("foreign")).encode()).validate() }
    }
    @Test fun qualifiedMeasurementsKeepTheirOriginalUnitsAndRejectDifferentScopeOrInventedConversions() {
        val raw = candidate(field = RecognitionField.CLOTHING_BUST, value = "88 至 94 mm", quote = "胸围88 至 94 mm")
            .copy(measurement = RecognitionMeasurement(RecognitionMeasurementTarget.BUST_CIRCUMFERENCE,
                RecognitionMeasurementBasis.PRODUCT_MEASURED, RecognitionMeasurementUnit.MM))
        assertTrue(raw.eligible(null,null))
        assertFalse(raw.copy(measurement = raw.measurement!!.copy(basis = RecognitionMeasurementBasis.FLAT_WIDTH)).eligible(null,null))
        assertFalse(raw.copy(value = "8.8 至 9.4 cm").eligible(null,null))
        assertFalse(raw.copy(value = "94 至 88 mm").eligible(null,null))
        assertFalse(raw.copy(value = "0 mm", quote = "胸围0 mm").eligible(null,null))
        assertEquals("88 至 94 mm", RecognitionResult.decode(RecognitionResult(candidates=listOf(raw)).encode(),setOf("region")).candidates.single().value)
        assertEquals(ClothingSpecifications(), WardrobeSpecifications.decode("""{"version":1}""").clothing)
    }

}
