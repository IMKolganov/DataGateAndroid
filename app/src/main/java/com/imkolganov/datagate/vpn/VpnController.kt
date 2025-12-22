package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit

class VpnController(
    private val activity: Activity,
    private val permissionLauncher: ActivityResultLauncher<Intent>,
    private val onStateChange: (VpnStatusUiState) -> Unit,
    private val getState: () -> VpnStatusUiState
) {
    private var isReceiverRegistered = false
    private var pendingConfigText: String? = null
    private val prefs = activity.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "OpenVPN3"
    }

    private val receiver = VpnStatusBroadcastReceiver { eventName, eventInfo ->
        val newState = VpnEventMapper.map(getState(), eventName, eventInfo)
        onStateChange(newState)
        Log.d(TAG, "VPN status updated: $eventName - $eventInfo")
    }

    fun onStart() {
        registerReceiver()

        val cachedName = prefs.getString("selected_server_name", null)
        if (!cachedName.isNullOrBlank() && getState().selectedServerName.isNullOrBlank()) {
            onStateChange(getState().copy(selectedServerName = cachedName))
        }

        requestCurrentStatus()
    }

    fun onStop() {
        if (isReceiverRegistered) {
            @Suppress("DEPRECATION")
            activity.unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    /**
     * Used for pre-connect steps: selecting server, reading IDs, downloading config, etc.
     * This only updates the message and does not toggle isConnectRequested.
     */
    fun showStatus(eventName: String, eventInfo: String? = null) {
        val baseMessage = if (!eventInfo.isNullOrBlank()) "$eventName: $eventInfo" else eventName

        val next = when (eventName) {
            "SELECTED_SERVER" -> {
                val name = eventInfo?.takeIf { it.isNotBlank() }
                if (name != null) {
                    prefs.edit().putString("selected_server_name", name).apply()
                }
                getState().copy(
                    selectedServerName = name,
                    lastMessage = "Server selected"
                )
            }
            else -> getState().copy(lastMessage = baseMessage)
        }

        onStateChange(next)
    }

    fun startWithConfig(configText: String) {
        // Do not block on isConnectRequested here because it may already be set by pre-connect flow.
        // The real connection state will be driven by OpenVPN core events.

        pendingConfigText = configText

        Log.d(TAG, "Calling VpnService.prepare()")
        val prepareIntent = VpnService.prepare(activity)
        Log.d(TAG, "Prepare intent is null: ${prepareIntent == null}")

        if (prepareIntent != null) {
            Log.d(TAG, "VPN permission is not granted yet, requesting...")
            permissionLauncher.launch(prepareIntent)
            onStateChange(
                getState().copy(
                    isConnectRequested = true,
                    lastMessage = "Waiting for VPN permission..."
                )
            )
            return
        }

        Log.d(TAG, "VPN permission already granted, starting service...")
        startServiceWithConfig(configText)
    }

    fun onPermissionGranted() {
        Log.d(TAG, "VPN permission granted from launcher")

        val cfg = pendingConfigText
        pendingConfigText = null
        if (cfg.isNullOrBlank()) {
            showError("VPN permission granted, but config is missing")
            return
        }

        startServiceWithConfig(cfg)
    }

    fun onPermissionDenied() {
        Log.w(TAG, "VPN permission denied from launcher")
        pendingConfigText = null
        showError("VPN permission denied")
    }

    fun requestDisconnect() {
        prefs.edit { remove("selected_server_name") }

        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_DISCONNECT
        }

        startServiceCompat(intent)

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                selectedServerName = null,
                lastMessage = "Disconnecting..."
            )
        )
    }

    fun sendTestBroadcast() {
        val testIntent = Intent(OpenVpn3Service.ACTION_STATUS)
            .setPackage(activity.packageName)
            .apply {
                putExtra(OpenVpn3Service.EXTRA_EVENT_NAME, "TEST")
                putExtra(OpenVpn3Service.EXTRA_EVENT_INFO, "Hello from Activity")
            }
        activity.sendBroadcast(testIntent)
    }

    private fun startServiceWithConfig(configText: String) {
        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_CONNECT
            putExtra(OpenVpn3Service.EXTRA_OVPN_CONFIG, configText)
        }

        startServiceCompat(intent)

        onStateChange(
            getState().copy(
                isConnectRequested = true,
                lastMessage = "Connecting..."
            )
        )
    }

    private fun startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }

    private fun requestCurrentStatus() {
        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_QUERY_STATUS
        }
        startServiceCompat(intent)
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return

        val filter = IntentFilter(OpenVpn3Service.ACTION_STATUS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }

        isReceiverRegistered = true
    }

    fun showError(message: String) {
        prefs.edit { remove("selected_server_name") }

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                selectedServerName = null,
                lastMessage = message
            )
        )
    }
}
