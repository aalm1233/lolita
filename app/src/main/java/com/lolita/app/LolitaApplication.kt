package com.lolita.app

import android.app.Application
import com.lolita.app.data.notification.NotificationChannelSetup
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LolitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(applicationContext)
        NotificationChannelSetup.createNotificationChannel(applicationContext)
        NotificationChannelSetup.createOutfitNotificationChannel(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyOutfitReminderScheduler(applicationContext).reschedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("LolitaApplication", "Failed to recover outfit reminder", e)
            }
            try {
                AppModule.backupManager().recoverPendingReminders()
                AppModule.orderRepository().reconcileReminders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("LolitaApplication", "Failed to refresh pending balance status", e)
            }
        }
    }
}
