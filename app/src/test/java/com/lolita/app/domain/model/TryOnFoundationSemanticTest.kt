package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

/** A position correction cannot change what a known product actually is. */
class TryOnFoundationSemanticTest {
    private fun row(semantic: CategorySemantic?, role: TryOnRole = TryOnRole.MAIN) =
        TryOnSelection(1, "已确认物品", "/images/reference.jpg", role, semantic, ItemComposition.SINGLE)

    @Test fun knownPetticoatCannotBecomeTheOnlyFoundationByChangingItsRole() {
        listOf(TryOnRole.MAIN, TryOnRole.TOP, TryOnRole.BOTTOM, TryOnRole.OUTER).forEach { role ->
            assertThrows(IllegalArgumentException::class.java) { TryOnRequest(listOf(row(CategorySemantic.PETTICOAT, role))) }
        }
    }

    @Test fun knownWigsShoesAndAccessoriesCannotBecomeFoundationThroughMainRole() {
        listOf(CategorySemantic.FULL_WIG, CategorySemantic.MARY_JANE, CategorySemantic.KC).forEach { semantic ->
            assertThrows(IllegalArgumentException::class.java) { TryOnRequest(listOf(row(semantic))) }
        }
    }

    @Test fun actualClothingAndExplicitLegacyUnknownRolesStillWork() {
        assertEquals(CategorySemantic.OP, TryOnRequest(listOf(row(CategorySemantic.OP))).selections.single().semantic)
        assertEquals(1, TryOnRequest(listOf(row(null))).selections.size)
        assertEquals(1, TryOnRequest(listOf(row(CategorySemantic.UNCONFIRMED))).selections.size)
    }

    @Test fun originalProtocolsKeepTheirHistoricalRoleGate() {
        (1..3).forEach { version ->
            assertEquals(version, TryOnRequest(listOf(row(CategorySemantic.PETTICOAT)), version).protocolVersion)
        }
    }
}
