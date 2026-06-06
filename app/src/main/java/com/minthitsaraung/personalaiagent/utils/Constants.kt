package com.minthitsaraung.personalaiagent.utils

/**
 * App-wide constants.
 *
 * Centralising constants here avoids magic strings/numbers scattered through
 * the codebase and makes changes easier to apply.
 */
object Constants {
    // ─── Tap detection defaults ───────────────────────────────────────────────
    const val DEFAULT_TAP_COUNT = 10
    const val DEFAULT_TAP_INTERVAL_MS = 500L
    const val MIN_TAP_COUNT = 3
    const val MAX_TAP_COUNT = 20
    const val MIN_TAP_INTERVAL_MS = 200L
    const val MAX_TAP_INTERVAL_MS = 2000L

    // ─── TTS defaults ─────────────────────────────────────────────────────────
    const val DEFAULT_SPEECH_RATE = 1.0f
    const val DEFAULT_SPEECH_PITCH = 1.0f

    // ─── Gemini API ───────────────────────────────────────────────────────────
    const val GEMINI_MODEL = "gemini-1.5-flash"
    const val GEMINI_MAX_OUTPUT_TOKENS = 1024
    const val GEMINI_TEMPERATURE = 0.7f

    // ─── Default user ─────────────────────────────────────────────────────────
    const val DEFAULT_USER_NAME = "MinThitSarAung"

    // ─── Notification ─────────────────────────────────────────────────────────
    const val OVERLAY_NOTIFICATION_ID = 1001
}
