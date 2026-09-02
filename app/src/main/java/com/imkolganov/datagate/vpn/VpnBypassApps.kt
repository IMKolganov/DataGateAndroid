package com.imkolganov.datagate.vpn

import android.net.VpnService
import com.imkolganov.datagate.logger.VpnDebugLogger

/**
 * Applies per-app split tunneling via [VpnService.Builder.addDisallowedApplication].
 * Same mechanism for OpenVPN and Xray TUN sessions.
 *
 * The OS only reads the disallowed list at `establish()`, so the selection is resolved per
 * establish (see [SplitTunnelSession]) rather than snapshotted when the session started: an
 * OpenVPN reconnect rebuilds the TUN and must pick up edits made while the tunnel was up.
 */
object VpnBypassApps {
    private const val TAG = "VpnBypassApps"

    /**
     * Resolves [bypassApps] at call time — once per establish — and applies the result.
     *
     * @return number of packages successfully passed to
     * [VpnService.Builder.addDisallowedApplication].
     */
    fun applyToBuilder(builder: VpnService.Builder, bypassApps: () -> List<String>): Int =
        apply(bypassApps) { builder.addDisallowedApplication(it) }

    /**
     * Resolves the selection, then adds each package under its own try/catch so a package
     * uninstalled since selection cannot drop the rest of the list.
     *
     * Exposed to tests, which cannot build a real [VpnService.Builder].
     */
    internal fun apply(
        bypassApps: () -> List<String>,
        addDisallowedApplication: (String) -> Unit,
    ): Int {
        val bypassPackages = bypassApps()
        if (bypassPackages.isEmpty()) return 0

        var applied = 0
        for (packageName in bypassPackages) {
            try {
                addDisallowedApplication(packageName)
                applied++
            } catch (t: Throwable) {
                VpnDebugLogger.w(TAG, "addDisallowedApplication failed for $packageName", t)
            }
        }
        VpnDebugLogger.d(TAG, "Applied bypass apps: $applied/${bypassPackages.size}")
        return applied
    }
}
