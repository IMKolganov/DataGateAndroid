package com.imkolganov.datagate.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

data class NetworkIdentitySnapshot(
    /** Virtual IPv4 assigned inside the VPN tunnel (e.g. 10.51.15.x). */
    val vpnIpAddress: String? = null,
    /** Public egress IPv4 as seen on the internet (via VPN when connected). */
    val externalIpAddress: String? = null,
    val dnsServers: List<String> = emptyList()
)

object NetworkIdentityReader {

    fun read(context: Context): NetworkIdentitySnapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkIdentitySnapshot()

        findVpnNetwork(cm)?.let { vpnNetwork ->
            val props = cm.getLinkProperties(vpnNetwork)
            return NetworkIdentitySnapshot(
                vpnIpAddress = props?.linkAddresses?.firstIpv4Host(),
                externalIpAddress = fetchPublicIpv4(vpnNetwork),
                dnsServers = props?.dnsServers.orEmpty().mapNotNull { formatHost(it.hostAddress) }
            )
        }

        val defaultNetwork = cm.activeNetwork
        val defaultProps = defaultNetwork?.let { cm.getLinkProperties(it) }
        return NetworkIdentitySnapshot(
            vpnIpAddress = null,
            externalIpAddress = defaultNetwork?.let { fetchPublicIpv4(it) } ?: fetchPublicIpv4(),
            dnsServers = defaultProps?.dnsServers.orEmpty().mapNotNull { formatHost(it.hostAddress) }
        )
    }

    private fun findVpnNetwork(cm: ConnectivityManager): Network? {
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        return null
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

    private fun fetchPublicIpv4(network: Network? = null): String? {
        return runCatching {
            val url = URL("https://api.ipify.org")
            @Suppress("DEPRECATION")
            val connection = (
                if (network != null) network.openConnection(url)
                else url.openConnection()
                ) as HttpURLConnection
            connection.apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
            }
            connection.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrNull()?.takeIf { IPV4_REGEX.matches(it) }
    }

    private fun formatHost(host: String?): String? =
        host?.trim()?.takeIf { it.isNotEmpty() }

    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}
