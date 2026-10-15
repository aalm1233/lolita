package com.lolita.app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DatePickerDatesTest {
    @Test fun localDatesUseUtcPickerDaysAndReturnToLocalMidnight() {
        val cases = listOf(
            Triple("Asia/Shanghai", "2026-09-30T16:00:00Z", "2026-10-01T00:00:00Z"),
            Triple("America/Los_Angeles", "2026-10-01T07:00:00Z", "2026-10-01T00:00:00Z"),
            Triple("America/New_York", "2026-03-08T05:00:00Z", "2026-03-08T00:00:00Z"),
            Triple("America/New_York", "2026-11-01T04:00:00Z", "2026-11-01T00:00:00Z")
        )
        for ((zone, localStart, pickerStart) in cases) {
            val local = Instant.parse(localStart).toEpochMilli()
            val picker = Instant.parse(pickerStart).toEpochMilli()
            assertEquals(picker, localDateToPickerMillis(local, ZoneId.of(zone)))
            assertEquals(local, pickerMillisToLocalDate(picker, ZoneId.of(zone)))
        }
    }
}
