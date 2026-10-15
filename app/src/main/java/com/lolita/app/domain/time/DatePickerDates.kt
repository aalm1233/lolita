package com.lolita.app.domain.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Material DatePicker represents a date by UTC midnight; stored dates use local midnight. */
fun localDateToPickerMillis(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToLocalDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
