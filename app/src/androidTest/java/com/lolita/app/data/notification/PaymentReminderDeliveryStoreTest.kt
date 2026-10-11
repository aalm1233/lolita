package com.lolita.app.data.notification

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.domain.usecase.PaymentReminderIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real preferences with a per-test namespace; no alarms, notifications or application DB writes. */
@RunWith(AndroidJUnit4::class)
class PaymentReminderDeliveryStoreTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val identity = PaymentReminderIdentity(-7, 31, 1_900_000_000_000L, 1)
    private lateinit var preferencesName: String
    private lateinit var isolatedContext: Context

    @Before
    fun setUp() {
        preferencesName = "payment_reminder_deliveries_test_${UUID.randomUUID()}"
        isolatedContext = object : ContextWrapper(baseContext) {
            // The store requests applicationContext; retain the isolated preference mapping.
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name == "payment_reminder_deliveries") { "Unexpected preference request in isolated test" }
                return baseContext.getSharedPreferences(preferencesName, mode)
            }
        }
    }

    @After
    fun tearDown() {
        if (::preferencesName.isInitialized) baseContext.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun deliveredReceiptSurvivesStoreRecreationAndPreventsAnotherNotification() {
        var notifications = 0
        val original = PaymentReminderDeliveryStore(isolatedContext)
        assertFalse(original.wasDelivered(identity))
        assertTrue(original.deliverOnce(identity) { notifications++; true })

        val recreated = PaymentReminderDeliveryStore(isolatedContext)
        assertTrue(recreated.wasDelivered(identity))
        assertFalse(recreated.deliverOnce(identity) { notifications++; true })
        assertEquals(1, notifications)
        assertTrue(baseContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(identity.storageKey, false))
    }

    @Test
    fun changedDueDateOrAdvanceDaysGetsItsOwnPersistentDeliveryReceipt() {
        val original = PaymentReminderDeliveryStore(isolatedContext)
        assertTrue(original.deliverOnce(identity) { true })
        val variants = listOf(identity.copy(dueDate = identity.dueDate + 86_400_000L),
            identity.copy(reminderDays = 7))
        variants.forEach { changed ->
            val recreated = PaymentReminderDeliveryStore(isolatedContext)
            assertFalse(recreated.wasDelivered(changed))
            assertTrue(recreated.deliverOnce(changed) { true })
            assertFalse(PaymentReminderDeliveryStore(isolatedContext).deliverOnce(changed) { true })
        }
        val recreated = PaymentReminderDeliveryStore(isolatedContext)
        assertTrue(recreated.wasDelivered(identity))
        assertEquals(3, baseContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).all.size)
    }

    @Test
    fun disabledNotificationLeavesNoReceiptAndCanRetryAfterStoreRecreation() {
        val original = PaymentReminderDeliveryStore(isolatedContext)
        assertFalse(original.deliverOnce(identity) { false })
        assertFalse(original.wasDelivered(identity))
        assertTrue(baseContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).all.isEmpty())

        val recreated = PaymentReminderDeliveryStore(isolatedContext)
        assertFalse(recreated.wasDelivered(identity))
        assertTrue(recreated.deliverOnce(identity) { true })
        assertTrue(PaymentReminderDeliveryStore(isolatedContext).wasDelivered(identity))
    }
}
