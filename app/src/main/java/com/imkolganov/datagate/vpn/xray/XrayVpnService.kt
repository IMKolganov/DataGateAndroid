package com.imkolganov.datagate.vpn.xray

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
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.VpnDebugLogger
import com.imkolganov.datagate.ui.tv.isTelevision
import com.imkolganov.datagate.vpn.diag.VpnDiagnostics
import com.imkolganov.datagate.vpn.ExcludeRouteSession
import com.imkolganov.datagate.vpn.IpListRouteConfig
import com.imkolganov.datagate.vpn.OpenVpn3Service
import com.imkolganov.datagate.vpn.SplitTunnelSession
import com.imkolganov.datagate.vpn.VpnBypassApps
import com.imkolganov.datagate.vpn.VpnExcludeRoutes
import com.imkolganov.datagate.vpn.VpnTunnelSessionStore
import com.imkolganov.datagate.vpn.traffic.VpnTrafficMonitor
import com.imkolganov.datagate.vpn.traffic.VpnTunIfaceCounters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android [VpnService] that runs XTLS/libXray with a TUN fd injected into the client config.
 * Status broadcasts use the same action/extras as [OpenVpn3Service] so Home/Access UI stays shared.
 *
 * Notification mirrors OpenVPN (status text + Pause/Disconnect). Pause for Xray v1 disconnects.
 */
@SuppressLint("VpnServicePolicy")
class XrayVpnService : VpnService() {

    companion object {
        private const val TAG = "XrayVpn"
        const val ACTION_CONNECT = "com.imkolganov.datagate.vpn.xray.CONNECT"
        const val ACTION_DISCONNECT = "com.imkolganov.datagate.vpn.xray.DISCONNECT"
        /** Same UX affordance as OpenVPN pause; tears down the Xray session. */
        const val ACTION_PAUSE = "com.imkolganov.datagate.vpn.xray.PAUSE"
        const val ACTION_QUERY_STATUS = "com.imkolganov.datagate.vpn.xray.QUERY_STATUS"
        const val EXTRA_CONFIG_PATH = "com.imkolganov.datagate.vpn.xray.EXTRA_CONFIG_PATH"
        const val EXTRA_CONFIG_TEXT = "com.imkolganov.datagate.vpn.xray.EXTRA_CONFIG_TEXT"
        const val EXTRA_SERVER_DISPLAY_NAME = "com.imkolganov.datagate.vpn.xray.EXTRA_SERVER_DISPLAY_NAME"
        /** Same CIDR list format as OpenVPN [OpenVpn3Service.EXTRA_EXCLUDED_ROUTES_PATH]. */
        const val EXTRA_EXCLUDED_ROUTES_PATH = "com.imkolganov.datagate.vpn.xray.EXTRA_EXCLUDED_ROUTES_PATH"
        /** Classic IPv4 DNS servers for [Builder.addDnsServer] (comma-free list via ArrayList). */
        const val EXTRA_DNS_SERVERS = "com.imkolganov.datagate.vpn.xray.EXTRA_DNS_SERVERS"
        /** When true, Access UI shows Private DNS Off hint (issued profile flag). */
        const val EXTRA_DNS_IDENTITY_ENABLED = "com.imkolganov.datagate.vpn.xray.EXTRA_DNS_IDENTITY_ENABLED"

        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "xray_vpn_channel"

        @Volatile
        private var lastEventName: String = "DISCONNECTED"

        @Volatile
        private var lastEventInfo: String = ""
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunPfd: ParcelFileDescriptor? = null
    private var sessionServerDisplayName: String? = null
    private var notificationWatchdogJob: Job? = null
    private var connectJob: Job? = null
    private var lastNotificationStatusText: String = ""
    @Volatile private var running = false
    /** True while the FGS notification should remain visible (connecting or connected). */
    @Volatile private var foregroundDesired = false
    /** Bumped on every connect / stop so a stale blocking [connect] cannot tear down a newer session. */
    @Volatile private var connectGeneration = 0
    @Volatile private var isStopping = false
    @Volatile private var desiredConnection = false
    @Volatile private var networkAvailable = true
    private var isNetworkCallbackRegistered = false
    private var pendingConnect: PendingXrayConnect? = null
    private val sessionLock = Any()

    private data class PendingXrayConnect(
        val configText: String,
        val excludedRoutesPath: String?,
        val dnsServers: List<String>,
        val dnsIdentityEnabled: Boolean,
    )

    private val statePrefs: SharedPreferences by lazy {
        getSharedPreferences(OpenVpn3Service.PREFS_VPN_STATE, Context.MODE_PRIVATE)
    }

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

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
        createNotificationChannel()
        networkAvailable = hasUsableNetwork()
        registerNetworkCallbackSafely()
        VpnDebugLogger.d(TAG, "Service created")
    }

