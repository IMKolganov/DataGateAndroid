package com.imkolganov.datagate.vpn

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Discovers non-VPN local prefixes for OpenVPN [tun_builder_get_local_networks]
 * (CIDR strings like `10.0.2.0/24`).
 */
object VpnLocalNetworks {
    fun collectCidrs(connectivity: ConnectivityManager?, ipv6: Boolean): List<String> {
        if (connectivity == null) return emptyList()
        val out = LinkedHashSet<String>()
        for (network in connectivity.allNetworks) {
            val caps = connectivity.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val lp = connectivity.getLinkProperties(network) ?: continue
            for (link in lp.linkAddresses) {
                val addr = link.address ?: continue
                if (ipv6) {
                    if (addr !is Inet6Address) continue
                    if (addr.isLinkLocalAddress || addr.isLoopbackAddress) continue
                } else {
                    if (addr !is Inet4Address) continue
                    if (addr.isLoopbackAddress) continue
                }
                val prefix = link.prefixLength
                if (prefix !in 0..if (ipv6) 128 else 32) continue
                networkPrefixCidr(addr, prefix)?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    /** Network address of [host]/[prefix] as `a.b.c.d/n` or IPv6 CIDR. */
    fun networkPrefixCidr(host: InetAddress, prefix: Int): String? {
        val bytes = host.address ?: return null
        val maxBits = bytes.size * 8
        if (prefix !in 0..maxBits) return null
        val masked = bytes.copyOf()
        var bit = 0
        for (i in masked.indices) {
            var b = masked[i].toInt() and 0xff
            for (bitInByte in 7 downTo 0) {
                if (bit >= prefix) {
                    b = b and (1 shl bitInByte).inv()
                }
                bit++
            }
            masked[i] = b.toByte()
        }
        val network = InetAddress.getByAddress(masked)
        return "${network.hostAddress}/$prefix"
    }
}
