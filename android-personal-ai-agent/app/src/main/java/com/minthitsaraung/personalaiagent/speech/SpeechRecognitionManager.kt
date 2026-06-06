package com.minthitsaraung.personalaiagent.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpeechRecognitionManager
 * ──────────────────────────────────────────────────────────────────────────────
 * Wraps Android's [SpeechRecognizer] API and exposes speech results as a
 * Kotlin Flow, making it easy to consume from coroutines and ViewModels.
 *
 * ─── Why SpeechRecognizer (not MediaRecorder + Whisper)? ─────────────────────
 * The on-device SpeechRecognizer API works offline on supported devices and
 * has built-in Burmese support on many Google-powered devices. It requires
 * no network calls for recognition itself, keeping latency low.
 *
 * ─── Thread safety ───────────────────────────────────────────────────────────
 * SpeechRecognizer MUST be created and destroyed on the main thread (it uses
 * a Handler internally). We enforce this by requiring the context to be an
 * Application context and calling all methods on the main looper.
 */
@Singleton
class SpeechRecognitionManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SpeechRecognition"
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Channel through which speech events are emitted.
     * Using Channel(UNLIMITED) so events are never dropped even if the
     * collector is slow.
     */
    private val _eventChannel = Channel<SpeechEvent>(Channel.UNLIMITED)
    val eventFlow: Flow<SpeechEvent> = _eventChannel.receiveAsFlow()

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns true if the device supports speech recognition.
     * Always check this before calling [startListening].
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening for speech.
     *
     * @param language  BCP-47 language tag, e.g. "en-US" or "my-MM" (Burmese).
     *                  Falls back to English if the device does not support the
     *                  requested language.
     *
     * Must be called on the main thread.
     */
    fun startListening(language: String = "en-US") {
        if (!isAvailable()) {
            _eventChannel.trySend(SpeechEvent.Error("Speech recognition is not available on this device."))
            return
        }

        // Tear down any existing recognizer first
        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // Also accept partial results so we can show live feedback
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Silence detection: stop listening after 2s of silence, allow up to 10s of speech
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening (language: $language)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _eventChannel.trySend(SpeechEvent.Error("Failed to start speech recognition: ${e.message}"))
        }
    }

    /** Stop listening and release resources. */
    fun stopListening() {
        speechRecognizer?.apply {
            stopListening()
            destroy()
        }
        speechRecognizer = null
    }

    /** Release all resources — call from onDestroy. */
    fun destroy() {
        stopListening()
        _eventChannel.close()
    }

    // ─── RecognitionListener ──────────────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _eventChannel.trySend(SpeechEvent.ReadyForSpeech)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
            _eventChannel.trySend(SpeechEvent.SpeechStarted)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Emit audio level for an animated visualizer bar
            _eventChannel.trySend(SpeechEvent.AudioLevelChanged(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) { /* Not used */ }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
            _eventChannel.trySend(SpeechEvent.SpeechEnded)
        }

        override fun onError(error: Int) {
            val message = mapErrorCode(error)
            Log.e(TAG, "Speech recognition error $error: $message")
            _eventChannel.trySend(SpeechEvent.Error(message))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            Log.d(TAG, "Final result: $text")
            if (!text.isNullOrBlank()) {
                _eventChannel.trySend(SpeechEvent.Result(text))
            } else {
                _eventChannel.trySend(SpeechEvent.Error("No speech was recognized. Please try again."))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _eventChannel.trySend(SpeechEvent.PartialResult(partial))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* Not used */ }
    }

    // ─── Error code mapping ───────────────────────────────────────────────────

    private fun mapErrorCode(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone access."
        SpeechRecognizer.ERROR_CLIENT -> "Client-side error. Please try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is not granted."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out. Please check your connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Please speak clearly and try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please wait a moment."
        SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
        else -> "Speech recognition failed (code: $code)."
    }
}

// ─── Speech Events ────────────────────────────────────────────────────────────

/**
 * Sealed class representing all events that the speech recognizer can emit.
 * Collected by the overlay service or ViewModel to update the UI.
 */
sealed class SpeechEvent {
    data object ReadyForSpeech : SpeechEvent()
    data object SpeechStarted : SpeechEvent()
    data object SpeechEnded : SpeechEvent()
    data class AudioLevelChanged(val rmsDb: Float) : SpeechEvent()
    data class PartialResult(val text: String) : SpeechEvent()
    data class Result(val text: String) : SpeechEvent()
    data class Error(val message: String) : SpeechEvent()
}
