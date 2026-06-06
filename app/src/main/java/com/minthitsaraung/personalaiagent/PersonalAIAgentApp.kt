package com.minthitsaraung.personalaiagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — the entry point of the app.
 *
 * @HiltAndroidApp triggers Hilt's code generation and sets up the
 * dependency injection component tree for the whole application.
 *
 * We also create the persistent notification channel here because channels
 * must exist before any notification is posted (Android 8.0+).
 */
@HiltAndroidApp
class PersonalAIAgentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Creates the notification channel required for the foreground service.
     * On Android 8.0+ (API 26+) apps must register channels before posting notifications.
     * Calling this multiple times is safe — Android ignores duplicate registrations.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW   // LOW = no sound, but persistent
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
