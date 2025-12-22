package com.imkolganov.datagate.vpn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.imkolganov.datagate.R

class VpnNotificationManager(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "vpn_status"
        const val CHANNEL_NAME = "VPN status"
        const val NOTIFICATION_ID = 1001

        const val ACTION_DISCONNECT = "com.imkolganov.datagate.vpn.ACTION_DISCONNECT"
        const val ACTION_PAUSE = "com.imkolganov.datagate.vpn.ACTION_PAUSE"
        const val ACTION_RESUME = "com.imkolganov.datagate.vpn.ACTION_RESUME"
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows VPN connection status"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun buildOngoing(
        title: String,
        text: String,
        isPaused: Boolean,
    ): Notification {
        ensureChannel()

        val disconnectIntent = Intent(context, OpenVpn3Service::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pauseOrResumeIntent = Intent(context, OpenVpn3Service::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }

        val disconnectPending = PendingIntent.getService(
            context,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT //or pendingIntentImmutableFlag()
        )

        val pauseOrResumePending = PendingIntent.getService(
            context,
            2,
            pauseOrResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT //or pendingIntentImmutableFlag()
        )

        val pauseOrResumeLabel = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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

//    @SuppressLint("ObsoleteSdkInt")
//    private fun pendingIntentImmutableFlag(): Int {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
//    }
}