    override fun onDestroy() {
        isStopping = true
        desiredConnection = false
        connectGeneration++
        connectJob?.cancel()
        connectJob = null
        unregisterNetworkCallbackSafely()
        stopXraySession(broadcast = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundNow(getString(R.string.vpn_connecting_generic))
        }
        when (action) {
            ACTION_CONNECT -> {
                sessionServerDisplayName = intent.getStringExtra(EXTRA_SERVER_DISPLAY_NAME)
                    ?.trim()?.takeIf { it.isNotEmpty() }
                    ?: statePrefs.getString("selected_server_name", null)
                        ?.trim()?.takeIf { it.isNotEmpty() }
                desiredConnection = true
                isStopping = false
                val session = ++connectGeneration
                connectJob?.cancel()
                connectJob = serviceScope.launch { connect(intent, session) }
            }
            ACTION_DISCONNECT, ACTION_PAUSE -> {
                desiredConnection = false
                pendingConnect = null
                isStopping = true
                val stopSession = ++connectGeneration
                connectJob?.cancel()
                connectJob = null
                serviceScope.launch {
                    if (!XrayConnectSessionPolicy.shouldApplyStop(stopSession, connectGeneration)) {
                        return@launch
                    }
                    stopXraySession(broadcast = true)
                    if (!XrayConnectSessionPolicy.shouldApplyStop(stopSession, connectGeneration)) {
                        return@launch
                    }
                    stopForegroundCompat()
                    if (!XrayConnectSessionPolicy.shouldApplyStop(stopSession, connectGeneration)) {
                        return@launch
                    }
                    stopSelf()
                }
            }
            ACTION_QUERY_STATUS -> {
                val (name, info) = resolveLiveStatusForQuery()
                runHealthCheck("query_status")
                broadcastStatus(name, info, fromQuery = true)
                if (!desiredConnection && !running && tunPfd == null) {
                    stopSelf()
                }
            }
            else -> Unit
        }
        return START_STICKY
    }

