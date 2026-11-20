package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AudioQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stream_music_prefs")

data class AppPreferences(
    val darkTheme: Boolean = true,
    val languageCode: String = "id",
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val notificationsEnabled: Boolean = true,
    val equalizerEnabled: Boolean = false,
    val equalizerPresetIndex: Int = 0
)

class SettingsRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val languageKey = stringPreferencesKey("language_code")
    private val audioQualityKey = stringPreferencesKey("audio_quality")
    private val notificationsKey = booleanPreferencesKey("notifications_enabled")
    private val eqEnabledKey = booleanPreferencesKey("eq_enabled")
    private val eqPresetKey = intPreferencesKey("eq_preset")

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            darkTheme = prefs[darkThemeKey] ?: true,
            languageCode = prefs[languageKey] ?: "id",
            audioQuality = AudioQuality.fromKey(prefs[audioQualityKey]),
            notificationsEnabled = prefs[notificationsKey] ?: true,
            equalizerEnabled = prefs[eqEnabledKey] ?: false,
            equalizerPresetIndex = prefs[eqPresetKey] ?: 0
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = enabled }
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[languageKey] = if (code == "en") "en" else "id" }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.dataStore.edit { it[audioQualityKey] = quality.key }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[notificationsKey] = enabled }
    }

    suspend fun setEqualizer(enabled: Boolean, presetIndex: Int) {
        context.dataStore.edit {
            it[eqEnabledKey] = enabled
            it[eqPresetKey] = presetIndex
        }
    }
}
