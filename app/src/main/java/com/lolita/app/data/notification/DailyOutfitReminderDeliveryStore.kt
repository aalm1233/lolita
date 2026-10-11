package com.lolita.app.data.notification

import android.content.Context
import android.util.Log
import com.lolita.app.domain.usecase.DailyOutfitReminderDelivery
import java.time.LocalDate

/** Local notification receipts are separate from clothing and outfit records. */
class DailyOutfitReminderDeliveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "daily_outfit_reminder_deliveries", Context.MODE_PRIVATE
    )
    private val delivery = DailyOutfitReminderDelivery(::wasDelivered) { date ->
        if (!preferences.edit().putBoolean(date.toString(), true).commit()) {
            Log.w("DailyOutfitReminder", "Could not persist notification delivery receipt")
        }
    }

    fun wasDelivered(date: LocalDate): Boolean = preferences.getBoolean(date.toString(), false)

    fun deliverOnce(date: LocalDate, showNotification: () -> Boolean): Boolean =
        delivery.deliverOnce(date, showNotification)
}
