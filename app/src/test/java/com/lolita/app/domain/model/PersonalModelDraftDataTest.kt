package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class PersonalModelDraftDataTest {
    @Test fun rawIncompleteMeasurementsAndPhotoOrderRoundTripWithoutPaths() {
        val data=PersonalModelDraftData(name="草稿",heightCm="1.",weightKg="",photoRoles=listOf(PersonalModelPhotoRole.FRONT,PersonalModelPhotoRole.FACE))
        assertEquals(data,PersonalModelDraftData.decode(data.encode()))
        assertFalse(data.encode().contains("imagePath"))
    }
    @Test fun strictDraftRejectsFutureVersionUnknownFieldsAndDuplicateRoles() {
        val json=PersonalModelDraftData().encode()
        listOf(json.replace("\"version\":1","\"version\":2"),json.dropLast(1)+",\"path\":\"/secret\"}",
            json.replace("\"photoRoles\":[]","\"photoRoles\":[\"FACE\",\"FACE\"]")).forEach {
            assertThrows(IllegalArgumentException::class.java) { PersonalModelDraftData.decode(it) }
        }
    }
}
