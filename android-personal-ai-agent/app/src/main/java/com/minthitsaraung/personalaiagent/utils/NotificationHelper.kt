package com.minthitsaraung.personalaiagent.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.minthitsaraung.personalaiagent.R
import com.minthitsaraung.personalaiagent.ui.activity.MainActivity

/**
 * NotificationHelper — builds and posts notifications.
 *
 * Keeping notification building in a helper class avoids duplicating the
 * NotificationCompat.Builder boilerplate across multiple services.
 */
object NotificationHelper {

    /**
     * Build the persistent foreground service notification shown while the
     * overlay / accessibility service is running.
     *
     * @param context  Application context.
     * @return         A [Notification] ready to pass to startForeground().
     */
    fun buildServiceNotification(context: Context): Notification {
        // Tapping the notification opens MainActivity
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_ai_brain)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)       // Cannot be swiped away
            .setShowWhen(false)     // No timestamp — it's a persistent status notification
            .build()
    }

    /** Post or update a notification by [id]. */
    fun postNotification(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    /** Cancel a notification by [id]. */
    fun cancelNotification(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
