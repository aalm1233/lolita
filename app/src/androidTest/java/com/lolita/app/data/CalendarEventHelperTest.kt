package com.lolita.app.data

import android.Manifest
import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OrderBinding
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.RestoreJournal
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory provider only; ContentResolver.wrap is available starting with API 29. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CalendarEventHelperTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun context(provider: CalendarProvider, read: Boolean = true, write: Boolean = true): ContextWrapper =
        object : ContextWrapper(baseContext) {
            private val resolver = ContentResolver.wrap(provider)
            override fun getContentResolver(): ContentResolver = resolver
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int = when (permission) {
                Manifest.permission.READ_CALENDAR -> permissionResult(read)
                Manifest.permission.WRITE_CALENDAR -> permissionResult(write)
                else -> super.checkPermission(permission, pid, uid)
            }
            override fun checkSelfPermission(permission: String): Int = when (permission) {
                Manifest.permission.READ_CALENDAR -> permissionResult(read)
                Manifest.permission.WRITE_CALENDAR -> permissionResult(write)
                else -> super.checkSelfPermission(permission)
            }
        }

    private fun permissionResult(granted: Boolean) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

    private fun insert(context: ContextWrapper) = CalendarEventHelper.insertEventChecked(
        context, "付款提醒", "付款金额: ¥100.00", 1_900_000_000_000L, 2 * 24 * 60
    )

    @Test
    fun creationRequiresReadAndWriteWhileDeletionOnlyRequiresWrite() {
        for ((read, write) in listOf(false to false, true to false, false to true)) {
            val provider = CalendarProvider()
            provider.events[11] = ContentValues()
            val context = context(provider, read, write)
            assertFalse(CalendarEventHelper.hasCalendarPermission(context))
            assertEquals(write, CalendarEventHelper.hasCalendarWritePermission(context))
            assertEquals(CalendarEventHelper.InsertResult.Unavailable, insert(context))
            assertEquals(0, provider.queryCount)
            assertEquals(0, provider.batchCount)
            assertEquals(write, CalendarEventHelper.deleteEventChecked(context, 11))
            assertEquals(!write, provider.events.containsKey(11))
            if (write) assertTrue(CalendarEventHelper.deleteEventChecked(context, 11))
        }
        val provider = CalendarProvider()
        assertTrue(CalendarEventHelper.hasCalendarPermission(context(provider)))
    }

    @Test
    fun readOnlyPrimaryCalendarsCannotHideAWritableSecondaryCalendar() {
        val provider = CalendarProvider()
        provider.calendars.clear()
        listOf(-1, CalendarContract.Calendars.CAL_ACCESS_NONE,
            CalendarContract.Calendars.CAL_ACCESS_FREEBUSY, CalendarContract.Calendars.CAL_ACCESS_READ,
            CalendarContract.Calendars.CAL_ACCESS_RESPOND).forEachIndexed { index, access ->
            provider.calendars += Calendar(index + 1L, 1, access)
        }
        provider.calendars += Calendar(25, 0, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
        val result = insert(context(provider)) as CalendarEventHelper.InsertResult.Created
        assertEquals(25L, provider.events[result.eventId]!!.getAsLong(CalendarContract.Events.CALENDAR_ID))
        assertEquals(1, provider.events.size)
        assertEquals(1, provider.reminders.size)
    }

    @Test
    fun writablePrimaryCalendarIsPreferredAndBothRowsUseTheSameEventId() {
        val provider = CalendarProvider()
        provider.calendars += Calendar(25, 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR)
        val result = insert(context(provider)) as CalendarEventHelper.InsertResult.Created
        val event = provider.events[result.eventId]!!
        assertEquals(25L, event.getAsLong(CalendarContract.Events.CALENDAR_ID))
        assertEquals(1_900_000_000_000L, event.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals(1_900_003_600_000L, event.getAsLong(CalendarContract.Events.DTEND))
        assertEquals("付款提醒", event.getAsString(CalendarContract.Events.TITLE))
        val reminder = provider.reminders.values.single()
        assertEquals(result.eventId, reminder.getAsLong(CalendarContract.Reminders.EVENT_ID))
        assertEquals(2 * 24 * 60, reminder.getAsInteger(CalendarContract.Reminders.MINUTES))
        assertEquals(CalendarContract.Reminders.METHOD_ALERT, reminder.getAsInteger(CalendarContract.Reminders.METHOD))
        assertEquals(1, provider.batchCount)
    }

    @Test
    fun noWritableCalendarIsOptionalButQueryFailuresRequireRetry() {
        val provider = CalendarProvider()
        provider.calendars.clear()
        assertEquals(CalendarEventHelper.InsertResult.Unavailable, insert(context(provider)))
        provider.calendars += Calendar(1, 1, CalendarContract.Calendars.CAL_ACCESS_RESPOND)
        assertEquals(CalendarEventHelper.InsertResult.Unavailable, insert(context(provider)))
        provider.queryFails = true
        assertEquals(CalendarEventHelper.InsertResult.Failed, insert(context(provider)))
        provider.queryFails = false
        provider.queryReturnsNull = true
        assertEquals(CalendarEventHelper.InsertResult.Failed, insert(context(provider)))
        assertEquals(0, provider.batchCount)
    }

    @Test
    fun reminderInsertExceptionsAndNullResultsRollbackTheEvent() {
        for (nullResult in listOf(false, true)) {
            val provider = CalendarProvider()
            provider.reminderFails = !nullResult
            provider.reminderReturnsNull = nullResult
            val context = context(provider)
            assertEquals(CalendarEventHelper.InsertResult.Failed, insert(context))
            assertEquals(1, provider.batchCount)
            assertTrue(provider.events.isEmpty())
            assertTrue(provider.reminders.isEmpty())
            assertNull(CalendarEventHelper.insertEvent(context, "付款提醒", "应付", 1_900_000_000_000L))
            assertTrue(provider.events.isEmpty())
        }
    }

    @Test
    fun providerFailuresRetainTheJournalUntilCalendarSyncSucceedsWithoutDuplicates() = runBlocking {
        val provider = CalendarProvider().apply { reminderFails = true }
        val context = context(provider)
        withOrder(context) { database, stageId, priceId ->
            val backup = BackupManager(context, database)
            assertEquals(1, backup.recoverPendingReminders())
            val journal = database.restoreJournalDao().get()!!
            assertNull(database.orderDao().getStage(stageId)!!.calendarEventId)
            assertTrue(provider.events.isEmpty())
            assertTrue(provider.reminders.isEmpty())
            assertEquals(1, backup.recoverPendingReminders())
            assertEquals(journal, database.restoreJournalDao().get())
            assertEquals(listOf(priceId), Gson().fromJson(journal.affectedPriceIdsJson, Array<Long>::class.java).toList())

            provider.reminderFails = false
            assertEquals(0, backup.recoverPendingReminders())
            assertNull(database.restoreJournalDao().get())
            assertNotNull(database.orderDao().getStage(stageId)!!.calendarEventId)
            assertEquals(1, provider.events.size)
            assertEquals(1, provider.reminders.size)
            val batchCount = provider.batchCount
            assertEquals(0, backup.recoverPendingReminders())
            assertEquals(batchCount, provider.batchCount)
            assertEquals(1, provider.events.size)
        }
    }

    @Test
    fun missingPermissionOrWritableCalendarsDoNotBlockReminderJournalCompletion() = runBlocking {
        for (readGranted in listOf(false, true)) {
            val provider = CalendarProvider().apply { calendars.clear() }
            val context = context(provider, read = readGranted)
            withOrder(context) { database, stageId, _ ->
                assertEquals(0, BackupManager(context, database).recoverPendingReminders())
                assertNull(database.restoreJournalDao().get())
                assertNull(database.orderDao().getStage(stageId)!!.calendarEventId)
                assertEquals(0, provider.batchCount)
                assertTrue(provider.events.isEmpty())
                assertEquals(if (readGranted) 1 else 0, provider.queryCount)
            }
        }
    }

    @Test
    fun writeOnlyPermissionCleansOldPointersWithoutPretendingToCreateANewCalendarEvent() = runBlocking {
        val provider = CalendarProvider()
        val context = context(provider, read = false)
        withOrder(context) { database, stageId, priceId ->
            provider.events[501] = ContentValues()
            database.orderDao().updateStageCalendarEventId(stageId, 501)
            val previous = Payment(id = -stageId, priceId = priceId, amount = 100.0,
                dueDate = 1_900_000_000_000L, calendarEventId = 501)
            database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = Gson().toJson(listOf(previous)),
                affectedPriceIdsJson = Gson().toJson(listOf(priceId))))
            assertEquals(0, BackupManager(context, database).recoverPendingReminders())
            assertNull(database.restoreJournalDao().get())
            assertNull(database.orderDao().getStage(stageId)!!.calendarEventId)
            assertTrue(provider.events.isEmpty())
            assertEquals(0, provider.queryCount)
            assertEquals(0, provider.batchCount)
        }
    }

    private suspend fun withOrder(
        context: ContextWrapper,
        block: suspend (LolitaDatabase, Long, Long) -> Unit
    ) {
        val database = Room.inMemoryDatabaseBuilder(baseContext, LolitaDatabase::class.java).build()
        val stageId = 1_810_000_071L
        val priceId = 1_710_000_071L
        try {
            database.brandDao().insertBrand(Brand(id = 71, name = "日历回归品牌", createdAt = 1))
            database.categoryDao().insertCategory(Category(id = 72, name = "日历回归分类", createdAt = 1))
            database.itemDao().insertItem(Item(id = 1_610_000_071L, brandId = 71, categoryId = 72,
                name = "日历回归订单", description = "内存数据库", status = ItemStatus.PENDING_PAYMENT,
                createdAt = 1, updatedAt = 1))
            database.priceDao().insertPrice(Price(id = priceId, itemId = 1_610_000_071L,
                type = PriceType.FULL, totalPrice = 100.0, createdAt = 1))
            database.orderDao().insertBinding(OrderBinding(1_610_000_071L, priceId))
            database.orderDao().insertStage(PaymentStage(id = stageId, priceId = priceId, name = "尾款",
                amount = 100.0, dueDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
                reminderSet = true, customReminderDays = 2))
            database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = "[]",
                affectedPriceIdsJson = Gson().toJson(listOf(priceId))))
            block(database, stageId, priceId)
        } finally {
            // High private test IDs and a 30-day deadline keep all test alarms isolated and silent.
            PaymentReminderScheduler(context).cancelReminder(-stageId)
            database.close()
        }
    }

    private data class Calendar(val id: Long, val primary: Int, val access: Int)

    /** Emulates the Calendar Provider's transaction and executes real operation back-references. */
    private class CalendarProvider : ContentProvider() {
        val calendars = mutableListOf(Calendar(1, 0, CalendarContract.Calendars.CAL_ACCESS_OWNER))
        val events = linkedMapOf<Long, ContentValues>()
        val reminders = linkedMapOf<Long, ContentValues>()
        var queryCount = 0
        var batchCount = 0
        var queryFails = false
        var queryReturnsNull = false
        var reminderFails = false
        var reminderReturnsNull = false
        private var nextId = 10_000L

        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            check(uri == CalendarContract.Calendars.CONTENT_URI)
            queryCount++
            if (queryFails) throw IllegalStateException("Calendar provider unavailable")
            if (queryReturnsNull) return null
            return MatrixCursor(arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)).apply {
                calendars.forEach { addRow(arrayOf<Any>(it.id, it.primary, it.access)) }
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = when (uri) {
            CalendarContract.Events.CONTENT_URI -> {
                val id = nextId++
                events[id] = ContentValues(values!!)
                ContentUris.withAppendedId(uri, id)
            }
            CalendarContract.Reminders.CONTENT_URI -> {
                if (reminderFails) throw IllegalStateException("Reminder insert failed")
                if (reminderReturnsNull) null else {
                    val id = nextId++
                    reminders[id] = ContentValues(values!!)
                    ContentUris.withAppendedId(uri, id)
                }
            }
            else -> error("Unexpected insert $uri")
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            check(uri.pathSegments.first() == CalendarContract.Events.CONTENT_URI.pathSegments.first())
            val id = ContentUris.parseId(uri)
            val removed = events.remove(id) != null
            reminders.entries.removeAll { it.value.getAsLong(CalendarContract.Reminders.EVENT_ID) == id }
            return if (removed) 1 else 0
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = error("No update expected")

        override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
            batchCount++
            val oldEvents = LinkedHashMap(events)
            val oldReminders = LinkedHashMap(reminders)
            val oldNextId = nextId
            return try {
                super.applyBatch(operations)
            } catch (failure: Exception) {
                events.clear()
                events.putAll(oldEvents)
                reminders.clear()
                reminders.putAll(oldReminders)
                nextId = oldNextId
                throw failure
            }
        }
    }
}
