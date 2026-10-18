package com.lolita.app.data.file

import org.junit.Assert.*
import org.junit.Test

class CropGeometryTest {
    @Test fun normalizesOnlyValidFiniteRectanglesAndSnapsToActualPixels() {
        val box = CropGeometry.pixelRect(NormalizedCropRect(1f / 3f, 0.25f, 2f / 3f, 0.75f), 300, 200)
        assertEquals(CropPixelRect(100, 50, 200, 150), box)
        assertThrows(IllegalArgumentException::class.java) { NormalizedCropRect(-0.1f, 0f, 1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedCropRect(0.5f, 0f, 0.5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedCropRect(Float.NaN, 0f, 1f, 1f) }
    }

    @Test fun rotatedBoundsAndOutputBudgetNeverInventExtraPixels() {
        assertEquals(CropDimensions(200, 300), CropGeometry.rotatedSize(300, 200, 90f))
        assertEquals(CropDimensions(300, 200), CropGeometry.rotatedSize(300, 200, 180f))
        val limited = CropGeometry.outputSize(100_000, 100_000)
        assertTrue(limited.width <= 4096 && limited.height <= 4096)
        assertTrue(limited.width.toLong() * limited.height <= 8_000_000)
        assertEquals(CropDimensions(25, 12), CropGeometry.outputSize(100, 50, sample = 4))
    }
}
