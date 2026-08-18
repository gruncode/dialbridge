package com.gruncode.dialbridge

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom

/**
 * The app's entire stored state.
 *
 * Worth listing in full, because the privacy policy has to be true: a server
 * address, a topic name, an encryption key, a Firebase token, and two flags.
 * No phone numbers are ever written here — a received number lives in a
 * notification and nowhere else.
 */
object Prefs {

    private const val FILE = "dialbridge"
    private const val KEY_SERVER = "server"
    private const val KEY_TOPIC = "topic"
    private const val KEY_TOKEN = "token"
    private const val KEY_SECRET = "secret"
    private const val KEY_RUNNING = "running"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_RELAY = "relay"
    private const val KEY_FCM = "fcm_token"

    const val DEFAULT_SERVER = "https://ntfy.sh"

    /** Delivery route: the phone's own connection, or Google's push service. */
    const val TRANSPORT_NTFY = "ntfy"
    const val TRANSPORT_FCM = "fcm"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun server(context: Context): String =
        prefs(context).getString(KEY_SERVER, DEFAULT_SERVER)
            ?.trimEnd('/')
            ?.ifBlank { DEFAULT_SERVER }
            ?: DEFAULT_SERVER

    fun setServer(context: Context, value: String) =
        prefs(context).edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    fun topic(context: Context): String =
        prefs(context).getString(KEY_TOPIC, "").orEmpty()

    fun setTopic(context: Context, value: String) =
        prefs(context).edit().putString(KEY_TOPIC, value.trim()).apply()

    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun setToken(context: Context, value: String) =
        prefs(context).edit().putString(KEY_TOKEN, value.trim()).apply()

    /** The shared AES key, base64url. Created locally, never transmitted. */
    fun secret(context: Context): String =
        prefs(context).getString(KEY_SECRET, "").orEmpty()

    fun setSecret(context: Context, value: String) =
        prefs(context).edit().putString(KEY_SECRET, value).apply()

    fun transport(context: Context): String =
        prefs(context).getString(KEY_TRANSPORT, TRANSPORT_NTFY) ?: TRANSPORT_NTFY

    fun setTransport(context: Context, value: String) =
        prefs(context).edit().putString(KEY_TRANSPORT, value).apply()

    fun relay(context: Context): String =
        prefs(context).getString(KEY_RELAY, "").orEmpty()

    fun setRelay(context: Context, value: String) =
        prefs(context).edit().putString(KEY_RELAY, value.trim().trimEnd('/')).apply()

    fun fcmToken(context: Context): String =
        prefs(context).getString(KEY_FCM, "").orEmpty()

    fun setFcmToken(context: Context, value: String) =
        prefs(context).edit().putString(KEY_FCM, value).apply()

    /** Whether the user wants the ntfy subscription running; survives reboots. */
    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    fun setRunning(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RUNNING, value).apply()

    /**
     * Erasure. Removes every stored value, so the app holds nothing about the
     * user or their computer. Backing the GDPR right to erasure with an actual
     * button matters more than describing it in a policy.
     */
    fun wipe(context: Context) =
        prefs(context).edit().clear().apply()

    /**
     * Invent a topic name.
     *
     * Under the ntfy transport the topic is the delivery address, so it is
     * generated from a cryptographic source and made long enough that guessing
     * it is hopeless. It is not the secret that protects the number — the
     * encryption key does that — but a stranger who guessed it could still
     * make the phone buzz.
     */
    fun generateTopic(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789" // no look-alike glyphs
        val random = SecureRandom()
        val builder = StringBuilder("dial-")
        repeat(20) { builder.append(alphabet[random.nextInt(alphabet.length)]) }
        return builder.toString()
    }

    /**
     * Build the pairing code the browser needs: where to deliver, and the key
     * to encrypt with. One opaque string, because pairing is the step where
     * users give up if asked to copy three fields correctly.
     */
    fun pairingCode(context: Context): String? {
        val secret = secret(context)
        if (secret.isBlank()) return null

        val json = JSONObject().apply {
            put("v", 1)
            put("k", secret)
            if (transport(context) == TRANSPORT_FCM) {
                val fcm = fcmToken(context)
                val relay = relay(context)
                if (fcm.isBlank() || relay.isBlank()) return null
                put("t", TRANSPORT_FCM)
                put("r", relay)
                put("d", fcm)
            } else {
                val topic = topic(context)
                if (topic.isBlank()) return null
                put("t", TRANSPORT_NTFY)
                put("s", server(context))
                put("c", topic)
                if (token(context).isNotBlank()) put("a", token(context))
            }
        }
        return Crypto.encodePairing(json.toString())
    }
}
