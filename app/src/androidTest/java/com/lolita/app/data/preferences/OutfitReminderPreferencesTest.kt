package com.lolita.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.usecase.DailyOutfitReminderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Every test uses a separate DataStore file and never opens app_settings. */
@RunWith(AndroidJUnit4::class)
class OutfitReminderPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: AppPreferences

    private fun openPreferences() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = AppPreferences(context, PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(directory, "reminder.preferences_pb") }))
    }

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "outfit-reminder-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        openPreferences()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::scope.isInitialized) scope.coroutineContext[Job]!!.cancelAndJoin()
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test
    fun legacyHourWithoutMinuteDefaultsToZero() = runBlocking<Unit> {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(directory, "reminder.preferences_pb") })
        dataStore.edit {
            it[booleanPreferencesKey("outfit_reminder_enabled")] = true
            it[intPreferencesKey("outfit_reminder_hour")] = 23
        }
        preferences = AppPreferences(context, dataStore)
        assertEquals(DailyOutfitReminderConfig(true, 23, 0), preferences.outfitReminderConfig.first())
    }

    @Test
    fun fullTimeChangeAndEnabledStatePersistAcrossDataStoreRecreation() = runBlocking<Unit> {
        preferences.setOutfitReminderEnabled(true)
        preferences.setOutfitReminderTime(20, 45)
        assertEquals(DailyOutfitReminderConfig(true, 20, 45), preferences.outfitReminderConfig.first())
        preferences.setOutfitReminderTime(21, 15)
        assertEquals(DailyOutfitReminderConfig(true, 21, 15), preferences.outfitReminderConfig.first())
        scope.coroutineContext[Job]!!.cancelAndJoin()
        openPreferences()
        assertEquals(DailyOutfitReminderConfig(true, 21, 15), preferences.outfitReminderConfig.first())
        preferences.setOutfitReminderEnabled(false)
        assertEquals(DailyOutfitReminderConfig(false, 21, 15), preferences.outfitReminderConfig.first())
    }

    @Test
    fun invalidTimeFailsBeforeChangingAnySavedSetting() = runBlocking<Unit> {
        preferences.setOutfitReminderTime(20, 45)
        listOf(24 to 15, 21 to 60).forEach { (hour, minute) ->
            try {
                preferences.setOutfitReminderTime(hour, minute)
                fail("Invalid reminder time was accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(DailyOutfitReminderConfig(false, 20, 45), preferences.outfitReminderConfig.first())
        }
    }
}
