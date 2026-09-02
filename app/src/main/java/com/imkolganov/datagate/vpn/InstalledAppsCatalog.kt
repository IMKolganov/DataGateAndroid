package com.imkolganov.datagate.vpn

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.content.pm.LauncherApps
import androidx.core.graphics.drawable.toBitmap
import com.imkolganov.datagate.logger.VpnDebugLogger

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

/**
 * Installed apps offered in the split tunneling picker: only packages that request
 * [Manifest.permission.INTERNET], since nothing else has traffic to keep out of the tunnel.
 */
object InstalledAppsCatalog {
    private const val TAG = "InstalledApps"

    /** Blocking package-manager work; call from a background dispatcher. */
    fun loadNetworkApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val packages = runCatching { installedPackagesWithPermissions(pm) }
            .onFailure { VpnDebugLogger.w(TAG, "getInstalledPackages failed", it) }
            .getOrElse { emptyList() }

        return packages.asSequence()
            .filter { it.packageName != context.packageName }
            .filter { it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true }
            .mapNotNull { info ->
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                InstalledAppInfo(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: info.packageName,
                    isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledAppInfo::label))
            .toList()
    }

    /** Blocking resource load; call from a background dispatcher. */
    fun loadIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? =
        runCatching {
            val drawable = launcherIconDrawable(context, packageName)
                ?: context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = sizePx, height = sizePx)
        }.getOrNull()

    /**
     * Same icon the launcher shows (adaptive background + foreground), not the raw package icon.
     */
    internal fun launcherIconDrawable(context: Context, packageName: String): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val activity = launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull()
            ?: return null
        return activity.getIcon(context.resources.displayMetrics.densityDpi)
    }

    private fun installedPackagesWithPermissions(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
}
