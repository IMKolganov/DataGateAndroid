package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import com.imkolganov.datagate.logger.VpnDebugLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import com.imkolganov.datagate.R
import com.imkolganov.datagate.vpn.xray.XrayVpnService
import java.io.File

class VpnController(
    private val activity: Activity,
    private val permissionLauncher: ActivityResultLauncher<Intent>,
    private val onStateChange: (VpnStatusUiState) -> Unit,
    private val getState: () -> VpnStatusUiState
) {
    private var isReceiverRegistered = false
    private var pendingConfigText: String? = null
    private var pendingWssLink: String? = null
    private var pendingTransport: VpnTransport = VpnTransport.Wss
    private var pendingLinkProtocol: VpnLinkProtocol? = null
    private var pendingBypassRoutes: List<IpCidrRoute> = emptyList()
    private var pendingUsername: String = ""
    private var pendingPassword: String = ""
    private var pendingXrayConfigText: String? = null
    private var pendingEngine: VpnEngine = VpnEngine.OpenVpn
    private val prefs = activity.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "OpenVPN3"
        private const val KEY_SESSION_SERVER_ID = "vpn_session_server_id"
        private const val KEY_ACTIVE_ENGINE = "vpn_active_engine"
    }

    private enum class VpnEngine { OpenVpn, Xray }

    private var pendingCommandRollback: VpnStatusUiState? = null

    /**
     * Set when we intentionally stop the other engine during a connect (OpenVPN↔Xray).
     * The peer's DISCONNECTED must not clear [VpnStatusUiState.isConnectRequested] / selection.
     */
    @Volatile
    private var expectPeerEngineDisconnect: Boolean = false

    private val receiver = VpnStatusBroadcastReceiver { eventName, eventInfo, fromQuery ->
        val current = getState()
        VpnCommandContract.parseRejectedCommand(eventName)?.let { rejected ->
            val rollback = pendingCommandRollback ?: current
            pendingCommandRollback = null
            val next = VpnCommandContract.applyCommandRejected(rollback, rejected, eventInfo)
            onStateChange(next)
            VpnDebugLogger.event(
                category = "ui.command",
                action = "rejected",
                details = mapOf("event" to eventName, "info" to eventInfo),
            )
            VpnDebugLogger.w(TAG, "VPN command rejected: $eventName ($eventInfo), rolled back UI")
            return@VpnStatusBroadcastReceiver
        }
        val broadcast = VpnLifecyclePolicy.StatusBroadcast(
            eventName = eventName,
            eventInfo = eventInfo,
            fromQuery = fromQuery,
        )
        var mapped = VpnLifecyclePolicy.applyStatusBroadcast(
            current = current,
            broadcast = broadcast,
            mapEvent = { state, name, info ->
                VpnEventMapper.map(activity.resources, state, name, info)
            },
        )
        if (mapped == null) {
            VpnDebugLogger.d(TAG, "Ignoring idle query DISCONNECTED over in-flight connect UI")
            return@VpnStatusBroadcastReceiver
        }
        if (VpnCommandContract.isAuthoritativeTunnelEvent(eventName)) {
            pendingCommandRollback = null
        }
        // Peer stop while switching engines: mapper clears a connected session; restore in-flight UI.
        if (eventName == "DISCONNECTED" && !fromQuery && expectPeerEngineDisconnect) {
            expectPeerEngineDisconnect = false
            mapped = mapped.copy(
                isConnectRequested = true,
                selectedServerId = current.selectedServerId,
                selectedServerName = current.selectedServerName,
            )
        } else if (
            eventName.equals("CONNECTED", ignoreCase = true) ||
            eventName.equals("ERROR", ignoreCase = true) ||
            eventName.equals("TUN_SETUP_FAILED", ignoreCase = true)
        ) {
            expectPeerEngineDisconnect = false
        }
        // Only drop session id when the session is truly over. Peer-engine DISCONNECTED
        // during an in-flight connect keeps isConnectRequested and must keep the id.
        if (eventName == "DISCONNECTED" && !fromQuery && !mapped.isConnectRequested) {
            prefs.edit { remove(KEY_SESSION_SERVER_ID) }
        }
        onStateChange(mapped)
        VpnDebugLogger.event(
            category = "ui.status",
            action = eventName,
            details = mapOf(
                "info" to eventInfo,
                "fromQuery" to fromQuery,
                "connected" to mapped.isVpnConnected,
                "paused" to mapped.isVpnPaused,
                "connectRequested" to mapped.isConnectRequested,
            ),
        )
        VpnDebugLogger.d(TAG, "VPN status updated: $eventName - $eventInfo (fromQuery=$fromQuery)")
    }

    fun onStart() {
        registerReceiver()
        updatePermissionState()

        val cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
            selectedServerName = prefs.getString("selected_server_name", null),
            sessionServerId = prefs.getInt(KEY_SESSION_SERVER_ID, -1).takeIf { it >= 0 },
            lastEventName = prefs.getString(OpenVpn3Service.PREF_LAST_EVENT_NAME, null),
            lastEventInfo = prefs.getString(OpenVpn3Service.PREF_LAST_EVENT_INFO, null),
        )
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = getState(),
            cached = cached,
            mapEvent = { state, name, info ->
                VpnEventMapper.map(activity.resources, state, name, info)
            },
        )
        if (restored != getState()) {
            onStateChange(restored)
        }
        queryServiceStatus()
    }

    /** Ask the running VPN service for its authoritative runtime state. */
    fun queryServiceStatus() {
        when (activeEngine()) {
            VpnEngine.Xray -> {
                val intent = Intent(activity, XrayVpnService::class.java).apply {
                    action = XrayVpnService.ACTION_QUERY_STATUS
                }
                runCatching { startServiceCompat(intent) }
                    .onFailure { VpnDebugLogger.w(TAG, "Failed to query Xray service status", it) }
            }
            VpnEngine.OpenVpn -> {
                val intent = Intent(activity, OpenVpn3Service::class.java).apply {
                    action = OpenVpn3Service.ACTION_QUERY_STATUS
                }
                runCatching { startServiceCompat(intent) }
                    .onFailure { VpnDebugLogger.w(TAG, "Failed to query VPN service status", it) }
            }
        }
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
        // Keep Access tab selection in sync (same prefs file, different key).
        VpnServerSelectionStore.setSelectedServerId(activity, serverId)
        VpnServerSelectionStore.setMode(activity, ServerSelectionMode.MANUAL)
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

    /** Local profile / non-catalog session — display name only, no Access server id. */
    fun notifyProfileSelectedForConnection(profileName: String) {
        prefs.edit {
            putString("selected_server_name", profileName)
            remove(KEY_SESSION_SERVER_ID)
        }
        onStateChange(
            getState().copy(
                selectedServerName = profileName,
                selectedServerId = null,
                lastMessage = activity.getString(R.string.vpn_server_selected),
                isConnectRequested = true,
                isVpnConnected = false
            )
        )
    }

    /**
     * Starts VPN with a prepared OVPN config.
     * [wssLink] is required when [transport] is [VpnTransport.Wss]; ignored for Direct.
     */
    fun startWithConfig(
        configText: String,
        wssLink: String?,
        linkProtocol: VpnLinkProtocol,
        bypassRoutes: List<IpCidrRoute> = emptyList(),
        transport: VpnTransport = if (wssLink.isNullOrBlank()) VpnTransport.Direct else VpnTransport.Wss,
        username: String = "",
        password: String = "",
    ) {
        // Do not block on isConnectRequested here because it may already be set by pre-connect flow.
        // The real connection state will be driven by OpenVPN core events.

        pendingEngine = VpnEngine.OpenVpn
        pendingConfigText = configText
        pendingXrayConfigText = null
        pendingWssLink = wssLink
        pendingTransport = transport
        pendingLinkProtocol = linkProtocol
        pendingBypassRoutes = bypassRoutes
        pendingUsername = username
        pendingPassword = password

        VpnDebugLogger.event(
            category = "ui.user",
            action = "start_with_config",
            details = mapOf(
                "proto" to linkProtocol.name,
                "transport" to transport.name,
                "wssHost" to wssLink?.let { runCatching { java.net.URI(it).host }.getOrNull() },
                "configBytes" to configText.length,
                "excludeRoutes" to bypassRoutes.size,
            ),
        )
        requestVpnPermissionOrStart {
            startServiceWithConfig(
                configText = configText,
                wssLink = wssLink,
                transport = transport,
                linkProtocol = linkProtocol,
                bypassRoutes = bypassRoutes,
                username = username,
                password = password,
            )
        }
    }

    /**
     * Starts VPN with a normalized Xray outbounds JSON (or full config with outbounds).
     * [bypassRoutes] are applied via [VpnService.Builder.excludeRoute] on Android 13+
     * (same IP list as OpenVPN). On Android 12 and below they are injected into Xray routing
     * as `direct` rules (freedom + [VpnService.protect]).
     */
    fun startWithXrayConfig(
        configText: String,
        bypassRoutes: List<IpCidrRoute> = emptyList(),
    ) {
        pendingEngine = VpnEngine.Xray
        pendingXrayConfigText = configText
        pendingConfigText = null
        pendingWssLink = null
        pendingLinkProtocol = null
        pendingBypassRoutes = bypassRoutes
        pendingUsername = ""
        pendingPassword = ""

        VpnDebugLogger.event(
            category = "ui.user",
            action = "start_with_xray_config",
            details = mapOf(
                "configBytes" to configText.length,
                "excludeRoutes" to bypassRoutes.size,
            ),
        )
        requestVpnPermissionOrStart {
            startXrayServiceWithConfig(configText, bypassRoutes)
        }
    }

    private fun requestVpnPermissionOrStart(onReady: () -> Unit) {
        VpnDebugLogger.d(TAG, "Calling VpnService.prepare()")
        val prepareIntent = VpnService.prepare(activity)
        VpnDebugLogger.d(TAG, "Prepare intent is null: ${prepareIntent == null}")

        if (prepareIntent != null) {
            VpnDebugLogger.event("ui.user", "request_vpn_permission")
            VpnDebugLogger.d(TAG, "VPN permission is not granted yet, requesting...")
            permissionLauncher.launch(prepareIntent)
            onStateChange(
                getState().copy(
                    isConnectRequested = true,
                    lastMessage = activity.getString(R.string.vpn_waiting_permission)
                )
            )
            return
        }

        VpnDebugLogger.d(TAG, "VPN permission already granted, starting service...")
        onReady()
    }

    fun onPermissionGranted() {
        VpnDebugLogger.d(TAG, "VPN permission granted from launcher")
        updatePermissionState()

        val engine = pendingEngine
        val xrayCfg = pendingXrayConfigText
        val cfg = pendingConfigText
        val wss = pendingWssLink
        val transport = pendingTransport
        val linkProtocol = pendingLinkProtocol
        val bypassRoutes = pendingBypassRoutes
        val username = pendingUsername
        val password = pendingPassword
        clearPendingConnect()

        when (engine) {
            VpnEngine.Xray -> {
                if (xrayCfg.isNullOrBlank()) {
                    showError(activity.getString(R.string.vpn_error_permission_missing_config))
                    return
                }
                startXrayServiceWithConfig(xrayCfg, bypassRoutes)
            }
            VpnEngine.OpenVpn -> {
                val missingWss = transport == VpnTransport.Wss && wss.isNullOrBlank()
                if (cfg.isNullOrBlank() || missingWss) {
                    showError(activity.getString(R.string.vpn_error_permission_missing_config))
                    return
                }
                startServiceWithConfig(
                    configText = cfg,
                    wssLink = wss,
                    transport = transport,
                    linkProtocol = linkProtocol ?: VpnLinkProtocol.TCP,
                    bypassRoutes = bypassRoutes,
                    username = username,
                    password = password,
                )
            }
        }
    }

    fun onPermissionDenied() {
        VpnDebugLogger.w(TAG, "VPN permission denied from launcher")
        updatePermissionState()
        clearPendingConnect()
        showError(activity.getString(R.string.vpn_error_permission_denied))
    }

    fun requestDisconnect() {
        VpnDebugLogger.event("ui.user", "disconnect")
        pendingCommandRollback = null
        expectPeerEngineDisconnect = false
        prefs.edit {
            remove("selected_server_name")
            remove(KEY_SESSION_SERVER_ID)
            remove(KEY_ACTIVE_ENGINE)
        }

        startServiceCompat(
            Intent(activity, OpenVpn3Service::class.java).apply {
                action = OpenVpn3Service.ACTION_DISCONNECT
            }
        )
        startServiceCompat(
            Intent(activity, XrayVpnService::class.java).apply {
                action = XrayVpnService.ACTION_DISCONNECT
            }
        )

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                selectedServerName = null,
                selectedServerId = null,
                lastMessage = activity.getString(R.string.vpn_disconnecting)
            )
        )
    }

    fun requestPause() {
        val current = getState()
        if (!VpnCommandContract.canRequestPauseFromUi(current)) {
            VpnDebugLogger.event(
                category = "ui.user",
                action = "pause_ignored",
                details = mapOf(
                    "connected" to current.isVpnConnected,
                    "paused" to current.isVpnPaused,
                    "pending" to current.pendingUserCommand?.name,
                ),
            )
            VpnDebugLogger.w(TAG, "Ignoring pause request in state connected=${current.isVpnConnected} paused=${current.isVpnPaused} pending=${current.pendingUserCommand}")
            return
        }
        // Xray v1 has no pause — treat as disconnect.
        if (activeEngine() == VpnEngine.Xray) {
            VpnDebugLogger.event("ui.user", "pause_as_disconnect_xray")
            requestDisconnect()
            return
        }
        VpnDebugLogger.event("ui.user", "pause")
        pendingCommandRollback = current
        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_PAUSE
        }
        startServiceCompat(intent)
        onStateChange(VpnCommandContract.beginPauseRequest(current))
    }

    fun requestResume() {
        val current = getState()
        if (!VpnCommandContract.canRequestResumeFromUi(current)) {
            VpnDebugLogger.event(
                category = "ui.user",
                action = "resume_ignored",
                details = mapOf(
                    "paused" to current.isVpnPaused,
                    "pending" to current.pendingUserCommand?.name,
                ),
            )
            VpnDebugLogger.w(TAG, "Ignoring resume request in state paused=${current.isVpnPaused} pending=${current.pendingUserCommand}")
            return
        }
        if (activeEngine() == VpnEngine.Xray) {
            VpnDebugLogger.w(TAG, "Ignoring resume: Xray has no pause/resume")
            return
        }
        VpnDebugLogger.event("ui.user", "resume")
        pendingCommandRollback = current
        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_RESUME
        }
        startServiceCompat(intent)
        onStateChange(VpnCommandContract.beginResumeRequest(current))
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

    private fun clearPendingConnect() {
        pendingConfigText = null
        pendingXrayConfigText = null
        pendingWssLink = null
        pendingTransport = VpnTransport.Wss
        pendingLinkProtocol = null
        pendingBypassRoutes = emptyList()
        pendingUsername = ""
        pendingPassword = ""
        pendingEngine = VpnEngine.OpenVpn
    }

    private fun activeEngine(): VpnEngine =
        when (prefs.getString(KEY_ACTIVE_ENGINE, null)) {
            VpnEngine.Xray.name -> VpnEngine.Xray
            else -> VpnEngine.OpenVpn
        }

    private fun setActiveEngine(engine: VpnEngine) {
        prefs.edit { putString(KEY_ACTIVE_ENGINE, engine.name) }
    }

    /** Always disconnect the other VPN engine before starting [keep]. */
    private fun stopPeerEngine(keep: VpnEngine) {
        expectPeerEngineDisconnect = true
        when (keep) {
            VpnEngine.OpenVpn -> {
                startServiceCompat(
                    Intent(activity, XrayVpnService::class.java).apply {
                        action = XrayVpnService.ACTION_DISCONNECT
                    }
                )
            }
            VpnEngine.Xray -> {
                startServiceCompat(
                    Intent(activity, OpenVpn3Service::class.java).apply {
                        action = OpenVpn3Service.ACTION_DISCONNECT
                    }
                )
            }
        }
    }

    private fun startServiceWithConfig(
        configText: String,
        wssLink: String?,
        transport: VpnTransport,
        linkProtocol: VpnLinkProtocol,
        bypassRoutes: List<IpCidrRoute>,
        username: String,
        password: String,
    ) {
        stopPeerEngine(VpnEngine.OpenVpn)
        setActiveEngine(VpnEngine.OpenVpn)
        val serverDisplayName =
            getState().selectedServerName?.takeIf { it.isNotBlank() }
                ?: prefs.getString("selected_server_name", null)?.takeIf { it.isNotBlank() }
        val configFile = writeConfigForService(configText)
        val routesFile = writeRoutesForService(bypassRoutes)

        val intent = Intent(activity, OpenVpn3Service::class.java).apply {
            action = OpenVpn3Service.ACTION_CONNECT
            putExtra(OpenVpn3Service.EXTRA_OVPN_CONFIG_PATH, configFile.absolutePath)
            routesFile?.let { putExtra(OpenVpn3Service.EXTRA_EXCLUDED_ROUTES_PATH, it.absolutePath) }
            putExtra(OpenVpn3Service.EXTRA_TRANSPORT, transport.intentValue())
            putExtra(OpenVpn3Service.EXTRA_LINK_PROTOCOL, linkProtocol.intentValue())
            if (transport == VpnTransport.Wss && !wssLink.isNullOrBlank()) {
                putExtra(OpenVpn3Service.EXTRA_WSS_URL, wssLink)
            }
            if (username.isNotEmpty()) {
                putExtra(OpenVpn3Service.EXTRA_AUTH_USERNAME, username)
            }
            if (password.isNotEmpty()) {
                putExtra(OpenVpn3Service.EXTRA_AUTH_PASSWORD, password)
            }
            if (serverDisplayName != null) {
                putExtra(OpenVpn3Service.EXTRA_SERVER_DISPLAY_NAME, serverDisplayName)
            }
        }

        try {
            startServiceCompat(intent)
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "Failed to start OpenVPN service", t)
            runCatching { configFile.delete() }
            routesFile?.let { runCatching { it.delete() } }
            showError(
                activity.getString(
                    R.string.vpn_connect_failed,
                    t.message ?: t.javaClass.simpleName
                )
            )
            return
        }

        onStateChange(
            getState().copy(
                isConnectRequested = true,
                lastMessage = activity.getString(R.string.vpn_connecting_generic)
            )
        )
    }

    private fun startXrayServiceWithConfig(
        configText: String,
        bypassRoutes: List<IpCidrRoute>,
    ) {
        stopPeerEngine(VpnEngine.Xray)
        setActiveEngine(VpnEngine.Xray)
        val serverDisplayName =
            getState().selectedServerName?.takeIf { it.isNotBlank() }
                ?: prefs.getString("selected_server_name", null)?.takeIf { it.isNotBlank() }
        val configFile = writeXrayConfigForService(configText)
        val routesFile = writeRoutesForService(bypassRoutes)

        val intent = Intent(activity, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_CONNECT
            putExtra(XrayVpnService.EXTRA_CONFIG_PATH, configFile.absolutePath)
            routesFile?.let {
                putExtra(XrayVpnService.EXTRA_EXCLUDED_ROUTES_PATH, it.absolutePath)
            }
            if (serverDisplayName != null) {
                putExtra(XrayVpnService.EXTRA_SERVER_DISPLAY_NAME, serverDisplayName)
            }
        }

        try {
            startServiceCompat(intent)
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "Failed to start Xray service", t)
            runCatching { configFile.delete() }
            routesFile?.let { runCatching { it.delete() } }
            showError(
                activity.getString(
                    R.string.vpn_connect_failed,
                    t.message ?: t.javaClass.simpleName
                )
            )
            return
        }

        onStateChange(
            getState().copy(
                isConnectRequested = true,
                lastMessage = activity.getString(R.string.vpn_connecting_generic)
            )
        )
    }

    private fun writeXrayConfigForService(configText: String): File {
        val dir = File(activity.cacheDir, "vpn").apply { mkdirs() }
        return File(dir, "pending-xray-${System.currentTimeMillis()}.json").apply {
            writeText(configText)
        }
    }

    private fun writeConfigForService(configText: String): File {
        val dir = File(activity.cacheDir, "vpn").apply { mkdirs() }
        return File(dir, "pending-${System.currentTimeMillis()}.ovpn").apply {
            writeText(configText)
        }
    }

    private fun writeRoutesForService(routes: List<IpCidrRoute>): File? {
        if (routes.isEmpty()) return null
        val dir = File(activity.cacheDir, "vpn").apply { mkdirs() }
        return File(dir, "excluded-routes-${System.currentTimeMillis()}.txt").apply {
            writeText(routes.joinToString(separator = "\n", postfix = "\n") { it.toCidrString() })
        }
    }

    private fun startServiceCompat(intent: Intent) {
        val isConnect =
            intent.action == OpenVpn3Service.ACTION_CONNECT ||
                intent.action == XrayVpnService.ACTION_CONNECT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isConnect) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
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

    private fun updatePermissionState() {
        val hasPermission = VpnService.prepare(activity) == null
        if (getState().hasVpnPermission != hasPermission) {
            onStateChange(getState().copy(hasVpnPermission = hasPermission))
        }
    }

    fun showError(message: String) {
        prefs.edit {
            remove("selected_server_name")
            remove(KEY_SESSION_SERVER_ID)
            remove(KEY_ACTIVE_ENGINE)
        }

        onStateChange(
            getState().copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                selectedServerName = null,
                selectedServerId = null,
                lastMessage = message
            )
        )
    }
}
