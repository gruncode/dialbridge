package com.gruncode.browserdial

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives dial requests over Firebase Cloud Messaging.
 *
 * This is the alternative to [SubscriberService]: instead of the app holding
 * its own connection, it borrows the one Google Play Services already keeps
 * open. That is far kinder to the battery, which is why it is offered — and it
 * is the route to the Play Store, where an app that maintains a permanent
 * socket is treated with suspicion.
 *
 * The privacy cost of that convenience is paid by encryption: what arrives here
 * is ciphertext, so the relay and Google carry a number they cannot read. The
 * key never leaves this device and the paired browser.
 */
class PushService : FirebaseMessagingService() {

    /**
     * Firebase hands out a new token on install, on data-clear, and
     * occasionally on its own. Storing it keeps the pairing code accurate; the
     * user still has to re-pair, because the browser holds the old one.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Prefs.setFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Only the ciphertext travels; there is deliberately no field here
        // containing a readable number.
        val payload = message.data["payload"]
        if (payload.isNullOrBlank()) {
            Log.w(TAG, "push without a payload; ignored")
            return
        }

        val secret = Prefs.secret(applicationContext)
        if (secret.isBlank()) {
            Log.w(TAG, "push arrived before pairing; ignored")
            return
        }

        val number = Crypto.decrypt(secret, payload)
        if (number == null) {
            // Either someone forged a message to this token, or the browser is
            // still paired with a key this phone has since replaced.
            Log.w(TAG, "push failed authentication; ignored")
            return
        }

        if (!NUMBER_SHAPE.matches(number)) {
            Log.w(TAG, "decrypted content was not a phone number; ignored")
            return
        }

        Notifications.showDialRequest(applicationContext, number)
    }

    companion object {
        private const val TAG = "BrowserDial"

        /** E.164: an optional plus, then 6 to 15 digits, and nothing else. */
        private val NUMBER_SHAPE = Regex("^\\+?[0-9]{6,15}$")
    }
}
