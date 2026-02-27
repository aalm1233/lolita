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

    val skinType: Flow<SkinType> = context.dataStore.data
        .map {
            try { SkinType.valueOf(it[SKIN_TYPE] ?: "DEFAULT") }
            catch (_: Exception) { SkinType.DEFAULT }
        }

    suspend fun setSkinType(skinType: SkinType) {
        context.dataStore.edit { it[SKIN_TYPE] = skinType.name }
    }

    val nickname: Flow<String> = context.dataStore.data
        .map { it[NICKNAME] ?: "" }

    suspend fun setNickname(name: String) {
        context.dataStore.edit { it[NICKNAME] = name }
    }

    val avatarPath: Flow<String> = context.dataStore.data
        .map { it[AVATAR_PATH] ?: "" }

    suspend fun setAvatarPath(path: String) {
        context.dataStore.edit { it[AVATAR_PATH] = path }
    }

    val viewMode: Flow<ViewMode> = context.dataStore.data
        .map {
            try { ViewMode.valueOf(it[VIEW_MODE] ?: "LIST") }
            catch (_: Exception) { ViewMode.LIST }
        }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[VIEW_MODE] = mode.name }
    }

    companion object {
        private val SHOW_TOTAL_PRICE = booleanPreferencesKey("show_total_price")
        private val OUTFIT_REMINDER_ENABLED = booleanPreferencesKey("outfit_reminder_enabled")
        private val OUTFIT_REMINDER_HOUR = intPreferencesKey("outfit_reminder_hour")
        private val SKIN_TYPE = stringPreferencesKey("skin_type")
        private val NICKNAME = stringPreferencesKey("nickname")
        private val AVATAR_PATH = stringPreferencesKey("avatar_path")
        private val VIEW_MODE = stringPreferencesKey("view_mode")
    }
}
