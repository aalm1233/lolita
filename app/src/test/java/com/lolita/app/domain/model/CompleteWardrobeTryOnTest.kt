package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

/** Regression: the old HEAD rule silently treated a full wig as a head ornament. */
class CompleteWardrobeTryOnTest {
    private fun selection(id: Long, role: String) = TryOnSelection(id, "物品$id", "/images/$id.jpg", TryOnRole.valueOf(role))

    @Test fun completeWardrobeHasExplicitRolesAndPetticoatIsNotBaseClothing() {
        val required = setOf("WIG", "HAIR_COMPONENT", "HOSIERY", "LEG_WARMER", "SHOES", "BAG", "NECK_CHEST", "PETTICOAT", "HANDHELD")
        assertTrue("假发、鞋袜与裙撑必须有独立穿戴角色", TryOnRole.entries.map { it.name }.containsAll(required))
        assertFalse(TryOnRole.valueOf("PETTICOAT").garment)
    }

    @Test fun selectedWigCanReplaceHairWhilePreservingFaceAndHeadwearUsesNewHair() {
        val prompt = TryOnRequest(listOf(selection(1, "MAIN"), selection(2, "WIG"), selection(3, "HEAD"))).prompt()
        assertTrue(prompt.contains("replace the original hairstyle and hair color", ignoreCase = true))
        assertTrue(prompt.contains("preserve", ignoreCase = true) && prompt.contains("face"))
        assertTrue(prompt.contains("selected hairstyle", ignoreCase = true))
    }

    @Test fun hosieryAndShoesRespectOcclusionAndCannotShortenTheSkirt() {
        val prompt = TryOnRequest(listOf(selection(1, "MAIN"), selection(2, "HOSIERY"), selection(3, "SHOES"))).prompt()
        assertTrue(prompt.contains("Do not shorten", ignoreCase = true))
        assertTrue(prompt.contains("boot", ignoreCase = true))
        assertTrue(prompt.contains("pair", ignoreCase = true))
    }

    @Test fun solePetticoatCannotSatisfyTheBaseGarmentRequirement() {
        val petticoat = selection(1, "PETTICOAT")
        assertThrows(IllegalArgumentException::class.java) { TryOnRequest(listOf(petticoat)) }
    }

    @Test fun twoFullWigsOrShoesAreNeverSilentlyAccepted() {
        val base = selection(1, "MAIN")
        for (role in listOf("WIG", "SHOES")) {
            val rows = listOf(base, selection(2, role), selection(3, role))
            assertThrows(IllegalArgumentException::class.java) { TryOnRequest(rows) }
        }
    }

    @Test fun hairComponentsAndLegWarmersCanBeLayeredWithTheirBaseItems() {
        val rows = listOf(selection(1, "MAIN"), selection(2, "WIG"), selection(3, "HAIR_COMPONENT"), selection(4, "HOSIERY"), selection(5, "LEG_WARMER"))
        assertEquals(5, TryOnRequest(rows).selections.size)
    }
}
