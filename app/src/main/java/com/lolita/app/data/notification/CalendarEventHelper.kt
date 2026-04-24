package com.lolita.app.data.notification

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

object CalendarEventHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun insertEvent(
        context: Context,
        title: String,
        description: String,
        startTimeMillis: Long,
        reminderMinutes: Int = 60
    ): Long? {
        if (!hasCalendarPermission(context)) return null

        val calendarId = getPrimaryCalendarId(context) ?: return null

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTimeMillis)
            put(CalendarContract.Events.DTEND, startTimeMillis + 60 * 60 * 1000) // 1 hour
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, 0)
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (_: Exception) {
            null
        } ?: return null

        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null

        // Add a reminder
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, reminderMinutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        try {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        } catch (_: Exception) {
            // Reminder insertion failed, event still exists
        }

        return eventId
    }

    fun deleteEvent(context: Context, eventId: Long) {
        if (!hasCalendarPermission(context)) return
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // Event may already be deleted
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val cursor = try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
        } catch (_: Exception) {
            null
        } ?: return null

        cursor.use {
            val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
            val isPrimaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val accessLevelIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            if (idIndex < 0) return null

            var firstWritableId: Long? = null
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val accessLevel = if (accessLevelIndex >= 0) it.getInt(accessLevelIndex) else -1
                val isReadOnly = accessLevel == CalendarContract.Calendars.CAL_ACCESS_READ
                if (isReadOnly) continue

                if (firstWritableId == null) {
                    firstWritableId = id
                }
                val isPrimary = if (isPrimaryIndex >= 0) it.getInt(isPrimaryIndex) else 0
                if (isPrimary == 1) {
                    return id
                }
            }
            return firstWritableId
        }
    }
}