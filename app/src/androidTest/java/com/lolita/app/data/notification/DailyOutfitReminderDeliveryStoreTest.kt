package com.lolita.app.data.notification

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DailyOutfitReminderDeliveryStoreTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var preferencesName: String
    private lateinit var isolatedContext: Context

    @Before
    fun setUp() {
        preferencesName = "daily_outfit_reminder_test_${UUID.randomUUID()}"
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name == "daily_outfit_reminder_deliveries")
                return baseContext.getSharedPreferences(preferencesName, mode)
            }
        }
    }

    @After
    fun tearDown() {
        if (::preferencesName.isInitialized) baseContext.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun sameLocalDateSurvivesStoreRecreationAndCannotBeNotifiedAgainAfterTimeChange() {
        val date = LocalDate.of(2026, 10, 11)
        var notifications = 0
        assertTrue(DailyOutfitReminderDeliveryStore(isolatedContext).deliverOnce(date) {
            notifications++; true
        })
        val recreated = DailyOutfitReminderDeliveryStore(isolatedContext)
        assertTrue(recreated.wasDelivered(date))
        assertFalse(recreated.deliverOnce(date) { notifications++; true })
        assertEquals(1, notifications)
    }

    @Test
    fun failedNotificationRemainsEligibleAndFutureDayHasItsOwnReceipt() {
        val date = LocalDate.of(2026, 10, 11)
        val store = DailyOutfitReminderDeliveryStore(isolatedContext)
        assertFalse(store.deliverOnce(date) { false })
        assertFalse(DailyOutfitReminderDeliveryStore(isolatedContext).wasDelivered(date))
        assertTrue(store.deliverOnce(date) { true })
        assertTrue(store.deliverOnce(date.plusDays(1)) { true })
        assertFalse(DailyOutfitReminderDeliveryStore(isolatedContext).deliverOnce(date) { true })
        assertEquals(2, baseContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).all.size)
    }
}
