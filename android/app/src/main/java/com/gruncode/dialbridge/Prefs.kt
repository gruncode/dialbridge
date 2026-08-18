package com.gruncode.dialbridge

import android.content.Context
import java.security.SecureRandom

/**
 * The app's entire configuration: which server to listen to, and on which
 * topic. Kept in one small object so the activity, the service and the boot
 * receiver cannot drift apart on what a setting is called.
 */
object Prefs {

    private const val FILE = "dialbridge"
    private const val KEY_SERVER = "server"
    private const val KEY_TOPIC = "topic"
    private const val KEY_TOKEN = "token"
    private const val KEY_RUNNING = "running"

    const val DEFAULT_SERVER = "https://ntfy.sh"

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

    /** Whether the user wants the subscription running; survives reboots. */
    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    fun setRunning(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RUNNING, value).apply()

    /**
     * Invent a topic name.
     *
     * The topic is the only thing standing between a stranger and the ability
     * to make this phone ring, so it is generated from a cryptographic random
     * source and made long enough that guessing it is hopeless. It is not a
     * password — it travels in the URL — which is why the README recommends a
     * self-hosted server with access tokens for anyone who wants real secrecy.
     */
    fun generateTopic(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789" // no look-alike glyphs
        val random = SecureRandom()
        val builder = StringBuilder("dial-")
        repeat(20) { builder.append(alphabet[random.nextInt(alphabet.length)]) }
        return builder.toString()
    }
}
