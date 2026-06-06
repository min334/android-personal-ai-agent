package com.minthitsaraung.personalaiagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.minthitsaraung.personalaiagent.overlay.FloatingOverlayService
import com.minthitsaraung.personalaiagent.utils.Constants
import com.minthitsaraung.personalaiagent.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * TapDetectionAccessibilityService
 * ──────────────────────────────────────────────────────────────────────────────
 * An Android Accessibility Service that passively monitors touch events across
 * the ENTIRE device — including inside other apps — and counts consecutive taps.
 *
 * ─── How it works ─────────────────────────────────────────────────────────────
 * The service receives [AccessibilityEvent.TYPE_VIEW_CLICKED] and
 * [AccessibilityEvent.TYPE_TOUCH_INTERACTION_START] events from the system.
 * For each touch interaction start, we:
 *   1. Check whether it arrived within [maxTapIntervalMs] of the previous tap.
 *   2. If yes, increment [tapCount].
 *   3. If no,  reset [tapCount] to 1 (this is the first tap of a new sequence).
 *   4. Post a delayed reset on the Handler — if no new tap arrives within
 *      [maxTapIntervalMs], the reset fires and clears [tapCount].
 *   5. When [tapCount] reaches [requiredTapCount], trigger the assistant.
 *
 * ─── Why Accessibility Service? ───────────────────────────────────────────────
 * No other Android API allows passive detection of touches across all apps
 * without root access. The accessibility service is the sanctioned approach.
 * We request ONLY the minimal set of permissions (see accessibility_service_config.xml)
 * and explicitly do NOT retrieve window content, preserving user privacy.
 *
 * ─── Battery efficiency ───────────────────────────────────────────────────────
 * The onAccessibilityEvent callback is very lightweight: it only reads a
 * timestamp, increments a counter, and posts/cancels a Handler message.
 * No network calls, no disk I/O, no heavy computation happens here.
 */
@AndroidEntryPoint
class TapDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TapDetection"

        // Broadcast action so the UI can observe service status
        const val ACTION_ASSISTANT_TRIGGERED = "com.minthitsaraung.personalaiagent.ASSISTANT_TRIGGERED"
        const val ACTION_SERVICE_STATUS_CHANGED = "com.minthitsaraung.personalaiagent.SERVICE_STATUS"
        const val EXTRA_IS_RUNNING = "is_running"

        // Shared reference so the UI can check if the service is active
        @Volatile
        var isRunning = false
            private set
    }

    // ─── Tap detection state ──────────────────────────────────────────────────

    /** Number of taps required to trigger the assistant (default 10). */
    private var requiredTapCount: Int = 10

    /** Maximum milliseconds between two taps for them to count as consecutive. */
    private var maxTapIntervalMs: Long = 500L

    /** Current consecutive tap count. */
    private var tapCount = 0

    /** System time of the most recent tap. */
    private var lastTapTimeMs = 0L

    /** Handler on the main thread — used to post a delayed reset runnable. */
    private val handler = Handler(Looper.getMainLooper())

    /** Runnable that resets tapCount after the timeout expires. */
    private val resetRunnable = Runnable {
        if (tapCount > 0) {
            Log.d(TAG, "Tap sequence timed out — resetting counter (was $tapCount)")
            tapCount = 0
            lastTapTimeMs = 0L
        }
    }

    // ─── Vibrator ─────────────────────────────────────────────────────────────
    private var vibrator: Vibrator? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility service connected")

        // Obtain the vibrator service
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // Notify the UI that the service is now running
        sendServiceStatusBroadcast(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(resetRunnable)
        sendServiceStatusBroadcast(false)
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    // ─── Core tap detection ───────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about touch interaction starts.
        // Using TYPE_TOUCH_INTERACTION_START rather than click events because:
        //  - It fires for every finger-down on any surface (not just Views)
        //  - It is not blocked by other apps' window flags
        if (event?.eventType != AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) return

        val now = System.currentTimeMillis()

        // Cancel the pending reset — a new tap arrived in time
        handler.removeCallbacks(resetRunnable)

        if (lastTapTimeMs == 0L || (now - lastTapTimeMs) <= maxTapIntervalMs) {
            // Tap arrived within the allowed interval — count it
            tapCount++
            Log.d(TAG, "Tap $tapCount / $requiredTapCount (interval: ${now - lastTapTimeMs}ms)")
        } else {
            // Gap was too long — restart the sequence from 1
            Log.d(TAG, "Tap interval exceeded (${now - lastTapTimeMs}ms > $maxTapIntervalMs ms) — reset to 1")
            tapCount = 1
        }

        lastTapTimeMs = now

        if (tapCount >= requiredTapCount) {
            // ─── TRIGGER ───────────────────────────────────────────────────────
            Log.i(TAG, "$requiredTapCount consecutive taps detected — activating AI assistant")
            tapCount = 0
            lastTapTimeMs = 0L
            triggerAssistant()
        } else {
            // Post a reset that fires if no new tap arrives within the interval
            handler.postDelayed(resetRunnable, maxTapIntervalMs + 50L)
        }
    }

    // ─── Assistant trigger ────────────────────────────────────────────────────

    /**
     * Called when [requiredTapCount] consecutive taps are detected.
     * Vibrates to confirm activation and starts the FloatingOverlayService.
     */
    private fun triggerAssistant() {
        // Short vibration burst as haptic confirmation
        vibrateActivation()

        // Start the floating overlay service (it will handle voice and AI)
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_ACTIVATE
        }
        startService(overlayIntent)

        // Also broadcast so the MainActivity can update its status display
        sendBroadcast(Intent(ACTION_ASSISTANT_TRIGGERED))
    }

    /**
     * Produces a two-pulse vibration pattern to confirm activation.
     * Pattern: wait 0ms → vibrate 100ms → wait 100ms → vibrate 150ms
     */
    private fun vibrateActivation() {
        vibrator?.let { v ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 100, 150)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 100, 100, 150), -1)
            }
        }
    }

    // ─── Broadcast helpers ────────────────────────────────────────────────────

    private fun sendServiceStatusBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_SERVICE_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Settings update from ViewModel ──────────────────────────────────────

    /**
     * Called by the ViewModel (via a broadcast or binder) to push updated
     * settings into the running service without restarting it.
     */
    fun updateSettings(tapCount: Int, intervalMs: Long) {
        this.requiredTapCount = tapCount.coerceIn(3, 20)
        this.maxTapIntervalMs = intervalMs.coerceIn(200L, 2000L)
        Log.d(TAG, "Settings updated: tapCount=$requiredTapCount, intervalMs=$maxTapIntervalMs")
    }
}
