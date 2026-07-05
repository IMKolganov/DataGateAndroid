package com.imkolganov.datagate.vpn

import android.content.Context
import com.imkolganov.datagate.util.NetworkIdentitySnapshot

/**
 * Tunnel IP/DNS applied by OpenVPN during the current session.
 * Android often does not expose these via [android.net.ConnectivityManager] on VPN networks.
 */
object VpnTunnelSessionStore {
    private const val PREFS = "vpn_tunnel_session"
    private const val KEY_VPN_IP = "vpn_ip4"
    private const val KEY_DNS = "dns_servers"

    fun recordVpnIp(context: Context, address: String) {
        val ip = address.trim().takeIf { it.isNotEmpty() } ?: return
        prefs(context).edit().putString(KEY_VPN_IP, ip).apply()
    }

    fun recordDnsServers(context: Context, servers: List<String>) {
        val cleaned = servers.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        if (cleaned.isEmpty()) return
        prefs(context).edit().putString(KEY_DNS, cleaned.joinToString(",")).apply()
    }

    fun read(context: Context): NetworkIdentitySnapshot {
        val p = prefs(context)
        val ip = p.getString(KEY_VPN_IP, null)?.trim()?.takeIf { it.isNotEmpty() }
        val dns = p.getString(KEY_DNS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .orEmpty()
        return NetworkIdentitySnapshot(vpnIpAddress = ip, dnsServers = dns)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
