package com.gruncode.browserdial

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Everything the app puts in the notification shade.
 *
 * Two channels, because they have opposite purposes: one must be impossible to
 * miss, the other must be impossible to notice.
 */
object Notifications {

    /** Incoming dial requests. Heads-up, so it appears over whatever is on screen. */
    const val CHANNEL_CALLS = "dial_requests"

    /** The permanent "listening" notice Android requires for a foreground service. */
    const val CHANNEL_SERVICE = "subscription_status"

    const val SERVICE_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val calls = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_calls_description)
        }

        // IMPORTANCE_LOW keeps the status notice silent and collapsed. Android
        // will not let a foreground service hide it entirely, so the next best
        // thing is to make it unobtrusive.
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_description)
            setShowBadge(false)
        }

        manager.createNotificationChannel(calls)
        manager.createNotificationChannel(service)
    }

    /** The always-present notice that the subscription is alive. */
    fun serviceNotification(context: Context, connected: Boolean) =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                context.getString(
                    if (connected) R.string.status_listening else R.string.status_reconnecting
                )
            )
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    /**
     * Announce an incoming number.
     *
     * Tapping opens the dialer with the number already filled in. The app stops
     * there by design: the person decides whether the call happens, which also
     * means the app never needs permission to place calls.
     */
    fun showDialRequest(context: Context, number: String) {
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pending = PendingIntent.getActivity(
            context,
            number.hashCode(),
            dial,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.dial_request_title))
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(R.drawable.ic_notification, context.getString(R.string.action_open_dialer), pending)
            .build()

        // Posting without the runtime permission throws on Android 13+, and a
        // missed notification is not worth crashing over.
        try {
            NotificationManagerCompat.from(context).notify(number.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