    private fun connect(intent: Intent, session: Int) {
        var localPfd: ParcelFileDescriptor? = null
        try {
            if (!isCurrentConnect(session)) return

            if (!XrayCoreFacade.isAvailable()) {
                failCurrentConnect(
                    session,
                    "libXray is not available on this device (${Build.SUPPORTED_ABIS.joinToString()})",
                )
                return
            }

            val path = intent.getStringExtra(EXTRA_CONFIG_PATH)
            val inline = intent.getStringExtra(EXTRA_CONFIG_TEXT)
            val raw = when {
                !path.isNullOrBlank() -> File(path).readText()
                !inline.isNullOrBlank() -> inline
                else -> {
                    failCurrentConnect(session, "Missing Xray config")
                    return
                }
            }

            if (!isCurrentConnect(session)) return

            val excludedRoutesPath = intent.getStringExtra(EXTRA_EXCLUDED_ROUTES_PATH)
            val intentRoutes = excludedRoutesPath
                ?.takeIf { it.isNotBlank() }
                ?.let { path ->
                    runCatching {
                        IpListRouteConfig.parseCidrRoutesResult(File(path).readText()).routes
                    }.getOrElse { emptyList() }
                }
                ?: emptyList()
            val dnsServers = resolveDnsServers(intent)
            val dnsIdentityEnabled = intent.getBooleanExtra(EXTRA_DNS_IDENTITY_ENABLED, false)
            pendingConnect = PendingXrayConnect(
                configText = raw,
                excludedRoutesPath = excludedRoutesPath,
                dnsServers = dnsServers,
                dnsIdentityEnabled = dnsIdentityEnabled,
            )

            if (!networkAvailable &&
                XrayNetworkPolicy.shouldWaitForNetwork(
                    desiredConnection = desiredConnection,
                    stopping = isStopping,
                    running = running,
                    networkAvailable = false,
                )
            ) {
                broadcastStatus("WAITING_NETWORK", getString(R.string.vpn_msg_reconnecting))
                startForegroundNow(getString(R.string.vpn_msg_reconnecting))
                return
            }

            broadcastStatus("CONNECTING", getString(R.string.vpn_connecting_generic))
            startForegroundNow(getString(R.string.vpn_connecting_generic))

            if (!isCurrentConnect(session)) return

            // Tear down any previous session in this service.
            stopXraySession(broadcast = false)

            if (!isCurrentConnect(session)) return

            val excludedRoutes = ExcludeRouteSession.resolveForEstablish(
                intentRoutes = intentRoutes,
                forXray = true,
                supportsAndroidRouteExclusion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                constrainedDevice = isTelevision(this),
            )
            val builder = Builder()
                .setSession(sessionServerDisplayName ?: "DataGate Xray")
                .setMtu(1500)
                .addAddress("10.10.10.2", 30)
                .addRoute("0.0.0.0", 0)
                .setBlocking(false)
            dnsServers.forEach { builder.addDnsServer(it) }

            // IPv4-only TUN: keep native IPv6 on the underlying network (same as OpenVPN on TV).
            // Without this, some OEMs still try to send IPv6 into the VPN and blackhole dual-stack apps.
            runCatching {
                builder.allowFamily(OsConstants.AF_INET6)
            }.onFailure { VpnDebugLogger.w(TAG, "allowFamily(AF_INET6) failed", it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val bypassApps = SplitTunnelSession.bypassAppsResolver(this)
            val appliedBypassApps = VpnBypassApps.applyToBuilder(builder, bypassApps)

            val appliedExcludes = VpnExcludeRoutes.applyToBuilder(builder, excludedRoutes)
            // Android 12-: excludeRoute is a no-op; inject the same CIDRs into Xray routing → direct.
            // Requires VpnService.protect on freedom sockets (registered below before runFromJson).
            val routingBypassCidrs =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    excludedRoutes.map { it.toCidrString() }
                } else {
                    emptyList()
                }
            VpnDebugLogger.event(
                category = "xray.tun",
                action = "establish",
                details = mapOf(
                    "excludeRoutes" to excludedRoutes.size,
                    "excludeApplied" to appliedExcludes,
                    "bypassAppsApplied" to appliedBypassApps,
                    "routingDirectBypass" to routingBypassCidrs.size,
                    "dnsServers" to dnsServers.joinToString(","),
                    "sdk" to Build.VERSION.SDK_INT,
                ),
            )

            if (!isCurrentConnect(session)) return

            // Register protect before establish/run so any early dials cannot loop into TUN.
            XrayCoreFacade.registerProtect(this)

            if (!isCurrentConnect(session)) return

            val pfd = builder.establish()
            localPfd = pfd
            if (pfd == null) {
                failCurrentConnect(session, "VpnService.Builder.establish() returned null")
                return
            }
            if (!isCurrentConnect(session)) {
                abandonLocalTun(pfd)
                return
            }
            tunPfd = pfd
            VpnTunnelSessionStore.recordVpnIp(
                applicationContext,
                "10.10.10.2",
                owner = VpnTunnelSessionStore.OWNER_XRAY,
            )
            VpnTunnelSessionStore.recordDnsServers(
                applicationContext,
                dnsServers,
                owner = VpnTunnelSessionStore.OWNER_XRAY,
            )
            VpnTunnelSessionStore.recordDnsIdentityEnabled(
                applicationContext,
                dnsIdentityEnabled,
                owner = VpnTunnelSessionStore.OWNER_XRAY,
            )

            if (!isCurrentConnect(session)) {
                abandonLocalTun(pfd)
                return
            }

            val fullConfig = XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = raw,
                tunFd = pfd.fd,
                directBypassCidrs = routingBypassCidrs,
                tunnelDnsServers = dnsServers,
            )
            val startedCore = synchronized(sessionLock) {
                if (!isCurrentConnect(session)) {
                    false
                } else {
                    XrayCoreFacade.runFromJson(fullConfig)
                    true
                }
            }
            if (!startedCore || !isCurrentConnect(session)) {
                // A newer connect or stop owns teardown. Closing only our TUN avoids
                // XrayCoreFacade.stop() killing a session that already replaced us.
                abandonLocalTun(pfd)
                return
            }
            running = true
            broadcastStatus("CONNECTED", getString(R.string.vpn_msg_connected))
            startForegroundNow(getString(R.string.vpn_status_connected))
            VpnTrafficMonitor.start(owner = VpnTunnelSessionStore.OWNER_XRAY) {
                VpnTunIfaceCounters.read(applicationContext)
            }
            VpnDiagnostics.schedulePostConnect(applicationContext, engine = "xray")
            runHealthCheck("post_connected")
            runCatching { path?.let { File(it).delete() } }
            excludedRoutesPath?.let { runCatching { File(it).delete() } }
        } catch (t: Throwable) {
            if (!isCurrentConnect(session)) {
                localPfd?.let { abandonLocalTun(it) }
                return
            }
            VpnDebugLogger.e(TAG, "Xray connect failed", t)
            desiredConnection = false
            pendingConnect = null
            stopXraySession(broadcast = false)
            broadcastStatus("ERROR", t.message ?: t.javaClass.simpleName)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun isCurrentConnect(session: Int): Boolean =
        XrayConnectSessionPolicy.isCurrent(session, connectGeneration, isStopping)

    private fun failCurrentConnect(session: Int, message: String) {
        if (!isCurrentConnect(session)) return
        desiredConnection = false
        pendingConnect = null
        stopXraySession(broadcast = false)
        broadcastStatus("ERROR", message)
        stopForegroundCompat()
        stopSelf()
    }

    private fun abandonLocalTun(pfd: ParcelFileDescriptor) {
        pfd.safeClose()
        if (tunPfd === pfd) {
            tunPfd = null
        }
    }

    private fun stopXraySession(broadcast: Boolean) {
        synchronized(sessionLock) {
            running = false
            VpnTrafficMonitor.stop(VpnTunnelSessionStore.OWNER_XRAY)
            runCatching { XrayCoreFacade.stop() }
            tunPfd.safeClose()
            tunPfd = null
            VpnTunnelSessionStore.clear(
                applicationContext,
                expectedOwner = VpnTunnelSessionStore.OWNER_XRAY,
            )
        }
        if (broadcast) {
            broadcastStatus("DISCONNECTED", getString(R.string.vpn_msg_disconnected))
        }
    }

    private fun resolveLiveStatusForQuery(): Pair<String, String> =
        XrayQueryStatusPolicy.resolve(
            running = running,
            hasTun = tunPfd != null,
            stopping = isStopping,
            lastEventName = lastEventName,
            lastEventInfo = lastEventInfo,
            disconnectedInfo = getString(R.string.vpn_msg_disconnected),
            connectedInfo = getString(R.string.vpn_msg_connected),
            connectingInfo = getString(R.string.vpn_connecting_generic),
        )

    private fun hasUsableNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return XrayNetworkPolicy.hasUsableNetwork(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    private fun runHealthCheck(trigger: String) {
        val caps = connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasVpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        if (XrayNetworkPolicy.shouldWarnMissingVpnTransport(running, hasVpnTransport)) {
            VpnDebugLogger.w(
                TAG,
                "system_vpn_transport_mismatch trigger=$trigger running=$running hasVpnTransport=$hasVpnTransport",
            )
        }
        if (running) {
            val coreRunning = runCatching { XrayCoreFacade.isRunning() }.getOrDefault(true)
            if (XrayNetworkPolicy.shouldRestartUnhealthySession(
                    desiredConnection = desiredConnection,
                    stopping = isStopping,
                    running = running,
                    coreRunning = coreRunning,
                    networkAvailable = networkAvailable,
                )
            ) {
                VpnDebugLogger.w(TAG, "xray core not running; reconnecting trigger=$trigger")
                reconnectFromPending()
            }
        }
    }

    private fun onNetworkStateChanged(source: String) {
        networkAvailable = hasUsableNetwork()
        VpnDebugLogger.event(
            category = "network",
            action = "changed",
            details = mapOf(
                "source" to source,
                "usable" to networkAvailable,
                "running" to running,
                "desired" to desiredConnection,
                "engine" to OpenVpn3Service.ENGINE_XRAY,
            ),
        )
        if (running) {
            VpnDiagnostics.onNetworkChanged(applicationContext, engine = "xray")
        }
        runHealthCheck("network_changed_$source")
        when {
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = desiredConnection,
                stopping = isStopping,
                running = running,
                networkAvailable = networkAvailable,
            ) -> {
                broadcastStatus("RECONNECTING", getString(R.string.vpn_msg_reconnecting))
                reconnectFromPending()
            }
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = desiredConnection,
                stopping = isStopping,
                running = running,
                networkAvailable = networkAvailable,
            ) -> {
                broadcastStatus("WAITING_NETWORK", getString(R.string.vpn_msg_reconnecting))
            }
        }
    }

    private fun reconnectFromPending() {
        val pending = pendingConnect ?: return
        isStopping = false
        val session = ++connectGeneration
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            connectFromPending(pending, session)
        }
    }

