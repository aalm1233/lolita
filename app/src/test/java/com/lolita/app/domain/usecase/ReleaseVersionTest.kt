package com.lolita.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericComponentsRatherThanText() {
        assertEquals(1, ReleaseVersion.compare("2.10.0", "2.9.9"))
        assertEquals(1, ReleaseVersion.compare("3.0.0", "2.99.99"))
        assertEquals(1, ReleaseVersion.compare("2.39.6", "2.39.5"))
    }

    @Test fun equalAndLocallyNewerVersionsDoNotOfferAnUpdate() {
        assertEquals(0, ReleaseVersion.compare("v2.39.5", "2.39.5"))
        assertEquals(-1, ReleaseVersion.compare("2.39.5", "2.40.0"))
    }

    @Test fun rejectsUnknownAndPrereleaseFormats() {
        listOf("latest", "2.40", "2.40.0-beta", "2.40.0.1", "-2.40.0", "999999999999.0.0").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReleaseVersion.compare(it, "2.39.5") }
        }
    }
}
