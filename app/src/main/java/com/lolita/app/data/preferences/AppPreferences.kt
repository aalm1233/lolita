package com.lolita.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    companion object {
        private val SHOW_TOTAL_PRICE = booleanPreferencesKey("show_total_price")
    }
}
