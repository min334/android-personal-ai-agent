package com.minthitsaraung.personalaiagent.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TextToSpeechManager
 * ──────────────────────────────────────────────────────────────────────────────
 * Wraps Android's [TextToSpeech] API and exposes playback state as a Flow.
 *
 * ─── Design decisions ─────────────────────────────────────────────────────────
 * • TTS is initialized lazily — we don't create it until the first speak() call.
 *   This avoids an unnecessary TTS engine start during app launch.
 * • Before speaking a new response, we always call stop() to cancel any ongoing
 *   speech. This prevents garbled output when responses come in quickly.
 * • We use a coroutine Channel to emit TTS lifecycle events so the overlay UI
 *   can show the "Speaking…" state and dismiss itself when done.
 *
 * ─── Language fallback ────────────────────────────────────────────────────────
 * We detect the response language and try to set TTS accordingly. If the device's
 * TTS engine doesn't support the language, we fall back to English.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "TextToSpeech"
        private const val UTTERANCE_ID = "ai_response"
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _eventChannel = Channel<TtsEvent>(Channel.UNLIMITED)
    val eventFlow: Flow<TtsEvent> = _eventChannel.receiveAsFlow()

    // ─── Initialization ───────────────────────────────────────────────────────

    /**
     * Initialize the TTS engine. Call this before [speak].
     * The callback fires when the engine is ready (or fails).
     *
     * It is safe to call this multiple times — if already initialized it returns immediately.
     */
    fun initialize(onReady: (Boolean) -> Unit = {}) {
        if (isInitialized && tts != null) {
            onReady(true)
            return
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureEngine()
                Log.d(TAG, "TTS initialized successfully")
                onReady(true)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
                _eventChannel.trySend(TtsEvent.Error("Text-to-speech initialization failed."))
                onReady(false)
            }
        }
    }

    private fun configureEngine() {
        tts?.apply {
            // Set utterance progress listener to emit lifecycle events
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                    _eventChannel.trySend(TtsEvent.Started)
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS done: $utteranceId")
                    _eventChannel.trySend(TtsEvent.Done)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error: $utteranceId")
                    _eventChannel.trySend(TtsEvent.Error("TTS playback failed."))
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS error ($errorCode): $utteranceId")
                    _eventChannel.trySend(TtsEvent.Error("TTS playback error (code: $errorCode)."))
                }
            })
        }
    }

    // ─── Speak ────────────────────────────────────────────────────────────────

    /**
     * Speak [text] aloud.
     *
     * Stops any currently playing speech before starting the new utterance.
     *
     * @param text       The text to read aloud.
     * @param speechRate 1.0 = normal, 0.5 = half speed, 2.0 = double speed.
     * @param pitch      1.0 = normal, <1 = lower, >1 = higher.
     * @param locale     Locale for the speech. Falls back to English if unsupported.
     */
    fun speak(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        locale: Locale = Locale.ENGLISH
    ) {
        if (!isInitialized || tts == null) {
            initialize { ready ->
                if (ready) speak(text, speechRate, pitch, locale)
                else _eventChannel.trySend(TtsEvent.Error("TTS not ready."))
            }
            return
        }

        // Stop any ongoing speech
        stop()

        tts?.apply {
            // Try to set the requested language; fall back to English
            val result = setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language $locale not supported — falling back to English")
                setLanguage(Locale.ENGLISH)
            }

            setSpeechRate(speechRate.coerceIn(0.25f, 3.0f))
            setPitch(pitch.coerceIn(0.5f, 2.0f))

            // QUEUE_FLUSH clears any pending speech before adding ours
            speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    /**
     * Detect whether [text] is likely Burmese and return the appropriate Locale.
     * Burmese Unicode block: U+1000–U+109F
     */
    fun detectLocale(text: String): Locale {
        val burmesePattern = Regex("[\u1000-\u109F]")
        return if (burmesePattern.containsMatchIn(text)) {
            Locale("my", "MM")   // Burmese (Myanmar)
        } else {
            Locale.ENGLISH
        }
    }

    // ─── Controls ─────────────────────────────────────────────────────────────

    /** Immediately stop any ongoing speech. */
    fun stop() {
        tts?.stop()
    }

    /** Check if TTS is currently speaking. */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /** Check if TTS is available and initialized. */
    fun isReady(): Boolean = isInitialized && tts != null

    /** Release TTS resources — call from onDestroy. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _eventChannel.close()
    }
}

// ─── TTS Events ───────────────────────────────────────────────────────────────

sealed class TtsEvent {
    data object Started : TtsEvent()
    data object Done : TtsEvent()
    data class Error(val message: String) : TtsEvent()
}
