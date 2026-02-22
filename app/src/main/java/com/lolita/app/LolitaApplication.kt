package com.lolita.app

import android.app.Application
import com.lolita.app.data.notification.NotificationChannelSetup
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LolitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(applicationContext)
        NotificationChannelSetup.createNotificationChannel(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            AppModule.database().itemDao().refreshPendingBalanceStatus()
        }
    }
}
