package com.lolita.app.data.notification

import android.content.Context
import android.util.Log
import com.lolita.app.domain.usecase.PaymentReminderDelivery
import com.lolita.app.domain.usecase.PaymentReminderIdentity

/** Local delivery receipts survive process restarts and are separate from purchase evidence. */
class PaymentReminderDeliveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "payment_reminder_deliveries", Context.MODE_PRIVATE
    )
    private val delivery = PaymentReminderDelivery(::wasDelivered) { identity ->
        // Commit before finishing the receiver so a process restart cannot lose the receipt.
        if (!preferences.edit().putBoolean(identity.storageKey, true).commit()) {
            Log.w("PaymentReminder", "Could not persist reminder delivery receipt")
        }
    }

    fun wasDelivered(identity: PaymentReminderIdentity): Boolean =
        preferences.getBoolean(identity.storageKey, false)

    fun deliverOnce(identity: PaymentReminderIdentity, showNotification: () -> Boolean): Boolean =
        delivery.deliverOnce(identity, showNotification)
}
