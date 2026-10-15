package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class TryOnTest {
    private fun garment(id: Long = 1, role: TryOnRole = TryOnRole.MAIN, path: String = "/images/$id.jpg") =
        TryOnSelection(id, "花鸟龙月$id", path, role)

    @Test fun accessoryOnlyCannotSendPaidRequest() {
        assertThrows(IllegalArgumentException::class.java) { TryOnRequest(listOf(garment(role = TryOnRole.HEAD))) }
    }
    @Test fun emptyMissingImageDuplicateAndOverLimitCannotSendPaidRequest() {
        listOf(emptyList(), listOf(garment(path = "")), listOf(garment(), garment()), (1L..9).map { garment(it) })
            .forEach { rows -> assertThrows(IllegalArgumentException::class.java) { TryOnRequest(rows) } }
    }
    @Test fun promptKeepsModelFirstAndExplicitlyMapsEachAccessoryReference() {
        val request = TryOnRequest(listOf(garment(33), garment(30, TryOnRole.HEAD), garment(29, TryOnRole.WAIST)))
        val prompt = request.prompt()
        assertTrue(prompt.contains("Image 1"))
        assertTrue(prompt.contains("\"image\":2"))
        assertTrue(prompt.contains("\"image\":3"))
        assertTrue(prompt.contains("\"image\":4"))
        assertTrue(prompt.contains("headwear"))
        assertTrue(prompt.contains("waist accessory"))
        assertFalse(prompt.contains("/images/"))
        assertEquals(listOf(33L,30L,29L), request.selections.map { it.itemId })
    }
    @Test fun categorySuggestionSeparatesAccessoriesAndClothes() {
        assertEquals(TryOnRole.HEAD, TryOnRole.suggest("KC/发箍"))
        assertEquals(TryOnRole.HAND, TryOnRole.suggest("手袖"))
        assertEquals(TryOnRole.WAIST, TryOnRole.suggest("腰封"))
        assertEquals(TryOnRole.OUTER, TryOnRole.suggest("短外套"))
        assertEquals(TryOnRole.MAIN, TryOnRole.suggest("JSK"))
    }
}
