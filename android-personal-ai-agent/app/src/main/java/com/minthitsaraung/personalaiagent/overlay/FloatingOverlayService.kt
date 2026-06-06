package com.minthitsaraung.personalaiagent.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.minthitsaraung.personalaiagent.R
import com.minthitsaraung.personalaiagent.ai.OpenRouterService
import com.minthitsaraung.personalaiagent.data.local.PreferencesDataSource
import com.minthitsaraung.personalaiagent.data.model.AiResult
import com.minthitsaraung.personalaiagent.data.model.AssistantState
import com.minthitsaraung.personalaiagent.speech.SpeechEvent
import com.minthitsaraung.personalaiagent.speech.SpeechRecognitionManager
import com.minthitsaraung.personalaiagent.tts.TextToSpeechManager
import com.minthitsaraung.personalaiagent.tts.TtsEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FloatingOverlayService
 * ──────────────────────────────────────────────────────────────────────────────
 * Foreground service that draws a draggable floating window above all apps
 * and orchestrates the full voice → AI → TTS interaction loop.
 *
 * Now uses [OpenRouterService] instead of the old Gemini service.
 * The API key and model ID are read from SharedPreferences on every request
 * (via OpenRouterService → SecureStorageManager), so Settings changes apply
 * immediately without restarting the service.
 */
@AndroidEntryPoint
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlay"
        const val ACTION_ACTIVATE = "ACTION_ACTIVATE"
        const val ACTION_DISMISS  = "ACTION_DISMISS"
        private const val NOTIFICATION_ID = 1001
    }

    @Inject lateinit var speechRecognitionManager: SpeechRecognitionManager
    @Inject lateinit var ttsManager: TextToSpeechManager
    @Inject lateinit var openRouterService: OpenRouterService
    @Inject lateinit var preferencesDataSource: PreferencesDataSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentState = AssistantState.IDLE

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        ttsManager.initialize()
        observeTtsEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACTIVATE -> activate()
            ACTION_DISMISS  -> dismissOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        ttsManager.shutdown()
        speechRecognitionManager.destroy()
        serviceScope.cancel()
    }

    // ─── Activation flow ──────────────────────────────────────────────────────

    private fun activate() {
        showOverlay()
        setState(AssistantState.LISTENING)
        startListening()
    }

    // ─── Voice ────────────────────────────────────────────────────────────────

    private fun startListening() {
        if (!speechRecognitionManager.isAvailable()) {
            showError(getString(R.string.error_speech_not_available))
            return
        }
        serviceScope.launch {
            speechRecognitionManager.startListening()
            speechRecognitionManager.eventFlow.collect { event ->
                when (event) {
                    is SpeechEvent.ReadyForSpeech  -> setState(AssistantState.LISTENING)
                    is SpeechEvent.SpeechStarted   -> updateOverlayText(getString(R.string.ai_state_listening))
                    is SpeechEvent.SpeechEnded     -> setState(AssistantState.THINKING)
                    is SpeechEvent.PartialResult   -> updateOverlayText(event.text)
                    is SpeechEvent.Result -> {
                        setState(AssistantState.THINKING)
                        askAi(event.text)
                        return@collect
                    }
                    is SpeechEvent.Error -> {
                        showError(event.message)
                        return@collect
                    }
                    else -> {}
                }
            }
        }
    }

    // ─── OpenRouter request ───────────────────────────────────────────────────

    private fun askAi(userText: String) {
        serviceScope.launch {
            val prefs = preferencesDataSource.userPreferencesFlow.first()
            updateOverlayText(getString(R.string.ai_state_thinking))

            // API key and model ID are read inside OpenRouterService from
            // SecureStorageManager — always reflects the latest saved Settings.
            val result = openRouterService.generateResponse(
                userMessage = userText,
                userName    = prefs.userName
            )

            when (result) {
                is AiResult.Success -> speakResponse(result.text, prefs.speechRate, prefs.speechPitch)
                is AiResult.Error   -> showError(result.message)
                AiResult.Loading    -> {}
            }
        }
    }

    // ─── TTS ──────────────────────────────────────────────────────────────────

    private fun speakResponse(text: String, speechRate: Float, pitch: Float) {
        setState(AssistantState.SPEAKING)
        updateOverlayText(text)
        val locale = ttsManager.detectLocale(text)
        ttsManager.speak(text, speechRate, pitch, locale)
    }

    private fun observeTtsEvents() {
        serviceScope.launch {
            ttsManager.eventFlow.collect { event ->
                when (event) {
                    is TtsEvent.Started -> setState(AssistantState.SPEAKING)
                    is TtsEvent.Done -> {
                        setState(AssistantState.IDLE)
                        kotlinx.coroutines.delay(2_000)
                        dismissOverlay()
                    }
                    is TtsEvent.Error -> showError(event.message)
                }
            }
        }
    }

    // ─── Overlay window ───────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_assistant, null)
        setupDragging()
        setupDismissButton()
        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            showError(getString(R.string.error_overlay_denied))
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
    }

    private fun dismissOverlay() {
        removeOverlay()
        setState(AssistantState.IDLE)
    }

    private fun setupDragging() {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        overlayView?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = layoutParams?.x ?: 0; iy = layoutParams?.y ?: 0; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.x = ix + (e.rawX - tx).toInt()
                    layoutParams?.y = iy + (e.rawY - ty).toInt()
                    overlayView?.let { windowManager?.updateViewLayout(it, layoutParams) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDismissButton() {
        overlayView?.findViewById<View>(R.id.btn_dismiss_overlay)?.setOnClickListener {
            ttsManager.stop()
            speechRecognitionManager.stopListening()
            dismissOverlay()
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun setState(state: AssistantState) {
        currentState = state
        val label = when (state) {
            AssistantState.IDLE      -> getString(R.string.ai_state_idle)
            AssistantState.LISTENING -> getString(R.string.ai_state_listening)
            AssistantState.THINKING  -> getString(R.string.ai_state_thinking)
            AssistantState.SPEAKING  -> getString(R.string.ai_state_speaking)
        }
        overlayView?.findViewById<TextView>(R.id.tv_status)?.text = label
    }

    private fun updateOverlayText(text: String) {
        overlayView?.findViewById<TextView>(R.id.tv_response)?.text = text
    }

    private fun showError(message: String) {
        updateOverlayText("⚠ $message")
        setState(AssistantState.IDLE)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_ai_brain)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
