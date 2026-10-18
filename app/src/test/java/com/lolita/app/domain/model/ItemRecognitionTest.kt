package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ItemRecognitionTest {
    private fun candidate(field: RecognitionField = RecognitionField.NAME, value: String = "奶茶色长卷假发",
        source: RecognitionSource = RecognitionSource.TEXT, variant: String? = null, size: String? = null) =
        RecognitionCandidate("one", field, value, source, listOf("region-1"), "${field.label}：$value", variant, size,
            when (field) {
                RecognitionField.WIG_LENGTH -> RecognitionMeasurement(RecognitionMeasurementTarget.HAIR_LENGTH, RecognitionMeasurementBasis.PRODUCT_MEASURED, RecognitionMeasurementUnit.CM)
                RecognitionField.SHOE_INNER_LENGTH -> RecognitionMeasurement(RecognitionMeasurementTarget.SHOE_INNER_LENGTH, RecognitionMeasurementBasis.PRODUCT_MEASURED, RecognitionMeasurementUnit.CM)
                else -> null
            })

    @Test fun visualMeasurementsAndUnqualifiedNumericFactsCannotBeApplied() {
        for (field in listOf(RecognitionField.WIG_LENGTH, RecognitionField.WIG_HEAT,
            RecognitionField.SHOE_HEEL, RecognitionField.SHOE_INNER_LENGTH, RecognitionField.HOSIERY_DEN)) {
            assertFalse(candidate(field, "60", RecognitionSource.VISUAL).eligible(null, null))
        }
        assertTrue(candidate(RecognitionField.WIG_CURL, "长卷", RecognitionSource.VISUAL).eligible(null, null))
        assertFalse(candidate(RecognitionField.WIG_LENGTH, "60").eligible(null, null))
        assertTrue(candidate(RecognitionField.WIG_LENGTH, "60 cm").eligible(null, null))
    }

    @Test fun variantAndSizeMustBothMatchBeforeApplyingRowFacts() {
        val row = candidate(RecognitionField.SHOE_INNER_LENGTH, "23 至 25 cm", variant = "玛丽珍", size = "EU 37")
        assertFalse(row.eligible(null, null)); assertFalse(row.eligible("玛丽珍", null))
        assertFalse(row.eligible("玛丽珍", "EU 38")); assertTrue(row.eligible("玛丽珍", "EU 37"))
        assertTrue(candidate().eligible(null, null))
    }

    @Test fun applyingRechecksDraftIdentityAndValuesChangedDuringRequest() {
        val name = candidate()
        val color = candidate(RecognitionField.COLOR, "奶茶色").copy(id = "two")
        val baseline = mapOf(RecognitionField.NAME to "", RecognitionField.COLOR to "")
        val actual = RecognitionMerge.apply("draft-a", "draft-a", baseline,
            baseline + (RecognitionField.NAME to "我手动填的新名称"), listOf(name, color), setOf("one", "two"))
        assertEquals(mapOf(RecognitionField.COLOR to "奶茶色"), actual.values)
        assertEquals(setOf("one"), actual.conflicts)
        assertThrows(IllegalArgumentException::class.java) {
            RecognitionMerge.apply("draft-a", "draft-b", baseline, baseline, listOf(name), setOf("one"))
        }
    }

    @Test fun existingAndVisualFieldsNeverPreselectAndUnknownNeverOverwrites() {
        assertTrue(candidate().preselect(""))
        assertFalse(candidate().preselect("已有名称"))
        assertFalse(candidate(source = RecognitionSource.VISUAL).preselect(""))
        assertFalse(candidate(value = "").preselect(""))
        assertFalse(candidate(source = RecognitionSource.UNKNOWN).eligible(null, null))
        assertFalse(candidate(RecognitionField.STYLE, "甜系").preselect(""))
        assertFalse(candidate(RecognitionField.BRAND, "未核验水印").preselect(""))
    }

    @Test fun strictResultRejectsCommandsUnknownFieldsAndMissingEvidence() {
        val valid = """{"version":1,"candidates":[{"id":"one","field":"NAME","value":"假发","source":"TEXT","regionIds":["region-1"],"quote":"假发","variant":null,"sizeRow":null}]}"""
        assertEquals("假发", RecognitionResult.decode(valid, setOf("region-1")).candidates.single().value)
        for (bad in listOf(valid.replace("NAME", "PAYMENT_AMOUNT"),
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"command\":\"save\",\"version\":1"),
            valid.replace("region-1", "other-region"))) {
            assertThrows(IllegalArgumentException::class.java) { RecognitionResult.decode(bad, setOf("region-1")) }
        }
    }

    @Test fun conflictingCandidatesAreNotMergedByLastImageOrder() {
        val first = candidate(RecognitionField.COLOR, "红色")
        val second = candidate(RecognitionField.COLOR, "蓝色").copy(id = "two")
        val actual = RecognitionMerge.apply("a", "a", emptyMap(), emptyMap(), listOf(first, second), setOf("one", "two"))
        assertTrue(actual.values.isEmpty())
        assertEquals(setOf("one", "two"), actual.conflicts)
    }
}
