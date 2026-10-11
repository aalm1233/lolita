package com.lolita.app.data.notification

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.TimeZone

object CalendarEventHelper {

    sealed interface InsertResult {
        data class Created(val eventId: Long) : InsertResult
        /** Calendar sync is optional when permission or a writable calendar is unavailable. */
        data object Unavailable : InsertResult
        /** An attempted provider operation failed and must be retried. */
        data object Failed : InsertResult
    }

    /** Creating an event also reads the list of calendars. */
    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED && hasCalendarWritePermission(context)

    fun hasCalendarWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun insertEvent(
        context: Context,
        title: String,
        description: String,
        startTimeMillis: Long,
        reminderMinutes: Int = 60
    ): Long? = (insertEventChecked(context, title, description, startTimeMillis, reminderMinutes)
        as? InsertResult.Created)?.eventId

    /** The Calendar Provider commits the event and its reminder in one transaction. */
    fun insertEventChecked(
        context: Context,
        title: String,
        description: String,
        startTimeMillis: Long,
        reminderMinutes: Int = 60
    ): InsertResult {
        if (!hasCalendarPermission(context)) return InsertResult.Unavailable
        return try {
            val calendarId = getPrimaryCalendarId(context) ?: return InsertResult.Unavailable
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTimeMillis)
                put(CalendarContract.Events.DTEND, startTimeMillis + 60 * 60 * 1000) // 1 hour
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, 0)
            }
            val operations = arrayListOf(
                ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(values)
                    .build(),
                ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                    .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                    .withValue(CalendarContract.Reminders.MINUTES, reminderMinutes)
                    .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    .build()
            )
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
            val eventId = results.getOrNull(0)?.uri?.lastPathSegment?.toLongOrNull()
            check(eventId != null && eventId > 0 && results.getOrNull(1)?.uri != null) {
                "Calendar provider did not commit an event and reminder"
            }
            InsertResult.Created(eventId)
        } catch (e: Exception) {
            Log.w("CalendarEventHelper", "Failed to create calendar event and reminder", e)
            InsertResult.Failed
        }
    }

    fun deleteEvent(context: Context, eventId: Long) {
        deleteEventChecked(context, eventId)
    }

    /** Restore journal must retain failed cleanup so it can retry after permission is granted. */
    fun deleteEventChecked(context: Context, eventId: Long): Boolean {
        if (!hasCalendarWritePermission(context)) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null) >= 0
        } catch (_: Exception) { false }
    }

    /** Empty calendar lists are optional; provider errors propagate to the retry result. */
    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val cursor = checkNotNull(context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )) { "Calendar provider did not return calendars" }
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val isPrimaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val accessLevelIndex = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            var firstWritableId: Long? = null
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val accessLevel = it.getInt(accessLevelIndex)
                if (id <= 0 || accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (firstWritableId == null) firstWritableId = id
                val isPrimary = if (isPrimaryIndex >= 0) it.getInt(isPrimaryIndex) else 0
                if (isPrimary == 1) return id
            }
            return firstWritableId
        }
    }
}
