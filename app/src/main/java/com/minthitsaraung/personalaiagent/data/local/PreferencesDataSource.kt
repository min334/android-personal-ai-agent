package com.minthitsaraung.personalaiagent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minthitsaraung.personalaiagent.data.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property to create a single DataStore instance per app process.
// Using a companion object pattern avoids creating multiple DataStore instances.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_prefs")

/**
 * Data source that reads and writes non-sensitive user preferences.
 *
 * We use Jetpack DataStore (Preferences) as a modern, coroutine-friendly
 * replacement for SharedPreferences.
 *
 * IMPORTANT: The Gemini API key is NOT stored here because DataStore stores
 * data in plain text on disk. The API key goes through [SecureStorageManager]
 * which uses Android's EncryptedSharedPreferences.
 */
@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ─── Preference Keys ──────────────────────────────────────────────────────
    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val REQUIRED_TAP_COUNT = intPreferencesKey("required_tap_count")
        val MAX_TAP_INTERVAL_MS = longPreferencesKey("max_tap_interval_ms")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Emits [UserPreferences] whenever any preference changes.
     * Callers collect this Flow in their ViewModel/Repository.
     */
    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            userName = prefs[Keys.USER_NAME] ?: "MinThitSarAung",
            requiredTapCount = prefs[Keys.REQUIRED_TAP_COUNT] ?: 10,
            maxTapIntervalMs = prefs[Keys.MAX_TAP_INTERVAL_MS] ?: 500L,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 1.0f
        )
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setRequiredTapCount(count: Int) {
        context.dataStore.edit { it[Keys.REQUIRED_TAP_COUNT] = count.coerceIn(3, 20) }
    }

    suspend fun setMaxTapIntervalMs(ms: Long) {
        context.dataStore.edit { it[Keys.MAX_TAP_INTERVAL_MS] = ms.coerceIn(200L, 2000L) }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.25f, 3.0f) }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.5f, 2.0f) }
    }
}
