package com.minthitsaraung.personalaiagent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver
 * ──────────────────────────────────────────────────────────────────────────────
 * Receives the BOOT_COMPLETED broadcast when the device finishes starting up.
 *
 * We use this to remind the user (via a notification or log) that the
 * Accessibility Service needs to be re-enabled if it was disabled.
 *
 * Note: The Accessibility Service itself is managed by Android and persists
 * across reboots as long as the user leaves it enabled in system settings.
 * This receiver exists as a safety net in case the user needs to be reminded.
 *
 * For a more advanced implementation you could start the overlay service here,
 * but we leave that to the Accessibility Service to avoid permission issues.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Device booted — Personal AI Agent is ready")

        // The Accessibility Service re-attaches automatically after boot
        // if it was enabled before shutdown. No action needed here, but this
        // is a good place to re-schedule any WorkManager jobs or show a
        // "tap 10 times to activate" reminder notification.
    }
}
