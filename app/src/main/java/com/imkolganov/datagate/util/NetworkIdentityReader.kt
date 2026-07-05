package com.imkolganov.datagate.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import com.imkolganov.datagate.vpn.VpnTunnelSessionStore
import java.net.Inet4Address

data class NetworkIdentitySnapshot(
    /** Virtual IPv4 assigned inside the VPN tunnel (e.g. 10.51.15.x). */
    val vpnIpAddress: String? = null,
    val dnsServers: List<String> = emptyList()
)

object NetworkIdentityReader {

    fun read(context: Context): NetworkIdentitySnapshot {
        val fromSession = VpnTunnelSessionStore.read(context)
        return try {
            mergeWithSession(readUnsafe(context), fromSession)
        } catch (t: Throwable) {
            if (isConnectivitySystemFailure(t)) fromSession
            else throw t
        }
    }

    internal fun mergeWithSession(
        fromSystem: NetworkIdentitySnapshot,
        fromSession: NetworkIdentitySnapshot
    ): NetworkIdentitySnapshot = NetworkIdentitySnapshot(
        vpnIpAddress = fromSystem.vpnIpAddress ?: fromSession.vpnIpAddress,
        dnsServers = fromSystem.dnsServers.ifEmpty { fromSession.dnsServers }
    )

    private fun readUnsafe(context: Context): NetworkIdentitySnapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkIdentitySnapshot()

        val vpnNetwork = findVpnNetwork(cm) ?: return NetworkIdentitySnapshot()
        val props = cm.getLinkProperties(vpnNetwork)
        return NetworkIdentitySnapshot(
            vpnIpAddress = props?.linkAddresses?.firstIpv4Host(),
            dnsServers = props?.dnsServers.orEmpty().mapNotNull { formatHost(it.hostAddress) }
        )
    }

    private fun findVpnNetwork(cm: ConnectivityManager): android.net.Network? {
        var fallback: android.net.Network? = null
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            fallback = network
            val props = cm.getLinkProperties(network) ?: continue
            val hasTunnelIpv4 = props.linkAddresses.any { linkAddress ->
                val host = linkAddress.address
                host is Inet4Address && !host.isLoopbackAddress
            }
            if (hasTunnelIpv4) {
                return network
            }
        }
        return fallback
    }

    private fun List<LinkAddress>.firstIpv4Host(): String? {
        for (linkAddress in this) {
            val host = linkAddress.address
            if (host is Inet4Address && !host.isLoopbackAddress) {
                return formatHost(host.hostAddress)
            }
        }
        return null
    }

    internal fun formatHostAddress(host: String?): String? = formatHost(host)

    private fun formatHost(host: String?): String? =
        host?.trim()?.takeIf { it.isNotEmpty() }
}

/** True when ConnectivityManager is temporarily unavailable (VPN churn, system restart). */
internal fun isConnectivitySystemFailure(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        when (cur) {
            is SecurityException,
            is IllegalStateException -> return true
        }
        when (cur.javaClass.name) {
            "android.os.DeadSystemException",
            "android.os.DeadSystemRuntimeException" -> return true
        }
        cur = cur.cause
    }
    return false
}

internal fun pickFirstIpv4HostAddress(addresses: Iterable<String>): String? {
    for (raw in addresses) {
        val host = NetworkIdentityReader.formatHostAddress(raw) ?: continue
        if (isLikelyIpv4Host(host)) return host
    }
    return null
}

internal fun isLikelyIpv4Host(host: String): Boolean {
    if (host.isEmpty() || host.contains(':')) return false
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.toIntOrNull()?.let { it in 0..255 } == true
    }
}