    private fun connectFromPending(pending: PendingXrayConnect, session: Int) {
        val intent = Intent().apply {
            putExtra(EXTRA_CONFIG_TEXT, pending.configText)
            pending.excludedRoutesPath?.let { putExtra(EXTRA_EXCLUDED_ROUTES_PATH, it) }
            putStringArrayListExtra(EXTRA_DNS_SERVERS, ArrayList(pending.dnsServers))
            putExtra(EXTRA_DNS_IDENTITY_ENABLED, pending.dnsIdentityEnabled)
        }
        connect(intent, session)
    }

    private fun registerNetworkCallbackSafely() {
        if (isNetworkCallbackRegistered) return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = true
        }.onFailure {
            VpnDebugLogger.w(TAG, "registerDefaultNetworkCallback failed", it)
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

    private fun resolveDnsServers(intent: Intent): List<String> =
        XrayVpnDns.resolveFromIntentExtras(intent.getStringArrayListExtra(EXTRA_DNS_SERVERS))

    /** Prefer "Server · Connected" so status stays visible when a display name is set. */
    private fun notificationBody(status: String): String {
        val name = sessionServerDisplayName?.takeIf { it.isNotBlank() }
        return if (name != null) "$name · $status" else status
    }

    private fun startForegroundNow(statusText: String) {
        lastNotificationStatusText = statusText
        foregroundDesired = true
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ensureNotificationWatchdogRunning()
    }

    private fun requiresForegroundNotification(): Boolean = foregroundDesired

    private fun isNotificationPosted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        return nm.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    /**
     * Android 14+ allows dismissing FGS notifications; re-post like [OpenVpn3Service]
     * so Pause/Disconnect stay available while the tunnel is up.
     */
    private fun ensureNotificationWatchdogRunning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (notificationWatchdogJob?.isActive == true) return
        notificationWatchdogJob = serviceScope.launch {
            while (isActive && requiresForegroundNotification()) {
                delay(3_000)
                if (!isNotificationPosted()) {
                    VpnDebugLogger.w(TAG, "VPN notification dismissed; re-posting foreground notification")
                    val text = lastNotificationStatusText.ifBlank {
                        getString(R.string.vpn_status_connected)
                    }
                    startForegroundNow(text)
                }
            }
        }
    }

