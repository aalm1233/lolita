package com.lolita.app

import android.app.Application
import com.lolita.app.di.AppModule

class LolitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(applicationContext)
    }
}
