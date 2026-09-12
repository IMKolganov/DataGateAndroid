package com.imkolganov.datagate.vpn.traffic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import com.imkolganov.datagate.logger.VpnDebugLogger
import java.net.Inet4Address

/**
 * Reads Android TUN iface counters the same way DataGateMac reads utun
 * (`ifi_ibytes` / `ifi_obytes`): RX = download, TX = upload.
 */
object VpnTunIfaceCounters {
    private const val TAG = "VpnTraffic"

    @Volatile
    private var warned = false

    fun read(context: Context): TrafficCounters? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val iface = findVpnInterfaceName(cm)
        if (iface.isNullOrBlank()) {
            warnOnce("VPN interface name not found")
            return null
        }
        val rx = TrafficStats.getRxBytes(iface)
        val tx = TrafficStats.getTxBytes(iface)
        val mapped = mapTunIfaceToUserCounters(rx, tx)
        if (mapped == null) {
            warnOnce("TrafficStats UNSUPPORTED for iface=$iface")
        }
        return mapped
    }

    fun mapTunIfaceToUserCounters(rxBytes: Long, txBytes: Long): TrafficCounters? {
        if (rxBytes < 0L || txBytes < 0L) return null
        return TrafficCounters(bytesIn = rxBytes, bytesOut = txBytes)
    }

    internal fun findVpnInterfaceName(cm: ConnectivityManager): String? {
        val candidates = ArrayList<TunIfaceCandidate>()
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val props = cm.getLinkProperties(network)
            val hasTunnelIpv4 = props?.linkAddresses.orEmpty().any { link ->
                val host = link.address
                host is Inet4Address && !host.isLoopbackAddress
            }
            candidates.add(
                TunIfaceCandidate(
                    isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    interfaceName = props?.interfaceName,
                    hasTunnelIpv4 = hasTunnelIpv4,
                )
            )
        }
        return VpnTunIfacePick.pickName(candidates)
    }

    private fun warnOnce(message: String) {
        if (warned) return
        warned = true
        VpnDebugLogger.w(TAG, message)
    }
}
