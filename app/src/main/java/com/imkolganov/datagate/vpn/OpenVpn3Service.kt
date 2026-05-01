package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.DataGateApp
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.openvpn.ovpn3.ClientAPI_Config
import net.openvpn.ovpn3.ClientAPI_EvalConfig
import net.openvpn.ovpn3.ClientAPI_ProvideCreds
import net.openvpn.ovpn3.ClientAPI_Status
import java.io.File
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

        init {
            System.loadLibrary("ovpncli")
        }

        private const val TAG = "OpenVPN3"
        const val ACTION_CONNECT = "com.imkolganov.datagate.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.imkolganov.datagate.vpn.DISCONNECT"

        const val ACTION_STATUS = "com.imkolganov.datagate.vpn.STATUS"
        const val EXTRA_EVENT_NAME = "event_name"
        const val EXTRA_EVENT_INFO = "event_info"

        const val ACTION_QUERY_STATUS = "com.imkolganov.datagate.vpn.QUERY_STATUS"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "openvpn3_channel"
        private var lastEventName: String = "UNKNOWN"
        private var lastEventInfo: String = "No status yet"

        const val ACTION_PAUSE = "com.imkolganov.datagate.vpn.PAUSE"
        const val ACTION_RESUME = "com.imkolganov.datagate.vpn.RESUME"
    }
    private val BRIDGE_PORT = 41194
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
    private var isNetworkCallbackRegistered = false

    private var vpnClient: OpenVpn3Client? = null
    private var vpnJob: Job? = null

    private val crashLogger: CrashLogger
        get() = (application as DataGateApp).crashLogger

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
        Log.d(TAG, "Service created")
        createNotificationChannel()
        registerNetworkCallbackSafely()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        unregisterNetworkCallbackSafely()
        stopVpnInternal()
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

        val pauseOrResumeLabel = if (isPaused) "Resume" else "Pause"

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
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
//            .addAction(
//                NotificationCompat.Action.Builder(
//                    0,
//                    pauseOrResumeLabel,
//                    pauseOrResumePending
//                ).build()
//            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.action_disconnect),
                    disconnectPending
                ).build()
            )
            .build()
    }

    private fun updateNotification(fallbackText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(fallbackText))
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
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun startVpn(
        configText: String,
        wssUrl: String,
        linkProtocol: VpnLinkProtocol,
        excludedRoutes: List<IpCidrRoute>
    ) {
        stopVpnInternal()

        val http = buildProtectedOkHttp(this@OpenVpn3Service)

        bridgeHttp = http

        when (linkProtocol) {
            VpnLinkProtocol.UDP -> {
                val b = UdpToWssBridge(
                    service = this@OpenVpn3Service,
                    port = BRIDGE_PORT,
                    wssUrl = wssUrl,
                    http = http
                )
                b.start()
                bridgeStop = { b.stop() }
            }
            VpnLinkProtocol.TCP -> {
                val b = TcpToWssBridge(
                    service = this@OpenVpn3Service,
                    port = BRIDGE_PORT,
                    wssUrl = wssUrl,
                    http = http
                )
                b.start()
                bridgeStop = { b.stop() }
            }
        }

        vpnJob = serviceScope.launch {
            try {
                Log.d(TAG, "startVpn: building config")

                val patchedConfig = forceRemoteToLocalBridge(configText, BRIDGE_PORT, linkProtocol)

                val cfg = ClientAPI_Config().apply {
                    content = patchedConfig
                    enableRouteEmulation = true
                }

                val client = OpenVpn3Client(
                    service = this@OpenVpn3Service,
                    excludedRoutes = excludedRoutes,
                    onTunChanged = { fd ->
                        Log.d(TAG, "TUN changed (fd=${fd?.fd ?: -1})")
                    },
                    onCoreEvent = { name, info ->
                        if (name.equals("CONNECTED", ignoreCase = true)) {
                            hasActiveSession = true
                            connectInProgress = false
                            isPaused = false
                            updateNotification("Connected")
                        }
                        if (name.equals("DISCONNECTED", ignoreCase = true)) {
                            hasActiveSession = false
                            connectInProgress = false
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

                            if (!isStopping) {
                                // optional: show a short status if it was unexpected
                                // updateNotification("Disconnected")
                            } else {
                                // we intentionally disconnected, do not recreate notification
                            }
                        }
                        broadcastStatus(name, info)
                    }
                )
                vpnClient = client

                Log.d(TAG, "startVpn: eval_config")
                val eval: ClientAPI_EvalConfig = client.eval_config(cfg)
                if (eval.error) {
                    Log.e(TAG, "eval_config error: ${eval.message}")
                    broadcastStatus("ERROR", eval.message ?: "OpenVPN profile validation failed")
                    stopSelf()
                    return@launch
                }

                Log.d(TAG, "startVpn: provide_creds")
                val creds = ClientAPI_ProvideCreds().apply {
                    username = ""
                    password = ""
                }
                val credStatus: ClientAPI_Status = client.provide_creds(creds)
                if (credStatus.error) {
                    Log.e(TAG, "provide_creds error: ${credStatus.message}")
                    broadcastStatus("ERROR", credStatus.message ?: "OpenVPN credentials failed")
                    stopSelf()
                    return@launch
                }

                Log.d(TAG, "startVpn: connect()")
                val status: ClientAPI_Status = client.connect()
                Log.d(TAG, "connect() finished: error=${status.error} message=${status.message}")

                stopVpnInternal()
                stopSelf()
            } catch (t: Throwable) {
                Log.e(TAG, "startVpn error", t)
                broadcastStatus("ERROR", t.message ?: t.javaClass.simpleName)
                crashLogger.logNonFatal("OpenVpn3Service.startVpn", t)
            } finally {
                connectInProgress = false
                stopVpnInternal()
                stopSelf()
            }
        }
    }

    private fun stopVpnInternal() {
        Log.d(TAG, "stopVpnInternal")

        vpnJob?.cancel()
        vpnJob = null

        vpnClient?.let {
            try {
                Log.d(TAG, "Calling client.stop()")
                it.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "client.stop() failed", t)
            }
        }
        vpnClient = null

        try {
            bridgeStop?.invoke()
        } catch (_: Throwable) {
        }
        bridgeStop = null

        try { bridgeHttp?.dispatcher?.executorService?.shutdown() } catch (_: Throwable) {}
        try { bridgeHttp?.connectionPool?.evictAll() } catch (_: Throwable) {}
        bridgeHttp = null
    }

    private fun broadcastStatus(name: String, info: String) {
        lastEventName = name
        lastEventInfo = info

        val intent = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .apply {
                putExtra(EXTRA_EVENT_NAME, name)
                putExtra(EXTRA_EVENT_INFO, info)
            }

        sendBroadcast(intent)
    }

    private fun registerNetworkCallbackSafely() {
        if (isNetworkCallbackRegistered) return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = true
        }.onFailure {
            Log.w(TAG, "registerDefaultNetworkCallback failed", it)
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
            Log.w(TAG, "unregisterNetworkCallback failed", it)
        }
        isNetworkCallbackRegistered = false
    }

    private fun onNetworkStateChanged(source: String) {
        lastNetworkChangeAtMs = SystemClock.elapsedRealtime()
        val transport = currentTransportLabel()
        val info = "$source:$transport"
        if (hasActiveSession || connectInProgress) {
            broadcastStatus("NETWORK_CHANGED", info)
        } else {
            lastEventName = "NETWORK_CHANGED"
            lastEventInfo = info
        }
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
        if (action == ACTION_CONNECT) {
            sessionServerDisplayName = intent.getStringExtra(EXTRA_SERVER_DISPLAY_NAME)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        val shouldBeForeground = action == ACTION_CONNECT || hasActiveSession || connectInProgress

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldBeForeground) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(if (hasActiveSession) "Connected" else "Connecting...")
            )
        }

        when (action) {
            ACTION_CONNECT -> {
                isStopping = false

                val configPath = intent.getStringExtra(EXTRA_OVPN_CONFIG_PATH)
                val excludedRoutesPath = intent.getStringExtra(EXTRA_EXCLUDED_ROUTES_PATH)
                val configText = intent.getStringExtra(EXTRA_OVPN_CONFIG)
                    ?: configPath?.let { path ->
                        runCatching { File(path).readText() }
                            .onFailure { Log.e(TAG, "Failed to read OVPN config file: $path", it) }
                            .getOrNull()
                    }
                val excludedRoutes = excludedRoutesPath
                    ?.let { path ->
                        runCatching {
                            IpListRouteConfig.parseCidrRoutesResult(File(path).readText()).routes
                        }
                            .onFailure { Log.e(TAG, "Failed to read excluded routes file: $path", it) }
                            .getOrNull()
                    }
                    .orEmpty()
                val wssUrl = intent.getStringExtra(EXTRA_WSS_URL)

                if (configText.isNullOrBlank() || wssUrl.isNullOrBlank()) {
                    Log.e(TAG, "ACTION_CONNECT missing config or WSS URL")
                    broadcastStatus("ERROR", "Missing config or WSS URL")
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (connectInProgress || hasActiveSession) {
                    Log.w(TAG, "ACTION_CONNECT ignored: already running")
                    broadcastStatus(lastEventName, lastEventInfo)
                    return START_STICKY
                }

                connectInProgress = true
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                val linkProtocol =
                    VpnLinkProtocol.fromIntentExtra(intent.getStringExtra(EXTRA_LINK_PROTOCOL))
                startVpn(configText, wssUrl, linkProtocol, excludedRoutes)
                configPath?.let { path ->
                    runCatching { File(path).delete() }
                        .onFailure { Log.w(TAG, "Failed to delete OVPN config file: $path", it) }
                }
                excludedRoutesPath?.let { path ->
                    runCatching { File(path).delete() }
                        .onFailure { Log.w(TAG, "Failed to delete excluded routes file: $path", it) }
                }
            }

            ACTION_DISCONNECT -> {
                Log.d(TAG, "ACTION_DISCONNECT received")

                isStopping = true

                stopVpnInternal()
                connectInProgress = false
                hasActiveSession = false
                isPaused = false
                sessionServerDisplayName = null

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

            ACTION_QUERY_STATUS -> {
                val name = when {
                    hasActiveSession -> "CONNECTED"
                    connectInProgress -> "CONNECTING"
                    else -> "DISCONNECTED"
                }
                val info = when {
                    hasActiveSession -> "Session active"
                    connectInProgress -> "Connecting..."
                    else -> "No active session"
                }

                broadcastStatus(name, info)
                stopSelf()
            }

            ACTION_PAUSE -> {
                Log.d(TAG, "ACTION_PAUSE received")
                isPaused = true

                // TODO: implement real pause behavior
                // Option A: call vpnClient?.pause() if your wrapper supports it
                // Option B: implement your own pause logic (depends on OpenVPN3 core API)

                updateNotification("Paused")
                broadcastStatus("PAUSED", "Paused by user")
            }

            ACTION_RESUME -> {
                Log.d(TAG, "ACTION_RESUME received")
                isPaused = false

                // TODO: implement real resume behavior
                updateNotification(if (hasActiveSession) "Connected" else "Connecting...")
                broadcastStatus("RESUMED", "Resumed by user")
            }

            null -> {
                Log.w(TAG, "Service restarted with null intent")
                broadcastStatus(lastEventName, lastEventInfo)
            }

            else -> {
                Log.w(TAG, "Unknown action: $action")
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
            .sslSocketFactory(protectingSslFactory, trustManager)
            .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
