package com.minthitsaraung.personalaiagent.data.model

/**
 * Holds all user-configurable settings for the app.
 *
 * These are persisted via DataStore (see PreferencesDataSource).
 * Default values are chosen to provide a good out-of-the-box experience.
 *
 * @param userName          The user's name (shown in the system prompt).
 * @param requiredTapCount  How many consecutive taps trigger the assistant.
 * @param maxTapIntervalMs  Max milliseconds between taps for them to count.
 * @param speechRate        TTS speech rate (1.0 = normal, 0.5 = slow, 2.0 = fast).
 * @param speechPitch       TTS pitch (1.0 = normal).
 * @param geminiApiKey      Gemini API key — stored in encrypted SharedPreferences,
 *                          NOT in plain DataStore.
 */
data class UserPreferences(
    val userName: String = "MinThitSarAung",
    val requiredTapCount: Int = 10,
    val maxTapIntervalMs: Long = 500L,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val geminiApiKey: String = ""
)
