package com.gruncode.dialbridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gruncode.dialbridge.databinding.ActivityMainBinding

/**
 * The only screen. It exists to do three things: hand the user a topic to
 * paste into the browser, start and stop the subscription, and be honest about
 * whether it is currently connected.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var views: ActivityMainBinding

    // Asked for on Android 13+; without it the dial notifications are silently
    // dropped and the app appears to do nothing at all.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.battery_explain, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)

        Notifications.createChannels(this)

        views.serverInput.setText(Prefs.server(this))
        views.topicInput.setText(Prefs.topic(this))
        views.tokenInput.setText(Prefs.token(this))

        views.generateButton.setOnClickListener {
            views.topicInput.setText(Prefs.generateTopic())
            persist()
        }

        views.copyButton.setOnClickListener { copyTopic() }

        views.toggleButton.setOnClickListener { toggleSubscription() }

        views.batteryButton.setOnClickListener { requestBatteryExemption() }

        askForNotificationPermission()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        // Save on leaving rather than behind a button: people close the app
        // expecting their typing to have stuck, and it is cheap to oblige.
        persist()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Copy the current settings out of the form and into storage. */
    private fun persist() {
        Prefs.setServer(this, views.serverInput.text.toString())
        Prefs.setTopic(this, views.topicInput.text.toString())
        Prefs.setToken(this, views.tokenInput.text.toString())
    }

    private fun copyTopic() {
        val topic = views.topicInput.text.toString().trim()
        if (topic.isEmpty()) {
            Toast.makeText(this, R.string.status_needs_topic, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DialBridge topic", topic))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSubscription() {
        persist()

        if (Prefs.isRunning(this)) {
            Prefs.setRunning(this, false)
            SubscriberService.stop(this)
        } else {
            if (Prefs.topic(this).isBlank()) {
                Toast.makeText(this, R.string.status_needs_topic, Toast.LENGTH_SHORT).show()
                return
            }
            Prefs.setRunning(this, true)
            SubscriberService.start(this)
        }
        refresh()
    }

    /** Make the button and the status line agree with reality. */
    private fun refresh() {
        val running = Prefs.isRunning(this)
        views.toggleButton.setText(if (running) R.string.action_stop else R.string.action_start)
        views.statusText.setText(
            when {
                !running -> R.string.status_stopped
                Prefs.topic(this).isBlank() -> R.string.status_needs_topic
                else -> R.string.status_listening
            }
        )
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Send the user to the battery-optimisation screen.
     *
     * Deliberately the settings screen rather than the direct request dialog:
     * the direct one is discouraged for apps that are not obviously entitled to
     * it, and this way the user sees exactly what they are agreeing to.
     */
    private fun requestBatteryExemption() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            // Some manufacturers remove the screen; fall back to app details.
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }
}
