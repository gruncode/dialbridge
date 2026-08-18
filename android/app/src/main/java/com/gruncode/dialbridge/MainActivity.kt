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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gruncode.dialbridge.databinding.ActivityMainBinding

/**
 * The only screen. It hands the user a pairing code for the browser, lets them
 * choose how messages should be delivered, and — because this app processes
 * personal data — lets them delete everything it holds.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var views: ActivityMainBinding

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
        views.tokenInput.setText(Prefs.token(this))
        views.relayInput.setText(Prefs.relay(this))

        val usingFcm = Prefs.transport(this) == Prefs.TRANSPORT_FCM
        views.radioFcm.isChecked = usingFcm
        views.radioNtfy.isChecked = !usingFcm

        views.transportGroup.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setTransport(
                this,
                if (checkedId == R.id.radioFcm) Prefs.TRANSPORT_FCM else Prefs.TRANSPORT_NTFY
            )
            applyTransport()
        }

        views.generateButton.setOnClickListener { generatePairing() }
        views.copyButton.setOnClickListener { copyPairing() }
        views.toggleButton.setOnClickListener { toggleSubscription() }
        views.batteryButton.setOnClickListener { requestBatteryExemption() }
        views.deleteDataButton.setOnClickListener { confirmDeleteEverything() }

        askForNotificationPermission()
        applyTransport()
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

    private fun persist() {
        Prefs.setServer(this, views.serverInput.text.toString())
        Prefs.setToken(this, views.tokenInput.text.toString())
        Prefs.setRelay(this, views.relayInput.text.toString())
    }

    /** Show only the settings that belong to the chosen delivery route. */
    private fun applyTransport() {
        val fcm = Prefs.transport(this) == Prefs.TRANSPORT_FCM
        views.ntfyBlock.visibility = if (fcm) View.GONE else View.VISIBLE
        views.fcmBlock.visibility = if (fcm) View.VISIBLE else View.GONE
        views.toggleButton.visibility = if (fcm) View.GONE else View.VISIBLE
        views.transportExplain.setText(
            if (fcm) R.string.transport_explain_fcm else R.string.transport_explain_ntfy
        )
        if (fcm) refreshFirebaseToken()
        refresh()
    }

    /**
     * Ask Firebase for this install's token.
     *
     * Wrapped defensively: a build without a google-services.json has no
     * Firebase configuration, and asking for a token then throws. That is a
     * supported state, not a bug — it is the F-Droid build.
     */
    private fun refreshFirebaseToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Prefs.setFcmToken(this, token)
                    views.fcmStatus.setText(R.string.fcm_ready)
                }
                .addOnFailureListener {
                    views.fcmStatus.setText(R.string.fcm_missing)
                }
        } catch (_: Throwable) {
            views.fcmStatus.setText(R.string.fcm_missing)
        }
    }

    /**
     * Create the pairing material: a fresh encryption key, and — on the ntfy
     * route — a fresh topic. Generating again invalidates the old pairing,
     * which is the intended way to revoke a computer's access.
     */
    private fun generatePairing() {
        persist()

        Prefs.setSecret(this, Crypto.newKey())
        if (Prefs.transport(this) == Prefs.TRANSPORT_NTFY) {
            Prefs.setTopic(this, Prefs.generateTopic())
        }

        val code = Prefs.pairingCode(this)
        if (code == null) {
            views.pairingCode.setText(R.string.pairing_incomplete)
        } else {
            views.pairingCode.text = code
            Toast.makeText(this, R.string.pairing_hint, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun copyPairing() {
        val code = Prefs.pairingCode(this)
        if (code == null) {
            Toast.makeText(this, R.string.status_needs_topic, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DialBridge pairing", code))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSubscription() {
        persist()

        if (Prefs.isRunning(this)) {
            Prefs.setRunning(this, false)
            SubscriberService.stop(this)
        } else {
            if (Prefs.topic(this).isBlank() || Prefs.secret(this).isBlank()) {
                Toast.makeText(this, R.string.status_needs_topic, Toast.LENGTH_SHORT).show()
                return
            }
            Prefs.setRunning(this, true)
            SubscriberService.start(this)
        }
        refresh()
    }

    /** Make the buttons and the status line agree with reality. */
    private fun refresh() {
        val fcm = Prefs.transport(this) == Prefs.TRANSPORT_FCM
        val running = Prefs.isRunning(this)

        views.toggleButton.setText(if (running) R.string.action_stop else R.string.action_start)
        views.statusText.setText(
            when {
                fcm -> R.string.status_push_mode
                !running -> R.string.status_stopped
                Prefs.secret(this).isBlank() -> R.string.status_needs_topic
                else -> R.string.status_listening
            }
        )

        val code = Prefs.pairingCode(this)
        if (code != null && views.pairingCode.text.isNullOrBlank()) {
            views.pairingCode.text = code
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestBatteryExemption() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    /**
     * The right to erasure, as a button rather than a paragraph.
     *
     * Also deletes the Firebase registration where one exists, so the device
     * stops being addressable through Google after the user asks it to stop.
     */
    private fun confirmDeleteEverything() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_confirm_yes) { _, _ -> deleteEverything() }
            .show()
    }

    private fun deleteEverything() {
        SubscriberService.stop(this)

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
        } catch (_: Throwable) {
            // No Firebase in this build; nothing registered to delete.
        }

        Prefs.wipe(this)

        views.serverInput.setText(Prefs.DEFAULT_SERVER)
        views.tokenInput.setText("")
        views.relayInput.setText("")
        views.pairingCode.text = ""
        views.radioNtfy.isChecked = true

        Toast.makeText(this, R.string.deleted, Toast.LENGTH_LONG).show()
        applyTransport()
    }
}
