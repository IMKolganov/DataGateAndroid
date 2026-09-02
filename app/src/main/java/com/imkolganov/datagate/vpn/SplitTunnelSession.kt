package com.imkolganov.datagate.vpn

import android.content.Context

/**
 * Single source of truth for the bypass list used by a TUN session.
 *
 * The resolver is deliberately late-bound: [VpnBypassApps] calls it once per `establish()`, so an
 * OpenVPN reconnect and an explicit reconnect both apply the current selection instead of the one
 * that happened to be stored when the session began.
 */
object SplitTunnelSession {

    fun bypassAppsResolver(context: Context): () -> List<String> {
        val appContext = context.applicationContext
        return { resolveBypassApps(appContext) }
    }

    fun resolveBypassApps(context: Context): List<String> =
        SplitTunnelPolicy.bypassPackagesForSession(
            settings = SplitTunnelStore.getSettings(context),
            selfPackage = context.packageName,
        )
}
