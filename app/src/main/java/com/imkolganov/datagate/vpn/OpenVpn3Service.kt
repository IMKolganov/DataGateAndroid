package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.DataGateApp
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import com.imkolganov.datagate.freetier.FreeTierComplianceController
import com.imkolganov.datagate.freetier.isDisconnectAttributableToGraceExpiry
import com.imkolganov.datagate.logger.CrashLogger
import com.imkolganov.datagate.logger.VpnDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ExecutorService
import net.openvpn.ovpn3.ClientAPI_Config
import net.openvpn.ovpn3.ClientAPI_EvalConfig
import net.openvpn.ovpn3.ClientAPI_ProvideCreds
import net.openvpn.ovpn3.ClientAPI_Status
import java.io.File
import java.net.BindException
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@SuppressLint("VpnServicePolicy")
class OpenVpn3Service : VpnService() {

    companion object {
        const val EXTRA_OVPN_CONFIG = "com.imkolganov.datagate.vpn.EXTRA_OVPN_CONFIG"
        const val EXTRA_OVPN_CONFIG_PATH = "com.imkolganov.datagate.vpn.EXTRA_OVPN_CONFIG_PATH"
        const val EXTRA_EXCLUDED_ROUTES_PATH = "com.imkolganov.datagate.vpn.EXTRA_EXCLUDED_ROUTES_PATH"
        const val EXTRA_WSS_URL = "com.imkolganov.datagate.vpn.EXTRA_WSS_URL"
        const val EXTRA_LINK_PROTOCOL = "com.imkolganov.datagate.vpn.EXTRA_LINK_PROTOCOL"
        const val EXTRA_SERVER_DISPLAY_NAME = "com.imkolganov.datagate.vpn.EXTRA_SERVER_DISPLAY_NAME"

        /** False when this device's ABI has no bundled libovpncli.so (see jniLibs). */
        @Volatile
        var isNativeLibraryLoaded: Boolean = false
            private set

        init {
            isNativeLibraryLoaded = try {
                System.loadLibrary("ovpncli")
                true
            } catch (t: UnsatisfiedLinkError) {
                VpnDebugLogger.e(
                    "OpenVPN3",
                    "libovpncli.so unavailable for ABIs ${Build.SUPPORTED_ABIS.joinToString()}",
                    t
                )
                false
            }
        }

        private const val TAG = "OpenVPN3"
        const val ACTION_CONNECT = "com.imkolganov.datagate.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.imkolganov.datagate.vpn.DISCONNECT"

        const val ACTION_STATUS = "com.imkolganov.datagate.vpn.STATUS"
        const val EXTRA_EVENT_NAME = "event_name"
        const val EXTRA_EVENT_INFO = "event_info"
        const val EXTRA_STATUS_FROM_QUERY = "status_from_query"

        const val ACTION_QUERY_STATUS = "com.imkolganov.datagate.vpn.QUERY_STATUS"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "openvpn3_channel"
        const val PREFS_VPN_STATE = "vpn_state"
        const val PREF_LAST_EVENT_NAME = "vpn_last_event_name"
        const val PREF_LAST_EVENT_INFO = "vpn_last_event_info"
        const val PREF_LAST_EVENT_AT_MS = "vpn_last_event_at_ms"
        private var lastEventName: String = "UNKNOWN"
        private var lastEventInfo: String = "No status yet"

        const val ACTION_PAUSE = "com.imkolganov.datagate.vpn.PAUSE"
        const val ACTION_RESUME = "com.imkolganov.datagate.vpn.RESUME"
    }

    private enum class VpnRuntimeState {
        IDLE,
        WAITING_NETWORK,
        CONNECTING,
        CONNECTED,
        PAUSED,
        DISCONNECTING,
        ERROR
    }

    private sealed interface VpnCommand {
        data class Connect(val intent: Intent) : VpnCommand
        data class CoreEvent(val name: String, val info: String) : VpnCommand
        data class NetworkChanged(val source: String, val transport: String) : VpnCommand
        data class RetryConnect(val reason: String) : VpnCommand
        data class BridgeTransportLost(val reason: String) : VpnCommand
        object Disconnect : VpnCommand
        object QueryStatus : VpnCommand
        object Pause : VpnCommand
        object Resume : VpnCommand
        object SyncStatus : VpnCommand
    }

    private data class PendingConnectRequest(
        val configText: String,
        val wssUrl: String,
        val linkProtocol: VpnLinkProtocol,
        val excludedRoutes: List<IpCidrRoute>
    )
    private data class SystemVpnSnapshot(
        val hasVpnTransport: Boolean,
        val activeTransport: String,
        val notificationsEnabled: Boolean,
        val channelImportance: Int?
    )

    private var bridgeStop: (() -> Unit)? = null
    private var bridgeHttp: okhttp3.OkHttpClient? = null

    @Volatile private var connectInProgress = false
    @Volatile private var hasActiveSession = false
    @Volatile private var isPaused = false
    @Volatile private var isStopping = false
    @Volatile private var lastNetworkChangeAtMs: Long = 0L

    /** Shown in the ongoing notification body (title is always app name). */
    private var sessionServerDisplayName: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val ovpnNativeExecutor: ExecutorService = OvpnNativeThread.createExecutorService()
    private val ovpnNativeDispatcher = ovpnNativeExecutor.asCoroutineDispatcher()
    private val commandQueue = Channel<VpnCommand>(Channel.UNLIMITED)
    private var commandProcessorJob: Job? = null
    private var runtimeState: VpnRuntimeState = VpnRuntimeState.IDLE
    private var desiredConnection = false
    private var pendingConnectRequest: PendingConnectRequest? = null
    private var networkAvailable = true
    private var lastReconnectAttemptAtMs: Long = 0L
    @Volatile private var reconnectPendingAfterJob = false
    private var isNetworkCallbackRegistered = false
    /** Bumped on every [startVpn]; vpnJob.finally only tears down when generations still match. */
    @Volatile private var vpnSessionGeneration = 0

