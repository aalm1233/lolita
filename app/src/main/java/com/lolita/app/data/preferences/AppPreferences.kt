package com.lolita.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lolita.app.ui.screen.common.ViewMode
import com.lolita.app.ui.theme.SkinType
import com.lolita.app.domain.usecase.DailyOutfitReminderConfig
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(context: Context, private val dataStore: DataStore<Preferences> = context.dataStore) {

    val showTotalPrice: Flow<Boolean> = dataStore.data
        .map { it[SHOW_TOTAL_PRICE] ?: false }

    suspend fun setShowTotalPrice(show: Boolean) {
        dataStore.edit { it[SHOW_TOTAL_PRICE] = show }
    }

    val outfitReminderConfig: Flow<DailyOutfitReminderConfig> = dataStore.data.map {
        DailyOutfitReminderConfig(
            enabled = it[OUTFIT_REMINDER_ENABLED] ?: false,
            hour = it[OUTFIT_REMINDER_HOUR]?.takeIf { hour -> hour in 0..23 } ?: 20,
            minute = it[OUTFIT_REMINDER_MINUTE]?.takeIf { minute -> minute in 0..59 } ?: 0
        )
    }.distinctUntilChanged()

    val outfitReminderEnabled: Flow<Boolean> = outfitReminderConfig.map { it.enabled }
    val outfitReminderHour: Flow<Int> = outfitReminderConfig.map { it.hour }
    val outfitReminderMinute: Flow<Int> = outfitReminderConfig.map { it.minute }

    suspend fun setOutfitReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[OUTFIT_REMINDER_ENABLED] = enabled }
    }

    suspend fun setOutfitReminderHour(hour: Int) {
        require(hour in 0..23)
        dataStore.edit { it[OUTFIT_REMINDER_HOUR] = hour }
    }

    suspend fun setOutfitReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        dataStore.edit {
            it[OUTFIT_REMINDER_HOUR] = hour
            it[OUTFIT_REMINDER_MINUTE] = minute
        }
    }

    val skinType: Flow<SkinType> = dataStore.data
        .map {
            try { SkinType.valueOf(it[SKIN_TYPE] ?: "DEFAULT") }
            catch (_: Exception) { SkinType.DEFAULT }
        }

    suspend fun setSkinType(skinType: SkinType) {
        dataStore.edit { it[SKIN_TYPE] = skinType.name }
    }

    val nickname: Flow<String> = dataStore.data
        .map { it[NICKNAME] ?: "" }

    suspend fun setNickname(name: String) {
        dataStore.edit { it[NICKNAME] = name }
    }

    val avatarPath: Flow<String> = dataStore.data
        .map { it[AVATAR_PATH] ?: "" }

    suspend fun setAvatarPath(path: String) {
        dataStore.edit { it[AVATAR_PATH] = path }
    }

    val viewMode: Flow<ViewMode> = dataStore.data
        .map {
            try { ViewMode.valueOf(it[VIEW_MODE] ?: "LIST") }
            catch (_: Exception) { ViewMode.LIST }
        }

    suspend fun setViewMode(mode: ViewMode) {
        dataStore.edit { it[VIEW_MODE] = mode.name }
    }

    val sharedLibraryBaseUrl: Flow<String> = dataStore.data
        .map { it[SHARED_LIBRARY_BASE_URL] ?: "" }

    suspend fun setSharedLibraryBaseUrl(url: String) {
        dataStore.edit { it[SHARED_LIBRARY_BASE_URL] = url.trim() }
    }

    val paymentCalendarBackgroundPath: Flow<String?> = dataStore.data
        .map { it[PAYMENT_CALENDAR_BACKGROUND_PATH] }

    suspend fun setPaymentCalendarBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) {
                prefs[PAYMENT_CALENDAR_BACKGROUND_PATH] = path
            } else {
                prefs.remove(PAYMENT_CALENDAR_BACKGROUND_PATH)
            }
        }
    }

    companion object {
        private val SHOW_TOTAL_PRICE = booleanPreferencesKey("show_total_price")
        private val OUTFIT_REMINDER_ENABLED = booleanPreferencesKey("outfit_reminder_enabled")
        private val OUTFIT_REMINDER_HOUR = intPreferencesKey("outfit_reminder_hour")
        private val OUTFIT_REMINDER_MINUTE = intPreferencesKey("outfit_reminder_minute")
        private val SKIN_TYPE = stringPreferencesKey("skin_type")
        private val NICKNAME = stringPreferencesKey("nickname")
        private val AVATAR_PATH = stringPreferencesKey("avatar_path")
        private val VIEW_MODE = stringPreferencesKey("view_mode")
        private val SHARED_LIBRARY_BASE_URL = stringPreferencesKey("shared_library_base_url")
        private val PAYMENT_CALENDAR_BACKGROUND_PATH = stringPreferencesKey("payment_calendar_background_path")
    }
}
