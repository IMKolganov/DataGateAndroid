package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
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
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@SuppressLint("VpnServicePolicy")
class OpenVpn3Service : VpnService() {

    companion object {
        const val EXTRA_OVPN_CONFIG = "com.imkolganov.datagate.vpn.EXTRA_OVPN_CONFIG"

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
    private val WSS_URL = "wss://your-wss"
    private val BRIDGE_PORT = 41194
    private var bridge: TcpToWssBridge? = null
    private var bridgeHttp: okhttp3.OkHttpClient? = null

    @Volatile private var connectInProgress = false
    @Volatile private var hasActiveSession = false
    @Volatile private var isPaused = false
    @Volatile private var isStopping = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var vpnClient: OpenVpn3Client? = null
    private var vpnJob: Job? = null

    private val crashLogger: CrashLogger
        get() = (application as DataGateApp).crashLogger

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopVpnInternal()
        super.onDestroy()
    }

//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//
//    }

    private fun buildNotification(text: String): Notification {
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
            .setContentTitle("OpenVPN 3")
            .setContentText(text)
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
                    "Disconnect",
                    disconnectPending
                ).build()
            )
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
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
                "OpenVPN 3",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun startVpn(configText: String) {
        stopVpnInternal()

        val http = buildProtectedOkHttp(this@OpenVpn3Service)


        bridgeHttp = http

        bridge = TcpToWssBridge(
            service = this@OpenVpn3Service,
            port = BRIDGE_PORT,
            wssUrl = WSS_URL,
            http = http
        ).also { it.start() }

        vpnJob = serviceScope.launch {
            try {
                Log.d(TAG, "startVpn: building config")

                val patchedConfig = forceRemoteToLocalBridge(configText, BRIDGE_PORT)

                val cfg = ClientAPI_Config().apply {
                    content = patchedConfig
                }

                val client = OpenVpn3Client(
                    service = this@OpenVpn3Service,
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

        try { bridge?.stop() } catch (_: Throwable) {}
        bridge = null

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action")

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForeground(NOTIFICATION_ID, buildNotification("Running..."))
//        }

        when (action) {
            ACTION_CONNECT -> {
                isStopping = false
                val configText = intent.getStringExtra(EXTRA_OVPN_CONFIG)
                if (configText.isNullOrBlank()) {
                    Log.e(TAG, "ACTION_CONNECT missing config")
                    broadcastStatus("ERROR", "Missing config")
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
                startVpn(configText)
            }

            ACTION_DISCONNECT -> {
                Log.d(TAG, "ACTION_DISCONNECT received")

                isStopping = true

                stopVpnInternal()
                connectInProgress = false
                hasActiveSession = false
                isPaused = false

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
                Log.d(TAG, "ACTION_QUERY_STATUS received")
                broadcastStatus(lastEventName, lastEventInfo)
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
