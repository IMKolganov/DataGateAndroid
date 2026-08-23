package com.imkolganov.datagate.vpn

import android.content.Context
import com.imkolganov.datagate.util.NetworkIdentitySnapshot

/**
 * Tunnel IP/DNS applied during the current VPN session.
 * Android often does not expose these via [android.net.ConnectivityManager] on VPN networks.
 *
 * [owner] prevents a late peer-engine teardown from wiping the active engine's session
 * (Xray disconnect racing OpenVPN establish, and vice versa).
 */
object VpnTunnelSessionStore {
    private const val PREFS = "vpn_tunnel_session"
    private const val KEY_VPN_IP = "vpn_ip4"
    private const val KEY_DNS = "dns_servers"
    private const val KEY_DNS_IDENTITY = "dns_identity_enabled"
    private const val KEY_OWNER = "session_owner"

    const val OWNER_OPENVPN = "openvpn"
    const val OWNER_XRAY = "xray"

    fun recordVpnIp(context: Context, address: String, owner: String = OWNER_OPENVPN) {
        val ip = address.trim().takeIf { it.isNotEmpty() } ?: return
        prefs(context).edit()
            .putString(KEY_OWNER, owner)
            .putString(KEY_VPN_IP, ip)
            .apply()
    }

    fun recordDnsServers(context: Context, servers: List<String>, owner: String = OWNER_OPENVPN) {
        val cleaned = servers.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        if (cleaned.isEmpty()) return
        val editor = prefs(context).edit()
            .putString(KEY_OWNER, owner)
            .putString(KEY_DNS, cleaned.joinToString(","))
        // OpenVPN never uses issued Xray identity DNS — drop a stale identity hint.
        if (owner == OWNER_OPENVPN) {
            editor.putBoolean(KEY_DNS_IDENTITY, false)
        }
        editor.apply()
    }

    fun recordDnsIdentityEnabled(context: Context, enabled: Boolean, owner: String = OWNER_XRAY) {
        prefs(context).edit()
            .putString(KEY_OWNER, owner)
            .putBoolean(KEY_DNS_IDENTITY, enabled)
            .apply()
    }

    fun read(context: Context): NetworkIdentitySnapshot {
        val p = prefs(context)
        val ip = p.getString(KEY_VPN_IP, null)?.trim()?.takeIf { it.isNotEmpty() }
        val dns = p.getString(KEY_DNS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .orEmpty()
        return NetworkIdentitySnapshot(
            vpnIpAddress = ip,
            dnsServers = dns,
            dnsIdentityEnabled = p.getBoolean(KEY_DNS_IDENTITY, false),
        )
    }

    /** Clears only when [expectedOwner] still owns the session (or when null = force). */
    fun clear(context: Context, expectedOwner: String? = null) {
        val p = prefs(context)
        if (expectedOwner != null) {
            val owner = p.getString(KEY_OWNER, null)
            if (owner != null && owner != expectedOwner) return
        }
        p.edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
