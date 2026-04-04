package com.imkolganov.datagate.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imkolganov.datagate.MainActivity
import com.imkolganov.datagate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateNotificationHelper {

    const val EXTRA_OPEN_UPDATE_FROM_NOTIF = "com.imkolganov.datagate.EXTRA_OPEN_UPDATE_FROM_NOTIF"

    private const val CHANNEL_ID = "update_available"

    /** Must match [com.imkolganov.datagate.update.UpdatePreferences.dismissRelease] cancel id. */
    const val NOTIFICATION_ID_UPDATE_AVAILABLE = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.update_notification_channel_description)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Shows a local notification when a newer GitHub release was just detected (after network check).
     */
    suspend fun showNewVersionAvailableIfEligible(context: Context, release: GitHubLatestRelease) {
        if (!UpdatePreferences.isPushForUpdatesEnabled(context)) return
        if (!UpdatePreferences.isCheckEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (UpdatePreferences.getLastPushedUpdateTag(context) == release.tagName) return

        withContext(Dispatchers.Main) {
            ensureChannel(context)
            val appCtx = context.applicationContext
            val open = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_UPDATE_FROM_NOTIF, true)
            }
            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pending = PendingIntent.getActivity(appCtx, NOTIFICATION_ID_UPDATE_AVAILABLE, open, piFlags)

            val n = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_vpn)
                .setContentTitle(appCtx.getString(R.string.update_notification_title))
                .setContentText(appCtx.getString(R.string.update_notification_text, release.tagName))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    appCtx.getString(R.string.update_notification_text, release.tagName)
                ))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID_UPDATE_AVAILABLE, n)
        }
        UpdatePreferences.recordUpdateNotificationShown(context, release.tagName)
    }

    fun cancelUpdateAvailableNotification(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID_UPDATE_AVAILABLE)
    }
}