    private fun stopNotificationWatchdog() {
        notificationWatchdogJob?.cancel()
        notificationWatchdogJob = null
    }

    private fun buildNotification(statusText: String): Notification {
        val disconnectIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pauseIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_PAUSE
        }
        val disconnectPending = PendingIntent.getService(
            this,
            3,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )
        val pausePending = PendingIntent.getService(
            this,
            4,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.login_title))
            .setContentText(notificationBody(statusText))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                }
            }
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.action_pause),
                    pausePending,
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.action_disconnect),
                    disconnectPending,
                ).build()
            )
            .build()
            .apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }
    }

    private fun stopForegroundCompat() {
        foregroundDesired = false
        stopNotificationWatchdog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
    }

    private fun pendingIntentImmutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    /** Avoid developer placeholders like UNKNOWN / "No status yet" on status query. */
    private fun resolvedCachedStatusForUi(): Pair<String, String> {
        val name = lastEventName.trim()
        val info = lastEventInfo.trim()
        if (name.isEmpty() || name.equals("UNKNOWN", ignoreCase = true)) {
            return "DISCONNECTED" to getString(R.string.vpn_msg_disconnected)
        }
        if (info.isEmpty() || info.equals("No status yet", ignoreCase = true)) {
            val friendly = when {
                name.equals("DISCONNECTED", ignoreCase = true) ->
                    getString(R.string.vpn_msg_disconnected)
                name.equals("CONNECTED", ignoreCase = true) ->
                    getString(R.string.vpn_msg_connected)
                name.equals("CONNECTING", ignoreCase = true) ->
                    getString(R.string.vpn_msg_connecting)
                else -> info
            }
            return name to friendly
        }
        return name to info
    }

    private fun broadcastStatus(name: String, info: String, fromQuery: Boolean = false) {
        if (!fromQuery) {
            lastEventName = name
            lastEventInfo = info
            statePrefs.edit()
                .putString(OpenVpn3Service.PREF_LAST_EVENT_NAME, name)
                .putString(OpenVpn3Service.PREF_LAST_EVENT_INFO, info)
                .putLong(OpenVpn3Service.PREF_LAST_EVENT_AT_MS, System.currentTimeMillis())
                .apply()
        }
        VpnDebugLogger.event(
            category = "service.broadcast",
            action = name,
            details = mapOf(
                "info" to info,
                "fromQuery" to fromQuery,
                "engine" to OpenVpn3Service.ENGINE_XRAY,
                "running" to running,
            ),
        )
        val intent = Intent(OpenVpn3Service.ACTION_STATUS)
            .setPackage(packageName)
            .apply {
                putExtra(OpenVpn3Service.EXTRA_EVENT_NAME, name)
                putExtra(OpenVpn3Service.EXTRA_EVENT_INFO, info)
                putExtra(OpenVpn3Service.EXTRA_STATUS_FROM_QUERY, fromQuery)
                putExtra(OpenVpn3Service.EXTRA_STATUS_ENGINE, OpenVpn3Service.ENGINE_XRAY)
            }
        sendBroadcast(intent)
    }
}
