package com.minthitsaraung.personalaiagent.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.minthitsaraung.personalaiagent.data.model.OpenRouterModels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecureStorageManager
 * ──────────────────────────────────────────────────────────────────────────────
 * Persists sensitive and user-configurable AI settings using two storage tiers:
 *
 * 1. **EncryptedSharedPreferences** (Android Keystore-backed AES-256-GCM)
 *    → Stores the OpenRouter API key. Encrypted both at rest and in transit
 *      within the device. The encryption key lives in the Android Keystore
 *      and cannot be extracted even on rooted devices (on supported hardware).
 *
 * 2. **Plain SharedPreferences**
 *    → Stores the selected Model ID. This is not sensitive — it is a public
 *      model identifier like "google/gemini-2.0-flash-exp". No need to encrypt.
 *
 * ─── Why two stores? ──────────────────────────────────────────────────────────
 * EncryptedSharedPreferences is noticeably slower than plain SharedPreferences
 * because every read/write involves a cryptographic operation. Using it only for
 * secrets (the API key) keeps the non-sensitive settings reads fast.
 */
@Singleton
class SecureStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ENCRYPTED_PREFS_FILE = "secure_prefs"
        private const val PLAIN_PREFS_FILE     = "ai_settings"

        // Keys — encrypted store
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"

        // Keys — plain store
        private const val KEY_MODEL_ID = "openrouter_model_id"
    }

    // ─── Encrypted SharedPreferences (API key) ────────────────────────────────

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Plain SharedPreferences (model ID) ───────────────────────────────────

    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PLAIN_PREFS_FILE, Context.MODE_PRIVATE)
    }

    // ─── API Key ──────────────────────────────────────────────────────────────

    /**
     * Save (or update) the OpenRouter API key in encrypted storage.
     * Call this from the Settings "Save" button handler.
     */
    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_OPENROUTER_API_KEY, apiKey.trim()).apply()
    }

    /**
     * Retrieve the stored OpenRouter API key.
     * Returns an empty string if the user has not configured one yet.
     */
    fun getApiKey(): String =
        encryptedPrefs.getString(KEY_OPENROUTER_API_KEY, null)?.trim() ?: ""

    /** Returns true if an API key has been saved. */
    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    /** Remove the stored API key (e.g. on reset). */
    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_OPENROUTER_API_KEY).apply()
    }

    // ─── Model ID ─────────────────────────────────────────────────────────────

    /**
     * Save the selected OpenRouter model ID.
     * Example: "google/gemini-2.0-flash-exp"
     *
     * Stored in plain SharedPreferences because model IDs are not sensitive.
     */
    fun saveModelId(modelId: String) {
        plainPrefs.edit().putString(KEY_MODEL_ID, modelId.trim()).apply()
    }

    /**
     * Retrieve the stored model ID.
     * Defaults to [OpenRouterModels.DEFAULT_MODEL] if none has been saved.
     */
    fun getModelId(): String =
        plainPrefs.getString(KEY_MODEL_ID, null)?.takeIf { it.isNotBlank() }
            ?: OpenRouterModels.DEFAULT_MODEL

    /**
     * Returns a masked version of the API key for display in the UI
     * (shows only the last 4 characters to confirm something is saved).
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey()
        return when {
            key.length > 8 -> "sk-or-••••••••${key.takeLast(4)}"
            key.isNotBlank() -> "••••"
            else -> ""
        }
    }
}
