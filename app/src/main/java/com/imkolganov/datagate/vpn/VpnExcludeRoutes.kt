package com.imkolganov.datagate.vpn

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import com.imkolganov.datagate.logger.VpnDebugLogger
import java.net.InetAddress

/**
 * Applies CIDR bypass lists via [VpnService.Builder.excludeRoute] (Android 13+).
 * Same mechanism for OpenVPN and Xray TUN sessions.
 */
object VpnExcludeRoutes {
    private const val TAG = "VpnExcludeRoutes"

    /**
     * @return number of routes successfully passed to [VpnService.Builder.excludeRoute].
     */
    fun applyToBuilder(builder: VpnService.Builder, excludedRoutes: List<IpCidrRoute>): Int {
        if (excludedRoutes.isEmpty()) return 0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            VpnDebugLogger.w(
                TAG,
                "Skipping ${excludedRoutes.size} excluded routes: excludeRoute requires Android 13+",
            )
            return 0
        }
        var applied = 0
        for (route in excludedRoutes) {
            try {
                builder.excludeRoute(
                    IpPrefix(
                        InetAddress.getByName(route.networkAddress),
                        route.prefixLength,
                    ),
                )
                applied++
            } catch (t: Throwable) {
                VpnDebugLogger.w(TAG, "excludeRoute failed for ${route.toCidrString()}", t)
            }
        }
        VpnDebugLogger.d(TAG, "Applied excluded routes: $applied/${excludedRoutes.size}")
        return applied
    }
}