    private var vpnClient: OpenVpn3Client? = null
    private var vpnJob: Job? = null
    private var notificationWatchdogJob: Job? = null
    private var vpnHealthRecheckJob: Job? = null

    private val crashLogger: CrashLogger
        get() = (application as DataGateApp).crashLogger

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val statePrefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_VPN_STATE, Context.MODE_PRIVATE)
    }
    private val reconnectBackoffMs = 4_000L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkStateChanged("AVAILABLE")
        }

        override fun onLost(network: Network) {
            onNetworkStateChanged("LOST")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onNetworkStateChanged("CAP_CHANGED")
        }
    }

    override fun onCreate() {
        super.onCreate()
        VpnDebugLogger.d(TAG, "Service created")
        createNotificationChannel()
        restoreCachedStatus()
        networkAvailable = hasUsableNetwork()
        commandProcessorJob = serviceScope.launch {
            for (command in commandQueue) {
                processCommand(command)
            }
        }
        registerNetworkCallbackSafely()
    }

    override fun onDestroy() {
        VpnDebugLogger.d(TAG, "Service destroyed")
        commandProcessorJob?.cancel()
        commandProcessorJob = null
        unregisterNetworkCallbackSafely()
        stopVpnInternal()
        ovpnNativeExecutor.shutdown()
        super.onDestroy()
    }

    private fun notificationBody(fallback: String): String =
        sessionServerDisplayName?.takeIf { it.isNotBlank() } ?: fallback

    private fun buildNotification(fallbackText: String): Notification {
        val disconnectIntent = Intent(this, OpenVpn3Service::class.java).apply {
            action = ACTION_DISCONNECT
        }

        val pauseOrResumeIntent = Intent(this, OpenVpn3Service::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }

        val disconnectPending = android.app.PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        val pauseOrResumePending = android.app.PendingIntent.getService(
            this,
            2,
            pauseOrResumeIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        val pauseOrResumeLabel = if (isPaused) {
            getString(R.string.action_resume)
        } else {
            getString(R.string.action_pause)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.login_title))
            .setContentText(notificationBody(fallbackText))
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                }
            }
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    pauseOrResumeLabel,
                    pauseOrResumePending
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.action_disconnect),
                    disconnectPending
                ).build()
            )
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
    }

    private fun requiresForegroundNotification(): Boolean =
        hasActiveSession ||
            connectInProgress ||
            isPaused ||
            desiredConnection ||
            runtimeState == VpnRuntimeState.CONNECTING ||
            runtimeState == VpnRuntimeState.WAITING_NETWORK ||
            runtimeState == VpnRuntimeState.CONNECTED ||
            runtimeState == VpnRuntimeState.PAUSED

    private fun notificationFallbackText(): String = when {
        hasActiveSession -> "Connected"
        isPaused -> getString(R.string.vpn_status_paused)
        else -> "Connecting..."
    }

    private fun postPersistentNotification(fallbackText: String = notificationFallbackText(), force: Boolean = false) {
        val notification = buildNotification(fallbackText)
        if (!force && !requiresForegroundNotification()) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            stopNotificationWatchdog()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ensureNotificationWatchdogRunning()
    }

    private fun isNotificationPosted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        return nm.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    private fun ensureNotificationWatchdogRunning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (notificationWatchdogJob?.isActive == true) return
        notificationWatchdogJob = serviceScope.launch {
            while (isActive && requiresForegroundNotification()) {
                delay(3_000)
                if (!isNotificationPosted()) {
                    VpnDebugLogger.w(TAG, "VPN notification dismissed; re-posting foreground notification")
                    postPersistentNotification()
                }
            }
        }
    }

    private fun stopNotificationWatchdog() {
        notificationWatchdogJob?.cancel()
        notificationWatchdogJob = null
    }

    private fun updateNotification(fallbackText: String) {
        postPersistentNotification(fallbackText)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.login_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)
                }
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun processCommand(command: VpnCommand) {
        val label = when (command) {
            is VpnCommand.Connect -> "Connect"
            is VpnCommand.CoreEvent -> "CoreEvent(${command.name})"
            is VpnCommand.NetworkChanged -> "NetworkChanged(${command.source}/${command.transport})"
            is VpnCommand.RetryConnect -> "RetryConnect(${command.reason})"
            is VpnCommand.BridgeTransportLost -> "BridgeTransportLost(${command.reason})"
            VpnCommand.Disconnect -> "Disconnect"
            VpnCommand.QueryStatus -> "QueryStatus"
            VpnCommand.Pause -> "Pause"
            VpnCommand.Resume -> "Resume"
            VpnCommand.SyncStatus -> "SyncStatus"
        }
        VpnDebugLogger.event(
            category = "service.command",
            action = "dispatch",
            details = mapOf(
                "command" to label,
                "state" to runtimeState.name,
                "desired" to desiredConnection,
                "activeSession" to hasActiveSession,
                "paused" to isPaused,
                "stopping" to isStopping,
                "connectInProgress" to connectInProgress,
                "network" to networkAvailable,
            ),
        )
        when (command) {
            is VpnCommand.Connect -> processConnect(command.intent)
            is VpnCommand.CoreEvent -> processCoreEvent(command.name, command.info)
            is VpnCommand.NetworkChanged -> processNetworkChanged(command.source, command.transport)
            is VpnCommand.RetryConnect -> startPendingConnectIfPossible(command.reason, enforceBackoff = true)
            is VpnCommand.BridgeTransportLost -> processBridgeTransportLost(command.reason)
            VpnCommand.Disconnect -> processDisconnect()
            VpnCommand.QueryStatus -> processQueryStatus()
            VpnCommand.Pause -> processPause()
            VpnCommand.Resume -> processResume()
            VpnCommand.SyncStatus -> broadcastStatus(lastEventName, lastEventInfo)
        }
    }

    private fun processConnect(intent: Intent) {
        isStopping = false
        desiredConnection = true

        if (runtimeState == VpnRuntimeState.CONNECTING || runtimeState == VpnRuntimeState.CONNECTED) {
            logCommandDropped("CONNECT", "already_$runtimeState")
            broadcastStatus(lastEventName, lastEventInfo)
            return
        }

        if (!isNativeLibraryLoaded) {
            desiredConnection = false
            pendingConnectRequest = null
            transitionState(VpnRuntimeState.ERROR, "native_library_unavailable")
            broadcastStatus("ERROR", "VPN engine is not available on this device (${Build.SUPPORTED_ABIS.joinToString()})")
            stopSelf()
            return
        }

        val configPath = intent.getStringExtra(EXTRA_OVPN_CONFIG_PATH)
        val excludedRoutesPath = intent.getStringExtra(EXTRA_EXCLUDED_ROUTES_PATH)
        val configText = intent.getStringExtra(EXTRA_OVPN_CONFIG)
            ?: configPath?.let { path ->
                runCatching { File(path).readText() }
                    .onFailure { VpnDebugLogger.e(TAG, "Failed to read OVPN config file: $path", it) }
                    .getOrNull()
            }
        val excludedRoutes = excludedRoutesPath
            ?.let { path ->
                runCatching {
                    IpListRouteConfig.parseCidrRoutesResult(File(path).readText()).routes
                }
                    .onFailure { VpnDebugLogger.e(TAG, "Failed to read excluded routes file: $path", it) }
                    .getOrNull()
            }
            .orEmpty()
        val wssUrl = intent.getStringExtra(EXTRA_WSS_URL)

        if (configText.isNullOrBlank() || wssUrl.isNullOrBlank()) {
            desiredConnection = false
            pendingConnectRequest = null
            transitionState(VpnRuntimeState.ERROR, "missing_connect_args")
            broadcastStatus("ERROR", "Missing config or WSS URL")
            stopSelf()
            return
        }

        val linkProtocol = VpnLinkProtocol.fromIntentExtra(intent.getStringExtra(EXTRA_LINK_PROTOCOL))
        pendingConnectRequest = PendingConnectRequest(
            configText = configText,
            wssUrl = wssUrl,
            linkProtocol = linkProtocol,
            excludedRoutes = excludedRoutes
        )
        VpnDebugLogger.event(
            category = "service.connect",
            action = "accepted",
            details = mapOf(
                "proto" to linkProtocol.name,
                "wssHost" to runCatching { java.net.URI(wssUrl).host }.getOrNull(),
                "configBytes" to configText.length,
                "excludeRoutes" to excludedRoutes.size,
                "server" to sessionServerDisplayName,
                "network" to networkAvailable,
                "transport" to currentTransportLabel(),
            ),
        )
        if (!networkAvailable) {
            connectInProgress = false
            hasActiveSession = false
            transitionState(VpnRuntimeState.WAITING_NETWORK, "connect_wait_network")
            broadcastStatus("WAITING_NETWORK", "Waiting for network...")
        } else {
            startPendingConnectIfPossible("connect_command", enforceBackoff = false)
        }

        configPath?.let { path ->
            runCatching { File(path).delete() }
                .onFailure { VpnDebugLogger.w(TAG, "Failed to delete OVPN config file: $path", it) }
        }
        excludedRoutesPath?.let { path ->
            runCatching { File(path).delete() }
                .onFailure { VpnDebugLogger.w(TAG, "Failed to delete excluded routes file: $path", it) }
        }
    }

    private fun processDisconnect() {
        desiredConnection = false
        pendingConnectRequest = null
        if (runtimeState == VpnRuntimeState.IDLE && !connectInProgress && !hasActiveSession) {
            logCommandDropped("DISCONNECT", "already_idle")
        } else {
            transitionState(VpnRuntimeState.DISCONNECTING, "disconnect_command")
        }

        isStopping = true
        reconnectPendingAfterJob = false
        stopVpnInternal()
        connectInProgress = false
        hasActiveSession = false
        isPaused = false
        sessionServerDisplayName = null
        transitionState(VpnRuntimeState.IDLE, "disconnect_completed")

        stopNotificationWatchdog()
        vpnHealthRecheckJob?.cancel()
        vpnHealthRecheckJob = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
        broadcastStatus("DISCONNECTED", "Disconnected by user")
        stopSelf()
    }

    private fun processQueryStatus() {
        val name = when (runtimeState) {
            VpnRuntimeState.CONNECTED -> "CONNECTED"
            VpnRuntimeState.PAUSED -> "PAUSED"
            VpnRuntimeState.WAITING_NETWORK -> "WAITING_NETWORK"
            VpnRuntimeState.CONNECTING -> "CONNECTING"
            VpnRuntimeState.DISCONNECTING -> "DISCONNECTING"
            VpnRuntimeState.ERROR -> "ERROR"
            VpnRuntimeState.IDLE -> "DISCONNECTED"
        }
        val info = when (runtimeState) {
            VpnRuntimeState.CONNECTED -> "Session active"
            VpnRuntimeState.PAUSED -> getString(R.string.vpn_msg_paused)
            VpnRuntimeState.WAITING_NETWORK -> "Waiting for network..."
            VpnRuntimeState.CONNECTING -> "Connecting..."
            VpnRuntimeState.DISCONNECTING -> "Disconnecting..."
            VpnRuntimeState.ERROR -> "Last operation failed"
            VpnRuntimeState.IDLE -> "No active session"
        }

        runSystemVpnHealthCheck("query_status")
        broadcastStatus(name, info, fromQuery = true)
        if (runtimeState == VpnRuntimeState.IDLE && !connectInProgress && !hasActiveSession && !desiredConnection) {
            stopSelf()
        }
    }

    private fun processPause() {
        val decision = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(
                hasActiveSession = hasActiveSession,
                vpnClientPresent = vpnClient != null,
                isPaused = isPaused,
            )
        )
        if (decision is VpnCommandContract.CommandDecision.Reject) {
            logCommandDropped("PAUSE", decision.reason)
            broadcastStatus("PAUSE_REJECTED", decision.reason)
            return
        }
        isPaused = true
        desiredConnection = true
        hasActiveSession = false
        connectInProgress = false
        transitionState(VpnRuntimeState.PAUSED, "pause_command")
        val client = vpnClient
        if (client != null) {
            val nativeJobActive = vpnJob?.isActive == true
            OpenVpnNativePauseResumeScheduling.schedulePauseOrResume(
                scope = serviceScope,
                nativeVpnJobActive = nativeJobActive,
                action = { client.pause("user") },
                onFailure = { error ->
                    VpnDebugLogger.w(TAG, "client.pause() scheduling failed", error)
                    rollbackPauseCommand(error.message ?: "pause_failed")
                },
            )
        } else {
            broadcastPauseConfirmed(getString(R.string.vpn_msg_paused))
        }
    }

    private fun rollbackPauseCommand(reason: String) {
        isPaused = false
        hasActiveSession = true
        connectInProgress = false
        transitionState(VpnRuntimeState.CONNECTED, "pause_rollback")
        broadcastStatus("PAUSE_REJECTED", reason)
    }

    private fun broadcastPauseConfirmed(info: String) {
        updateNotification(getString(R.string.vpn_status_paused))
        broadcastStatus(OpenVpnPauseBroadcastPolicy.uiBroadcastEventForCorePause(), info)
    }

    private fun processResume() {
        val decision = VpnCommandContract.evaluateResume(
            VpnCommandContract.VpnServiceSnapshot(
                hasActiveSession = hasActiveSession,
                vpnClientPresent = vpnClient != null,
                isPaused = isPaused,
            )
        )
        if (decision is VpnCommandContract.CommandDecision.Reject) {
            logCommandDropped("RESUME", decision.reason)
            broadcastStatus("RESUME_REJECTED", decision.reason)
            return
        }
        isPaused = false
        val client = vpnClient
        if (client != null) {
            val nativeJobActive = vpnJob?.isActive == true
            transitionState(VpnRuntimeState.CONNECTING, "resume_command")
            connectInProgress = true
            OpenVpnNativePauseResumeScheduling.schedulePauseOrResume(
                scope = serviceScope,
                nativeVpnJobActive = nativeJobActive,
                action = { client.resume() },
                onFailure = { error ->
                    VpnDebugLogger.w(TAG, "client.resume() scheduling failed", error)
                    rollbackResumeCommand(error.message ?: "resume_failed")
                },
            )
        } else if (pendingConnectRequest != null) {
            startPendingConnectIfPossible("resume_command", enforceBackoff = false)
        } else {
            transitionState(VpnRuntimeState.IDLE, "resume_without_session")
            broadcastStatus("DISCONNECTED", getString(R.string.vpn_msg_disconnected))
        }
    }

    private fun rollbackResumeCommand(reason: String) {
        isPaused = true
        connectInProgress = false
        transitionState(VpnRuntimeState.PAUSED, "resume_rollback")
        broadcastStatus("RESUME_REJECTED", reason)
    }

    private fun processCoreEvent(name: String, info: String) {
        when {
            OpenVpnPauseBroadcastPolicy.shouldBroadcastPausedOnCoreEvent(name) -> {
                isPaused = true
                hasActiveSession = false
                connectInProgress = false
                transitionState(VpnRuntimeState.PAUSED, "core_pause")
                broadcastPauseConfirmed(
                    info.takeIf { it.isNotBlank() } ?: getString(R.string.vpn_msg_paused)
                )
            }
            OpenVpnPauseBroadcastPolicy.shouldBroadcastResumedOnCoreEvent(name) -> {
                isPaused = false
                connectInProgress = true
                transitionState(VpnRuntimeState.CONNECTING, "core_resume")
                updateNotification(getString(R.string.vpn_msg_resuming))
                broadcastStatus(
                    OpenVpnPauseBroadcastPolicy.uiBroadcastEventForCoreResume(),
                    info.takeIf { it.isNotBlank() } ?: getString(R.string.vpn_msg_resuming)
                )
            }
            name.equals("CONNECTED", ignoreCase = true) -> {
                ensureForegroundForVpn()
                hasActiveSession = true
                connectInProgress = false
                isPaused = false
                transitionState(VpnRuntimeState.CONNECTED, "core_connected")
                updateNotification("Connected")
                broadcastStatus(name, info)
                runSystemVpnHealthCheck("core_connected")
                scheduleSystemVpnHealthRecheck()
            }
            name.equals("DISCONNECTED", ignoreCase = true) -> {
                hasActiveSession = false
                connectInProgress = false

                if (isPaused) {
                    transitionState(VpnRuntimeState.PAUSED, "core_disconnected_while_paused")
                    broadcastPauseConfirmed(
                        info.takeIf { it.isNotBlank() } ?: getString(R.string.vpn_msg_paused)
                    )
                } else {
                isPaused = false

                val sinceNetworkChangeMs = SystemClock.elapsedRealtime() - lastNetworkChangeAtMs
                if (lastNetworkChangeAtMs > 0L && sinceNetworkChangeMs in 0..20_000L) {
                    crashLogger.logNonFatal(
                        tag = "OpenVpn3Service.disconnect_after_network_change",
                        throwable = IllegalStateException("VPN disconnected shortly after network change"),
                        extras = mapOf(
                            "since_network_change_ms" to sinceNetworkChangeMs.toString(),
                            "event_info" to info,
                            "server" to (sessionServerDisplayName ?: "")
                        )
                    )
                }

                // A grace-period forced disconnect looks like any other core DISCONNECTED event —
                // without this check we'd auto-reconnect and just churn through another short grace
                // window instead of prompting the user to actually link/subscribe.
                val graceExpired = isDisconnectAttributableToGraceExpiry(
                    graceExpiresAtMs = FreeTierComplianceController.graceExpiresAtUtcMs.value,
                    nowMs = System.currentTimeMillis(),
                )

                if (!isStopping && desiredConnection && !graceExpired) {
                    if (OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(
                            reconnectPendingAfterJob
                        )
                    ) {
                        VpnDebugLogger.event(
                            category = "service.reconnect",
                            action = "deferred_to_bridge_loss_finally",
                            details = mapOf("info" to info),
                        )
                    } else if (networkAvailable) {
                        transitionState(VpnRuntimeState.CONNECTING, "core_disconnected_reconnect")
                        broadcastStatus("RECONNECTING", info.ifBlank { "Connection lost, reconnecting..." })
                        startPendingConnectIfPossible("core_disconnected_reconnect", enforceBackoff = true)
                    } else {
                        transitionState(VpnRuntimeState.WAITING_NETWORK, "core_disconnected_wait_network")
                        broadcastStatus("WAITING_NETWORK", "Waiting for network...")
                    }
                } else {
                    if (graceExpired) {
                        VpnDebugLogger.w(TAG, "Not auto-reconnecting: disconnect attributed to grace-period expiry")
                        desiredConnection = false
                        pendingConnectRequest = null
                        FreeTierComplianceController.setGraceExpiresAtUtcMs(null)
                    }
                    transitionState(VpnRuntimeState.IDLE, "core_disconnected")
                    broadcastStatus(name, info)
                }
                }
            }
            else -> {
                if (!isPaused) {
                    broadcastStatus(name, info)
                }
            }
        }
    }

    private fun processBridgeTransportLost(reason: String) {
        if (!OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = isStopping,
                desiredConnection = desiredConnection,
                isPaused = isPaused,
                hasActiveSession = hasActiveSession
            )
        ) {
            VpnDebugLogger.d(TAG, "bridge_transport_lost_ignored: $reason state=$runtimeState")
            return
        }

        VpnDebugLogger.w(TAG, "bridge_transport_lost: $reason state=$runtimeState")
        VpnDebugLogger.event(
            category = "bridge",
            action = "transport_lost",
            details = mapOf(
                "reason" to reason,
                "state" to runtimeState.name,
                "network" to networkAvailable,
                "transport" to currentTransportLabel(),
                "willReconnect" to networkAvailable,
            ),
        )
        hasActiveSession = false
        reconnectPendingAfterJob = true

        if (networkAvailable) {
            transitionState(VpnRuntimeState.CONNECTING, "bridge_transport_lost")
            updateNotification(getString(R.string.vpn_msg_reconnecting))
            broadcastStatus("RECONNECTING", getString(R.string.vpn_msg_reconnecting))
        } else {
            transitionState(VpnRuntimeState.WAITING_NETWORK, "bridge_transport_lost")
            updateNotification("Waiting for network...")
            broadcastStatus("WAITING_NETWORK", "Waiting for network...")
        }

        // Must not use ovpnNativeDispatcher: stop would queue behind blocking connect() and the
        // TUN would stay up while tunneled traffic blackholes (see OpenVpnNativeBridgeLossStopScheduling).
        // Capture the client now — a later startVpn must not make this stop() hit the replacement.
        val clientToStop = vpnClient
        OpenVpnNativeBridgeLossStopScheduling.scheduleStop(
            scope = serviceScope,
            nativeVpnJobActive = vpnJob?.isActive == true,
            stopAction = { clientToStop?.stop() },
            onFailure = { VpnDebugLogger.w(TAG, "client.stop() after bridge transport loss", it) },
        )
    }

    private fun processNetworkChanged(source: String, transport: String) {
        lastNetworkChangeAtMs = SystemClock.elapsedRealtime()
        networkAvailable = hasUsableNetwork()
        val info = "$source:$transport"
        VpnDebugLogger.event(
            category = "network",
            action = "changed",
            details = mapOf(
                "source" to source,
                "transport" to transport,
                "usable" to networkAvailable,
                "activeSession" to hasActiveSession,
                "desired" to desiredConnection,
                "state" to runtimeState.name,
            ),
        )
        runSystemVpnHealthCheck("network_changed_$source")
        if (!hasActiveSession && (connectInProgress || desiredConnection)) {
            broadcastStatus("NETWORK_CHANGED", info)
        } else {
            lastEventName = "NETWORK_CHANGED"
            lastEventInfo = info
        }

        if (desiredConnection && !hasActiveSession && !isPaused) {
            if (networkAvailable) {
                startPendingConnectIfPossible("network_available", enforceBackoff = true)
            } else {
                transitionState(VpnRuntimeState.WAITING_NETWORK, "network_lost")
                broadcastStatus("WAITING_NETWORK", "Waiting for network...")
            }
        }
    }

    private fun startPendingConnectIfPossible(reason: String, enforceBackoff: Boolean) {
        if (!desiredConnection || isPaused) return
        if (connectInProgress || hasActiveSession) return
        if (OpenVpnSessionTeardownPolicy.shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
                reconnectPendingAfterJob = reconnectPendingAfterJob,
                reason = reason,
            )
        ) {
            VpnDebugLogger.event(
                category = "service.reconnect",
                action = "deferred_pending_connect_bridge_loss",
                details = mapOf("reason" to reason),
            )
            return
        }

        val request = pendingConnectRequest ?: return
        if (!networkAvailable) {
            transitionState(VpnRuntimeState.WAITING_NETWORK, "wait_network_$reason")
            broadcastStatus("WAITING_NETWORK", "Waiting for network...")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (
            !OpenVpnRuntimePolicy.canAttemptReconnect(
                nowMs = now,
                lastAttemptAtMs = lastReconnectAttemptAtMs,
                backoffMs = reconnectBackoffMs,
                enforceBackoff = enforceBackoff
            )
        ) {
            return
        }
        lastReconnectAttemptAtMs = now

        ensureForegroundForVpn()
        connectInProgress = true
        hasActiveSession = false
        transitionState(VpnRuntimeState.CONNECTING, reason)
        startVpn(
            configText = request.configText,
            wssUrl = request.wssUrl,
            linkProtocol = request.linkProtocol,
            excludedRoutes = request.excludedRoutes
        )
    }

    private fun transitionState(next: VpnRuntimeState, reason: String) {
        val previous = runtimeState
        runtimeState = next
        VpnDebugLogger.event(
            category = "service.state",
            action = "transition",
            details = mapOf(
                "from" to previous.name,
                "to" to next.name,
                "reason" to reason,
                "desired" to desiredConnection,
                "activeSession" to hasActiveSession,
                "paused" to isPaused,
            ),
        )
    }

    private fun logCommandDropped(command: String, reason: String) {
        VpnDebugLogger.event(
            category = "service.command",
            action = "dropped",
            details = mapOf(
                "command" to command,
                "reason" to reason,
                "state" to runtimeState.name,
            ),
        )
        VpnDebugLogger.w(TAG, "vpn_command_dropped: command=$command reason=$reason state=$runtimeState")
        crashLogger.logNonFatal(
            tag = "OpenVpn3Service.command_dropped",
            throwable = IllegalStateException("VPN command dropped"),
            extras = mapOf(
                "command" to command,
                "reason" to reason,
                "state" to runtimeState.name
            )
        )
    }

    private fun restoreCachedStatus() {
        runtimeState = VpnRuntimeState.IDLE
        val restoreResult = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = statePrefs.getString(PREF_LAST_EVENT_NAME, null),
            cachedInfo = statePrefs.getString(PREF_LAST_EVENT_INFO, null)
        )
        if (!restoreResult.eventName.isNullOrBlank()) {
            lastEventName = restoreResult.eventName
            lastEventInfo = restoreResult.eventInfo ?: ""
            if (restoreResult.eventName.equals("ERROR", ignoreCase = true)) {
                runtimeState = VpnRuntimeState.ERROR
            }
            if (restoreResult.shouldPersist) {
                statePrefs.edit()
                    .putString(PREF_LAST_EVENT_NAME, lastEventName)
                    .putString(PREF_LAST_EVENT_INFO, lastEventInfo)
                    .putLong(PREF_LAST_EVENT_AT_MS, System.currentTimeMillis())
                    .apply()
            }
        }
    }

    private fun hasUsableNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun ensureForegroundForVpn() {
        postPersistentNotification()
    }

    private fun runSystemVpnHealthCheck(trigger: String) {
        val snapshot = collectSystemVpnSnapshot()
        VpnDebugLogger.i(
            TAG,
            "system_vpn_check trigger=$trigger state=$runtimeState hasActiveSession=$hasActiveSession " +
                "connectInProgress=$connectInProgress desiredConnection=$desiredConnection " +
                "hasVpnTransport=${snapshot.hasVpnTransport} activeTransport=${snapshot.activeTransport} " +
                "notificationsEnabled=${snapshot.notificationsEnabled} channelImportance=${snapshot.channelImportance}"
        )

        if (hasActiveSession && !snapshot.hasVpnTransport) {
            VpnDebugLogger.w(
                TAG,
                "system_vpn_transport_mismatch trigger=$trigger state=$runtimeState " +
                    "activeTransport=${snapshot.activeTransport}"
            )
        }
    }

    private fun scheduleSystemVpnHealthRecheck() {
        vpnHealthRecheckJob?.cancel()
        vpnHealthRecheckJob = serviceScope.launch {
            delay(2_000)
            if (!hasActiveSession) return@launch
            runSystemVpnHealthCheck("post_connected_2s")
            delay(3_000)
            if (!hasActiveSession) return@launch
            runSystemVpnHealthCheck("post_connected_5s")
        }
    }

    private fun collectSystemVpnSnapshot(): SystemVpnSnapshot {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasVpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val activeTransport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }

        val nm = getSystemService(NotificationManager::class.java)
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.getNotificationChannel(CHANNEL_ID)?.importance
        } else {
            null
        }

        return SystemVpnSnapshot(
            hasVpnTransport = hasVpnTransport,
            activeTransport = activeTransport,
            notificationsEnabled = nm.areNotificationsEnabled(),
            channelImportance = channelImportance
        )
    }

    private fun startVpn(
        configText: String,
        wssUrl: String,
        linkProtocol: VpnLinkProtocol,
        excludedRoutes: List<IpCidrRoute>
    ) {
        // Bump before tearing down the previous job so its finally cannot clear this session.
        val sessionGeneration = ++vpnSessionGeneration
        stopVpnInternal()

        vpnJob = serviceScope.launch(ovpnNativeDispatcher) {
            try {
                val http = buildProtectedOkHttp(this@OpenVpn3Service)
                bridgeHttp = http

                val bridgePort = startBridgeWithRetry(http, wssUrl, linkProtocol)

                VpnDebugLogger.d(TAG, "startVpn: building config gen=$sessionGeneration")

                val patchedConfig = forceRemoteToLocalBridge(configText, bridgePort, linkProtocol)

                val cfg = ClientAPI_Config().apply {
                    content = patchedConfig
                    enableRouteEmulation = true
                    // Exclude emulator/LAN prefixes (e.g. 10.0.2.0/24) from the full tunnel.
                    allowLocalLanAccess = true
                    // IPv4-only tunnel: keep native IPv6 on the underlying network instead of
                    // installing broken IPv6 default routes (common "no internet" on TV emulator).
                    allowUnusedAddrFamilies = "yes"
                }

                val client = OpenVpn3Client(
                    service = this@OpenVpn3Service,
                    excludedRoutes = excludedRoutes,
                    onTunChanged = { fd ->
                        VpnDebugLogger.d(TAG, "TUN changed (fd=${fd?.fd ?: -1})")
                    },
                    onCoreEvent = { name, info ->
                        commandQueue.trySend(VpnCommand.CoreEvent(name, info))
                    }
                )
                vpnClient = client

                VpnDebugLogger.d(TAG, "startVpn: eval_config")
                val eval: ClientAPI_EvalConfig = client.eval_config(cfg)
                if (eval.error) {
                    VpnDebugLogger.e(TAG, "eval_config error: ${eval.message}")
                    transitionState(VpnRuntimeState.ERROR, "eval_config_failed")
                    broadcastStatus("ERROR", eval.message ?: "OpenVPN profile validation failed")
                    stopSelf()
                    return@launch
                }

                VpnDebugLogger.d(TAG, "startVpn: provide_creds")
                val creds = ClientAPI_ProvideCreds().apply {
                    username = ""
                    password = ""
                }
                val credStatus: ClientAPI_Status = client.provide_creds(creds)
                if (credStatus.error) {
                    VpnDebugLogger.e(TAG, "provide_creds error: ${credStatus.message}")
                    transitionState(VpnRuntimeState.ERROR, "provide_creds_failed")
                    broadcastStatus("ERROR", credStatus.message ?: "OpenVPN credentials failed")
                    stopSelf()
                    return@launch
                }

                VpnDebugLogger.d(TAG, "startVpn: connect()")
                val status: ClientAPI_Status = client.connect()
                VpnDebugLogger.d(TAG, "connect() finished: error=${status.error} message=${status.message}")
            } catch (t: Throwable) {
                VpnDebugLogger.e(TAG, "startVpn error", t)
                transitionState(VpnRuntimeState.ERROR, "start_vpn_exception")
                broadcastStatus("ERROR", t.message ?: t.javaClass.simpleName)
                crashLogger.logNonFatal("OpenVpn3Service.startVpn", t)
                if (desiredConnection && !isStopping && !isPaused) {
                    commandQueue.trySend(VpnCommand.RetryConnect("start_vpn_exception"))
                }
            } finally {
                if (!OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(
                        sessionGeneration = sessionGeneration,
                        currentGeneration = vpnSessionGeneration
                    )
                ) {
                    if (OpenVpnSessionTeardownPolicy.shouldClearReconnectPendingOnStaleFinally()) {
                        reconnectPendingAfterJob = false
                    }
                    VpnDebugLogger.event(
                        category = "service.session",
                        action = "stale_finally_skipped",
                        details = mapOf(
                            "sessionGeneration" to sessionGeneration,
                            "currentGeneration" to vpnSessionGeneration,
                            "clearedReconnectPending" to true,
                        ),
                    )
                    return@launch
                }
                connectInProgress = false
                stopVpnInternal()
                val shouldReconnect = reconnectPendingAfterJob
                reconnectPendingAfterJob = false
                when {
                    OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                        reconnectPendingAfterJob = shouldReconnect,
                        desiredConnection = desiredConnection,
                        isStopping = isStopping,
                        isPaused = isPaused
                    ) -> {
                        commandQueue.trySend(
                            VpnCommand.RetryConnect(
                                OpenVpnSessionTeardownPolicy.BRIDGE_TRANSPORT_LOST_RETRY_REASON
                            )
                        )
                    }
                    !desiredConnection || isStopping -> stopSelf()
                }
            }
        }
    }

    private fun startBridgeWithRetry(
        http: okhttp3.OkHttpClient,
        wssUrl: String,
        linkProtocol: VpnLinkProtocol
    ): Int {
        val onTransportLost: (String) -> Unit = { reason ->
            commandQueue.trySend(VpnCommand.BridgeTransportLost(reason))
        }
        val candidates = LocalBridgePortPool.candidatePorts(applicationContext)
        var lastError: Throwable? = null
        var lastBindReason: String? = null
        for ((index, port) in candidates.withIndex()) {
            try {
                val boundPort = when (linkProtocol) {
                    VpnLinkProtocol.UDP -> {
                        val bridge = UdpToWssBridge(
                            service = this@OpenVpn3Service,
                            port = port,
                            wssUrl = wssUrl,
                            http = http,
                            onTransportLost = onTransportLost
                        )
                        val dynamicPort = bridge.start()
                        bridgeStop = { bridge.stop() }
                        dynamicPort
                    }
                    VpnLinkProtocol.TCP -> {
                        val bridge = TcpToWssBridge(
                            service = this@OpenVpn3Service,
                            port = port,
                            wssUrl = wssUrl,
                            http = http,
                            onTransportLost = onTransportLost
                        )
                        val dynamicPort = bridge.start()
                        bridgeStop = { bridge.stop() }
                        dynamicPort
                    }
                }
                VpnDebugLogger.d(
                    TAG,
                    "Bridge bound on 127.0.0.1:$boundPort " +
                        "(requested=$port attempt=${index + 1}/${candidates.size} protocol=$linkProtocol)"
                )
                return boundPort
            } catch (t: Throwable) {
                lastError = t
                bridgeStop?.invoke()
                bridgeStop = null
                if (LocalBridgePortPool.isBindConflict(t)) {
                    val reason = parseBindReason(t)
                    lastBindReason = reason
                    VpnDebugLogger.w(
                        TAG,
                        "Bridge bind failed on port=$port (attempt=${index + 1}/${candidates.size}): $reason",
                        t
                    )
                    continue
                }
                throw t
            }
        }
        lastError?.let { error ->
            crashLogger.logNonFatal(
                tag = "OpenVpn3Service.bridge_bind_failed",
                throwable = error,
                extras = mapOf(
                    "attempts" to candidates.size.toString(),
                    "bind_reason" to (lastBindReason ?: parseBindReason(error)),
                    "link_protocol" to linkProtocol.name
                )
            )
        }
        throw lastError ?: IllegalStateException("No free local bridge port in pool")
    }

    private fun parseBindReason(t: Throwable): String {
        val msg = t.message.orEmpty().uppercase()
        return when {
            "EADDRINUSE" in msg -> "EADDRINUSE"
            "EPERM" in msg -> "EPERM"
            else -> "UNKNOWN"
        }
    }

    private fun stopVpnInternal() {
        VpnDebugLogger.d(TAG, "stopVpnInternal")

        val job = vpnJob
        vpnJob = null
        job?.cancel()

        val client = vpnClient
        vpnClient = null
        if (client != null) {
            val nativeJobActive = job?.isActive == true
            OpenVpnNativeStopScheduling.runOrQueueStop(
                nativeExecutor = ovpnNativeExecutor,
                nativeVpnJobActive = nativeJobActive,
                stopAction = Runnable {
                    try {
                        VpnDebugLogger.d(TAG, "Calling client.stop()")
                        client.stop()
                    } catch (t: Throwable) {
                        VpnDebugLogger.w(TAG, "client.stop() failed", t)
                    }
                },
            )
            if (nativeJobActive && !OvpnNativeThread.runsOnNativeThread()) {
                VpnDebugLogger.d(TAG, "Queued client.stop() without blocking (native job active)")
            }
        }

        try {
            bridgeStop?.invoke()
        } catch (_: Throwable) {
        }
        bridgeStop = null

        try { bridgeHttp?.dispatcher?.executorService?.shutdown() } catch (_: Throwable) {}
        try { bridgeHttp?.connectionPool?.evictAll() } catch (_: Throwable) {}
        bridgeHttp = null
        VpnTunnelSessionStore.clear(applicationContext)
    }

    private fun broadcastStatus(name: String, info: String, fromQuery: Boolean = false) {
        if (!fromQuery) {
            lastEventName = name
            lastEventInfo = info
            statePrefs.edit()
                .putString(PREF_LAST_EVENT_NAME, name)
                .putString(PREF_LAST_EVENT_INFO, info)
                .putLong(PREF_LAST_EVENT_AT_MS, System.currentTimeMillis())
                .apply()
        }

        VpnDebugLogger.event(
            category = "service.broadcast",
            action = name,
            details = mapOf(
                "info" to info,
                "fromQuery" to fromQuery,
                "state" to runtimeState.name,
                "desired" to desiredConnection,
                "activeSession" to hasActiveSession,
                "paused" to isPaused,
            ),
        )

        val intent = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .apply {
                putExtra(EXTRA_EVENT_NAME, name)
                putExtra(EXTRA_EVENT_INFO, info)
                putExtra(EXTRA_STATUS_FROM_QUERY, fromQuery)
            }

        sendBroadcast(intent)
    }

    private fun registerNetworkCallbackSafely() {
        if (isNetworkCallbackRegistered) return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = true
        }.onFailure {
            VpnDebugLogger.w(TAG, "registerDefaultNetworkCallback failed", it)
            crashLogger.logNonFatal(
                tag = "OpenVpn3Service.network_callback_register_failed",
                throwable = it
            )
        }
    }

    private fun unregisterNetworkCallbackSafely() {
        if (!isNetworkCallbackRegistered) return
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }.onFailure {
            VpnDebugLogger.w(TAG, "unregisterNetworkCallback failed", it)
        }
        isNetworkCallbackRegistered = false
    }

    private fun onNetworkStateChanged(source: String) {
        commandQueue.trySend(VpnCommand.NetworkChanged(source, currentTransportLabel()))
    }

    private fun currentTransportLabel(): String {
        val network = connectivityManager.activeNetwork ?: return "none"
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        VpnDebugLogger.event(
            category = "service.lifecycle",
            action = "onStartCommand",
            details = mapOf(
                "intentAction" to (action ?: "null"),
                "flags" to flags,
                "startId" to startId,
                "state" to runtimeState.name,
            ),
        )
        if (action == ACTION_CONNECT) {
            sessionServerDisplayName = intent.getStringExtra(EXTRA_SERVER_DISPLAY_NAME)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (action == ACTION_CONNECT) {
                // VpnController starts CONNECT via startForegroundService(); the platform
                // requires startForeground() before this call returns, even if processConnect
                // is about to reject the attempt (missing config, missing native lib, etc.)
                // and stop the service right away. Skipping this throws
                // ForegroundServiceDidNotStartInTimeException and kills the whole app.
                postPersistentNotification(force = true)
            } else if (requiresForegroundNotification()) {
                postPersistentNotification()
            }
        }

        when (action) {
            ACTION_CONNECT -> {
                commandQueue.trySend(VpnCommand.Connect(intent))
            }

            ACTION_DISCONNECT -> {
                commandQueue.trySend(VpnCommand.Disconnect)
            }

            ACTION_QUERY_STATUS -> {
                commandQueue.trySend(VpnCommand.QueryStatus)
            }

            ACTION_PAUSE -> {
                commandQueue.trySend(VpnCommand.Pause)
            }

            ACTION_RESUME -> {
                commandQueue.trySend(VpnCommand.Resume)
            }

            null -> {
                VpnDebugLogger.w(TAG, "Service restarted with null intent")
                commandQueue.trySend(VpnCommand.SyncStatus)
            }

            else -> {
                VpnDebugLogger.w(TAG, "Unknown action: $action")
            }
        }

        return START_STICKY
    }


    private fun buildProtectedOkHttp(service: VpnService): okhttp3.OkHttpClient {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        val baseSslFactory = sslContext.socketFactory
        val protectingSslFactory = ProtectingSSLSocketFactory(baseSslFactory) { s ->
            service.protect(s)
        }

        return okhttp3.OkHttpClient.Builder()
            .socketFactory(
                ProtectingSocketFactory(
                    delegate = javax.net.SocketFactory.getDefault(),
                    protect = { socket -> service.protect(socket) },
                    log = { msg ->
                        com.imkolganov.datagate.logger.VpnDebugLogger.d("WssEgressSocket", msg)
                    },
                )
            )
            .sslSocketFactory(protectingSslFactory, trustManager)
            .eventListenerFactory(WssEgressOkHttpEventListener.FACTORY)
            .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
