package com.gruncode.dialbridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Holds the connection that makes the whole thing work.
 *
 * ntfy exposes a topic as an endless HTTP response: one JSON object per line,
 * arriving as events happen. Reading that stream is the entire subscription —
 * no push service, no Google Play Services, no account anywhere.
 *
 * The cost of owning the connection instead of borrowing Google's is battery:
 * this keeps a socket open. In practice ntfy sends a keepalive roughly every
 * 45 seconds, which is cheap, but it is an honest trade and the README says so.
 */
class SubscriberService : Service() {

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notice to appear promptly after starting, so it
        // goes up before any network work begins.
        startForeground(
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.serviceNotification(this, connected = false)
        )

        if (!running) {
            running = true
            worker = thread(name = "dialbridge-subscriber") { listenForever() }
        }

        // START_STICKY: if the system reclaims the process under pressure, it
        // should bring the service back — a dial request that never arrives is
        // the one failure mode users will not forgive.
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    /** Reconnect loop. Backs off after failures, resets once a stream opens. */
    private fun listenForever() {
        var backoffMs = 2_000L
        val maxBackoffMs = 120_000L

        while (running) {
            val topic = Prefs.topic(this)
            if (topic.isBlank()) {
                // Nothing to listen to yet; wait for the user to pair.
                sleepQuietly(5_000)
                continue
            }

            try {
                openStream(Prefs.server(this), topic, Prefs.token(this))
                // The stream ended without throwing — treat it as a normal
                // disconnect and retry promptly.
                backoffMs = 2_000L
            } catch (interrupted: InterruptedException) {
                return
            } catch (error: Exception) {
                Log.w(TAG, "subscription dropped: ${error.message}")
                updateStatus(connected = false)
                sleepQuietly(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            }
        }
    }

    /** Read one connection's worth of events, returning when it closes. */
    private fun openStream(server: String, topic: String, token: String) {
        val url = URL("$server/$topic/json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // Time out the handshake, but never the read: an idle stream is
            // the normal state of affairs here, not a fault.
            connectTimeout = 15_000
            readTimeout = 0
            setRequestProperty("Accept", "application/x-ndjson")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("server replied $code")
            }

            updateStatus(connected = true)

            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                while (running) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) handleEvent(line)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Turn one line of the stream into a notification, if it is a dial request. */
    private fun handleEvent(line: String) {
        val event = try {
            JSONObject(line)
        } catch (_: Exception) {
            return // not JSON; ignore rather than crash the listener
        }

        // ntfy also sends "open" and "keepalive" events down the same stream.
        if (event.optString("event") != "message") return

        val payload = event.optString("message").trim()
        if (payload.isEmpty()) return

        val secret = Prefs.secret(this)
        if (secret.isBlank()) {
            Log.w(TAG, "message arrived before pairing; ignored")
            return
        }

        // The topic is public knowledge to anyone who learns its name, so the
        // decryption step is also the authentication step: a message that does
        // not verify was not written by the paired browser.
        val number = Crypto.decrypt(secret, payload)
        if (number == null) {
            Log.w(TAG, "message failed authentication; ignored")
            return
        }

        if (!NUMBER_SHAPE.matches(number)) {
            Log.w(TAG, "decrypted content was not a phone number; ignored")
            return
        }

        Notifications.showDialRequest(this, number)
    }

    private fun updateStatus(connected: Boolean) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(
                Notifications.SERVICE_NOTIFICATION_ID,
                Notifications.serviceNotification(this, connected)
            )
        } catch (_: Exception) {
        }
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val TAG = "DialBridge"

        /** E.164: an optional plus, then 6 to 15 digits, and nothing else. */
        private val NUMBER_SHAPE = Regex("^\\+?[0-9]{6,15}$")

        fun start(context: Context) {
            val intent = Intent(context, SubscriberService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SubscriberService::class.java))
        }
    }
}
