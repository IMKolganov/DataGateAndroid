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
import com.imkolganov.datagate.R

class VpnController(
    private val activity: Activity,
    private val permissionLauncher: ActivityResultLauncher<Intent>,
    private val onStateChange: (VpnStatusUiState) -> Unit,
    private val getState: () -> VpnStatusUiState
) {
    private var isReceiverRegistered = false
    private var pendingConfigText: String? = null
    private var pendingWssLink: String? = null
    private var pendingLinkProtocol: VpnLinkProtocol? = null
    private val prefs = activity.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "OpenVPN3"
        private const val KEY_SESSION_SERVER_ID = "vpn_session_server_id"
    }

    private val receiver = VpnStatusBroadcastReceiver { eventName, eventInfo ->
        if (eventName == "DISCONNECTED") {
            prefs.edit { remove(KEY_SESSION_SERVER_ID) }
        }
        val newState = VpnEventMapper.map(activity.resources, getState(), eventName, eventInfo)
        onStateChange(newState)
        Log.d(TAG, "VPN status updated: $eventName - $eventInfo")
    }

    fun onStart() {
        registerReceiver()

        var next = getState()
        var changed = false
        val cachedName = prefs.getString("selected_server_name", null)
        if (!cachedName.isNullOrBlank() && next.selectedServerName.isNullOrBlank()) {
            next = next.copy(selectedServerName = cachedName)
            changed = true
        }
        val cachedSessionId = prefs.getInt(KEY_SESSION_SERVER_ID, -1)
        if (cachedSessionId >= 0 && next.selectedServerId == null) {
            next = next.copy(selectedServerId = cachedSessionId)
            changed = true
        }
        if (changed) onStateChange(next)

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
        val displayMessage = eventInfo?.takeIf { it.isNotBlank() } ?: eventName

        val next = when (eventName) {
            "SELECTED_SERVER" -> {
                val name = eventInfo?.takeIf { it.isNotBlank() }
                if (name != null) {
                    prefs.edit().putString("selected_server_name", name).apply()
                }
                getState().copy(
                    selectedServerName = name,
                    lastMessage = activity.getString(R.string.vpn_server_selected),
                    isConnectRequested = true,
                    isVpnConnected = false
                )
            }
            else -> {
                val newSession = eventName == "SELECTING_SERVER"
                getState().copy(
                    lastMessage = displayMessage,
                    isConnectRequested = true,
                    isVpnConnected = if (newSession) false else getState().isVpnConnected
                )
            }
        }

        onStateChange(next)
    }

    /**
     * Call when a concrete server has been chosen for the upcoming VPN session (before tunnel is up).
     * Keeps [VpnStatusUiState.selectedServerId] in sync with Access / switch-server logic.
     */
    fun notifyServerSelectedForConnection(serverId: Int, serverName: String) {
        prefs.edit {
            putString("selected_server_name", serverName)
            putInt(KEY_SESSION_SERVER_ID, serverId)
        }
        onStateChange(
            getState().copy(
                selectedServerName = serverName,
                selectedServerId = serverId,
                lastMessage = activity.getString(R.string.vpn_server_selected),
                isConnectRequested = true,
                isVpnConnected = false
            )
        )
    }

    fun startWithConfig(configText: String, wssLink: String, linkProtocol: VpnLinkProtocol) {
        // Do not block on isConnectRequested here because it may already be set by pre-connect flow.
        // The real connection state will be driven by OpenVPN core events.

        pendingConfigText = configText
        pendingWssLink = wssLink
        pendingLinkProtocol = linkProtocol

        Log.d(TAG, "Calling VpnService.prepare()")
        val prepareIntent = VpnService.prepare(activity)
        Log.d(TAG, "Prepare intent is null: ${prepareIntent == null}")

        if (prepareIntent != null) {
            Log.d(TAG, "VPN permission is not granted yet, requesting...")
            permissionLauncher.launch(prepareIntent)
            onStateChange(
                getState().copy(
                    isConnectRequested = true,
                    lastMessage = activity.getString(R.string.vpn_waiting_permission)
                )
            )
            return
        }

        Log.d(TAG, "VPN permission already granted, starting service...")
        startServiceWithConfig(configText, wssLink, linkProtocol)
    }

    fun onPermissionGranted() {
        Log.d(TAG, "VPN permission granted from launcher")

        val cfg = pendingConfigText
        val wss = pendingWssLink
        val linkProtocol = pendingLinkProtocol
        pendingConfigText = null
        pendingWssLink = null
        pendingLinkProtocol = null

        if (cfg.isNullOrBlank() || wss.isNullOrBlank()) {
            showError(activity.getString(R.string.vpn_error_permission_missing_config))
            return
        }

        startServiceWithConfig(cfg, wss, linkProtocol ?: VpnLinkProtocol.TCP)
    }

    fun onPermissionDenied() {
        Log.w(TAG, "VPN permission denied from launcher")
        pendingConfigText = null
        pendingWssLink = null
        pendingLinkProtocol = null
        showError(activity.getString(R.string.vpn_error_permission_denied))
    }

    fun requestDisconnect() {
        prefs.edit {
            remove("selected_server_name")
            remove(KEY_SESSION_SERVER_ID)
        }

        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_DISCONNECT
        }

        startServiceCompat(intent)

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                isVpnConnected = false,
                selectedServerName = null,
                selectedServerId = null,
                lastMessage = activity.getString(R.string.vpn_disconnecting)
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

    private fun startServiceWithConfig(configText: String, wssLink: String, linkProtocol: VpnLinkProtocol) {
        val serverDisplayName =
            getState().selectedServerName?.takeIf { it.isNotBlank() }
                ?: prefs.getString("selected_server_name", null)?.takeIf { it.isNotBlank() }

        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_CONNECT
            putExtra(OpenVpn3Service.EXTRA_OVPN_CONFIG, configText)
            putExtra(OpenVpn3Service.EXTRA_WSS_URL, wssLink)
            putExtra(OpenVpn3Service.EXTRA_LINK_PROTOCOL, linkProtocol.intentValue())
            if (serverDisplayName != null) {
                putExtra(OpenVpn3Service.EXTRA_SERVER_DISPLAY_NAME, serverDisplayName)
            }
        }

        startServiceCompat(intent)

        onStateChange(
            getState().copy(
                isConnectRequested = true,
                lastMessage = activity.getString(R.string.vpn_connecting_generic)
            )
        )
    }

    private fun startServiceCompat(intent: Intent) {
        val isConnect = intent.action == OpenVpn3Service.ACTION_CONNECT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isConnect) {
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
        prefs.edit {
            remove("selected_server_name")
            remove(KEY_SESSION_SERVER_ID)
        }

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                isVpnConnected = false,
                selectedServerName = null,
                selectedServerId = null,
                lastMessage = message
            )
        )
    }
}
