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
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.VpnDebugLogger
import com.imkolganov.datagate.vpn.OpenVpn3Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android [VpnService] that runs XTLS/libXray with a TUN fd injected into the client config.
 * Status broadcasts use the same action/extras as [OpenVpn3Service] so Home/Access UI stays shared.
 */
@SuppressLint("VpnServicePolicy")
class XrayVpnService : VpnService() {

    companion object {
        private const val TAG = "XrayVpn"
        const val ACTION_CONNECT = "com.imkolganov.datagate.vpn.xray.CONNECT"
        const val ACTION_DISCONNECT = "com.imkolganov.datagate.vpn.xray.DISCONNECT"
        const val ACTION_QUERY_STATUS = "com.imkolganov.datagate.vpn.xray.QUERY_STATUS"
        const val EXTRA_CONFIG_PATH = "com.imkolganov.datagate.vpn.xray.EXTRA_CONFIG_PATH"
        const val EXTRA_CONFIG_TEXT = "com.imkolganov.datagate.vpn.xray.EXTRA_CONFIG_TEXT"
        const val EXTRA_SERVER_DISPLAY_NAME = "com.imkolganov.datagate.vpn.xray.EXTRA_SERVER_DISPLAY_NAME"

        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "xray_vpn_channel"

        @Volatile
        private var lastEventName: String = "UNKNOWN"

        @Volatile
        private var lastEventInfo: String = "No status yet"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunPfd: ParcelFileDescriptor? = null
    private var sessionServerDisplayName: String? = null
    @Volatile private var running = false

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
                serviceScope.launch { connect(intent) }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    stopXraySession(broadcast = true)
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            ACTION_QUERY_STATUS -> {
                broadcastStatus(lastEventName, lastEventInfo, fromQuery = true)
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
            startForegroundNow(notificationBody(getString(R.string.vpn_connecting_generic)))

            // Tear down any previous session in this service.
            stopXraySession(broadcast = false)

            val builder = Builder()
                .setSession(sessionServerDisplayName ?: "DataGate Xray")
                .setMtu(1500)
                .addAddress("10.10.10.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd = builder.establish()
            if (pfd == null) {
                broadcastStatus("ERROR", "VpnService.Builder.establish() returned null")
                stopForegroundCompat()
                stopSelf()
                return
            }
            tunPfd = pfd

            XrayCoreFacade.registerProtect(this)
            val fullConfig = XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = raw,
                tunFd = pfd.fd,
            )
            XrayCoreFacade.runFromJson(fullConfig)
            running = true
            broadcastStatus("CONNECTED", getString(R.string.vpn_msg_connected))
            startForegroundNow(notificationBody(getString(R.string.vpn_status_connected)))
            runCatching { path?.let { File(it).delete() } }
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
        if (broadcast) {
            broadcastStatus("DISCONNECTED", getString(R.string.vpn_msg_disconnected))
        }
    }

    private fun notificationBody(fallback: String): String =
        sessionServerDisplayName?.takeIf { it.isNotBlank() } ?: fallback

    private fun startForegroundNow(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val disconnectIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this,
            3,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_disconnect), disconnectPending)
            .build()
    }

    private fun stopForegroundCompat() {
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
            }
        sendBroadcast(intent)
    }
}
