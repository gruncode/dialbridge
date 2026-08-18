package com.gruncode.browserdial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the subscription after a restart.
 *
 * Without this the app would look broken in the most ordinary situation there
 * is: the phone was rebooted and nobody thought to reopen the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Only resume if the user had it running when the phone went down.
        if (Prefs.isRunning(context) && Prefs.topic(context).isNotBlank()) {
            SubscriberService.start(context)
        }
    }
}
