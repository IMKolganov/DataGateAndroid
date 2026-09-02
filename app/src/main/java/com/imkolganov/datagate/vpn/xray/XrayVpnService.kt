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
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.VpnDebugLogger
import com.imkolganov.datagate.vpn.IpListRouteConfig
import com.imkolganov.datagate.vpn.OpenVpn3Service
import com.imkolganov.datagate.vpn.SplitTunnelSession
import com.imkolganov.datagate.vpn.VpnBypassApps
import com.imkolganov.datagate.vpn.VpnExcludeRoutes
import com.imkolganov.datagate.vpn.VpnTunnelSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var lastNotificationStatusText: String = ""
    @Volatile private var running = false
    /** True while the FGS notification should remain visible (connecting or connected). */
    @Volatile private var foregroundDesired = false

    private val statePrefs: SharedPreferences by lazy {
        getSharedPreferences(OpenVpn3Service.PREFS_VPN_STATE, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        VpnDebugLogger.d(TAG, "Service created")
    }

    override fun onDestroy() {
        stopXraySession(broadcast = false)
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
                serviceScope.launch { connect(intent) }
            }
            ACTION_DISCONNECT, ACTION_PAUSE -> {
                serviceScope.launch {
                    stopXraySession(broadcast = true)
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            ACTION_QUERY_STATUS -> {
                val (name, info) = resolvedCachedStatusForUi()
                broadcastStatus(name, info, fromQuery = true)
            }
            else -> Unit
        }
        return START_STICKY
    }

    private fun connect(intent: Intent) {
        try {
            if (!XrayCoreFacade.isAvailable()) {
                broadcastStatus(
                    "ERROR",
                    "libXray is not available on this device (${Build.SUPPORTED_ABIS.joinToString()})",
                )
                stopForegroundCompat()
                stopSelf()
                return
            }

            val path = intent.getStringExtra(EXTRA_CONFIG_PATH)
            val inline = intent.getStringExtra(EXTRA_CONFIG_TEXT)
            val raw = when {
                !path.isNullOrBlank() -> File(path).readText()
                !inline.isNullOrBlank() -> inline
                else -> {
                    broadcastStatus("ERROR", "Missing Xray config")
                    stopForegroundCompat()
                    stopSelf()
                    return
                }
            }

            broadcastStatus("CONNECTING", getString(R.string.vpn_connecting_generic))
            startForegroundNow(getString(R.string.vpn_connecting_generic))

            // Tear down any previous session in this service.
            stopXraySession(broadcast = false)

            val excludedRoutesPath = intent.getStringExtra(EXTRA_EXCLUDED_ROUTES_PATH)
            val excludedRoutes = excludedRoutesPath
                ?.takeIf { it.isNotBlank() }
                ?.let { path ->
                    runCatching {
                        IpListRouteConfig.parseCidrRoutesResult(File(path).readText()).routes
                    }.getOrElse { emptyList() }
                }
                ?: emptyList()

            val dnsServers = resolveDnsServers(intent)
            val dnsIdentityEnabled = intent.getBooleanExtra(EXTRA_DNS_IDENTITY_ENABLED, false)
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

            // Register protect before establish/run so any early dials cannot loop into TUN.
            XrayCoreFacade.registerProtect(this)

            val pfd = builder.establish()
            if (pfd == null) {
                broadcastStatus("ERROR", "VpnService.Builder.establish() returned null")
                stopForegroundCompat()
                stopSelf()
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

            val fullConfig = XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = raw,
                tunFd = pfd.fd,
                directBypassCidrs = routingBypassCidrs,
                tunnelDnsServers = dnsServers,
            )
            XrayCoreFacade.runFromJson(fullConfig)
            running = true
            broadcastStatus("CONNECTED", getString(R.string.vpn_msg_connected))
            startForegroundNow(getString(R.string.vpn_status_connected))
            runCatching { path?.let { File(it).delete() } }
            excludedRoutesPath?.let { runCatching { File(it).delete() } }
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "Xray connect failed", t)
            stopXraySession(broadcast = false)
            broadcastStatus("ERROR", t.message ?: t.javaClass.simpleName)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopXraySession(broadcast: Boolean) {
        running = false
        runCatching { XrayCoreFacade.stop() }
        tunPfd.safeClose()
        tunPfd = null
        VpnTunnelSessionStore.clear(applicationContext, expectedOwner = VpnTunnelSessionStore.OWNER_XRAY)
        if (broadcast) {
            broadcastStatus("DISCONNECTED", getString(R.string.vpn_msg_disconnected))
        }
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
        VpnDebugLogger.d(TAG, "broadcast $name: $info (fromQuery=$fromQuery running=$running)")
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
