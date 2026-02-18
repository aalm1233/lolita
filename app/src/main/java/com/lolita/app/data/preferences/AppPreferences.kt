package com.lolita.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(private val context: Context) {

    val showTotalPrice: Flow<Boolean> = context.dataStore.data
        .map { it[SHOW_TOTAL_PRICE] ?: false }

    suspend fun setShowTotalPrice(show: Boolean) {
        context.dataStore.edit { it[SHOW_TOTAL_PRICE] = show }
    }

    val outfitReminderEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[OUTFIT_REMINDER_ENABLED] ?: false }

    val outfitReminderHour: Flow<Int> = context.dataStore.data
        .map { it[OUTFIT_REMINDER_HOUR] ?: 20 }

    suspend fun setOutfitReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OUTFIT_REMINDER_ENABLED] = enabled }
    }

    suspend fun setOutfitReminderHour(hour: Int) {
        context.dataStore.edit { it[OUTFIT_REMINDER_HOUR] = hour }
    }

    companion object {
        private val SHOW_TOTAL_PRICE = booleanPreferencesKey("show_total_price")
        private val OUTFIT_REMINDER_ENABLED = booleanPreferencesKey("outfit_reminder_enabled")
        private val OUTFIT_REMINDER_HOUR = intPreferencesKey("outfit_reminder_hour")
    }
}
