package com.imkolganov.datagate.vpn

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.imkolganov.datagate.logger.VpnDebugLogger
import net.openvpn.ovpn3.ClientAPI_Event
import net.openvpn.ovpn3.ClientAPI_LogInfo
import net.openvpn.ovpn3.ClientAPI_OpenVPNClient
import net.openvpn.ovpn3.ClientAPI_StringVec
import net.openvpn.ovpn3.DnsOptions
import net.openvpn.ovpn3.DnsServer
import java.net.InetAddress

class OpenVpn3Client(
    private val service: VpnService,
    private val excludedRoutes: List<IpCidrRoute>,
    private val onTunChanged: (ParcelFileDescriptor?) -> Unit,
    private val onCoreEvent: (String, String) -> Unit
) : ClientAPI_OpenVPNClient() {

    companion object {
        private const val TAG = "OpenVPN3"
    }

    private var builder: VpnService.Builder? = null

    // -------- Logging / events ----------

    override fun log(info: ClientAPI_LogInfo) {
        val text = info.text ?: ""
        // Always logcat; file only W/E-ish lines (core.log storms fill the 8MB debug file).
        android.util.Log.d(TAG, "core log: $text")
        if (OpenVpnCoreLogFilter.shouldPersistToDebugFile(text)) {
            VpnDebugLogger.w(TAG, "core: $text")
        }
    }

    override fun event(ev: ClientAPI_Event) {
        VpnDebugLogger.event(
            category = "core.event",
            action = ev.name ?: "unknown",
            details = mapOf("info" to (ev.info ?: "")),
        )
        onCoreEvent(ev.name, ev.info ?: "")
    }

    // -------- Socket protect ----------

    override fun socket_protect(socket: Int, remote: String, ipv6: Boolean): Boolean {
        val protected = service.protect(socket)
        VpnDebugLogger.d(TAG, "socket_protect(socket=$socket, remote=$remote, ipv6=$ipv6) -> $protected")
        return protected
    }

    // -------- TUN builder API ----------

    override fun tun_builder_new(): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_new()")

        builder = service.Builder().apply {
            setSession("DataGate VPN")
            setBlocking(true)
        }

        return builder != null
    }

    override fun tun_builder_set_layer(layer: Int): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_layer($layer)")
        return true
    }

    override fun tun_builder_set_remote_address(address: String, ipv6: Boolean): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_remote_address($address, ipv6=$ipv6)")
        return true
    }

    override fun tun_builder_add_address(
        address: String,
        prefix_length: Int,
        gateway: String,
        ipv6: Boolean,
        net30: Boolean
    ): Boolean {
        VpnDebugLogger.d(
            TAG,
            "tun_builder_add_address($address/$prefix_length, gw=$gateway, ipv6=$ipv6, net30=$net30)"
        )
        return try {
            if (!ipv6) {
                VpnTunnelSessionStore.recordVpnIp(service.applicationContext, address)
            }
            builder?.addAddress(address, prefix_length)
            true
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "addAddress failed", t)
            false
        }
    }

    override fun tun_builder_set_route_metric_default(metric: Int): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_route_metric_default($metric)")
        return true
    }

    override fun tun_builder_reroute_gw(ipv4: Boolean, ipv6: Boolean, flags: Long): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_reroute_gw(ipv4=$ipv4, ipv6=$ipv6, flags=$flags)")
        return try {
            if (ipv4) {
                builder?.addRoute("0.0.0.0", 1)
                builder?.addRoute("128.0.0.0", 1)
            }
            if (ipv6) {
                builder?.addRoute("::", 1)
                builder?.addRoute("8000::", 1)
            }
            true
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "reroute gateway failed", t)
            false
        }
    }

    override fun tun_builder_add_route(
        address: String,
        prefix_length: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_add_route($address/$prefix_length, metric=$metric, ipv6=$ipv6)")
        return try {
            builder?.addRoute(address, prefix_length)
            true
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "addRoute failed", t)
            false
        }
    }

    override fun tun_builder_exclude_route(
        address: String,
        prefix_length: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        VpnDebugLogger.d(
            TAG,
            "tun_builder_exclude_route($address/$prefix_length, metric=$metric, ipv6=$ipv6)"
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder?.excludeRoute(IpPrefix(InetAddress.getByName(address), prefix_length))
            } else {
                VpnDebugLogger.d(TAG, "excludeRoute ignored below Android 13; OpenVPN route emulation should handle it")
            }
            true
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "excludeRoute failed", t)
            false
        }
    }

    override fun tun_builder_set_dns_options(dns: DnsOptions): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_dns_options()")
        val b = builder ?: run {
            VpnDebugLogger.e(TAG, "tun_builder_set_dns_options: builder is null")
            return false
        }
        return try {
            applyDnsOptions(b, dns)
            true
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "tun_builder_set_dns_options failed", t)
            false
        }
    }

    private fun applyDnsOptions(b: VpnService.Builder, dns: DnsOptions) {
        val appliedServers = mutableListOf<String>()

        val servers = dns.servers
        if (servers != null && !servers.isEmpty()) {
            for ((_, server) in servers) {
                val transport = server.transport
                if (transport == DnsServer.Transport.HTTPS || transport == DnsServer.Transport.TLS) {
                    VpnDebugLogger.w(TAG, "Skipping DoH/DoT DNS server (transport=$transport): ${server.to_string()}")
                    continue
                }
                val addresses = server.addresses ?: continue
                for (i in 0 until addresses.size) {
                    val addr = addresses[i].address?.trim()
                    if (!addr.isNullOrEmpty()) {
                        b.addDnsServer(addr)
                        appliedServers.add(addr)
                    }
                }
            }
        }

        val searchDomains = dns.search_domains
        if (searchDomains != null && !searchDomains.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (i in 0 until searchDomains.size) {
                    val domain = searchDomains[i].domain?.trim()
                    if (!domain.isNullOrEmpty()) {
                        b.addSearchDomain(domain)
                        VpnDebugLogger.d(TAG, "DNS search domain: $domain")
                    }
                }
            } else {
                VpnDebugLogger.d(TAG, "Search domains ignored below Android 10")
            }
        }

        if (appliedServers.isEmpty()) {
            VpnDebugLogger.w(TAG, "No DNS servers from push; falling back to 8.8.8.8 and 1.1.1.1")
            b.addDnsServer("8.8.8.8")
            b.addDnsServer("1.1.1.1")
            appliedServers.addAll(listOf("8.8.8.8", "1.1.1.1"))
        }

        VpnDebugLogger.d(TAG, "Applied DNS servers: ${appliedServers.joinToString(", ")}")
        VpnTunnelSessionStore.recordDnsServers(service.applicationContext, appliedServers)
    }

    override fun tun_builder_set_mtu(mtu: Int): Boolean {
        val safeMtu = mtu.coerceIn(1200, 1500)
        VpnDebugLogger.d(TAG, "tun_builder_set_mtu($mtu) -> $safeMtu")
        builder?.setMtu(safeMtu)
        return true
    }

    override fun tun_builder_set_session_name(name: String): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_session_name($name)")
        builder?.setSession(name)
        return true
    }

    override fun tun_builder_add_proxy_bypass(bypass_host: String): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_add_proxy_bypass($bypass_host)")
        return true
    }

    override fun tun_builder_set_proxy_auto_config_url(url: String): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_proxy_auto_config_url($url)")
        return true
    }

    override fun tun_builder_set_proxy_http(host: String, port: Int): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_proxy_http($host, $port)")
        return true
    }

    override fun tun_builder_set_proxy_https(host: String, port: Int): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_proxy_https($host, $port)")
        return true
    }

    override fun tun_builder_add_wins_server(address: String): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_add_wins_server($address)")
        return true
    }

    override fun tun_builder_set_allow_family(af: Int, allow: Boolean): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_allow_family(af=$af, allow=$allow)")
        return true
    }

    override fun tun_builder_set_allow_local_dns(allow: Boolean): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_set_allow_local_dns($allow)")
        return true
    }

    override fun tun_builder_establish(): Int {
        VpnDebugLogger.d(TAG, "tun_builder_establish()")

        val b = builder ?: run {
            VpnDebugLogger.e(TAG, "tun_builder_establish: builder is null")
            return -1
        }

        return try {
            val appliedExcludes = applyExcludedRoutes(b)
            val dropped = (excludedRoutes.size - appliedExcludes).coerceAtLeast(0)
            VpnDebugLogger.i(
                TAG,
                "excludeRoute establish: requested=${excludedRoutes.size} applied=$appliedExcludes dropped=$dropped"
            )

            val pfd = b.establish()

            if (pfd == null) {
                VpnDebugLogger.e(TAG, "tun_builder_establish: establish() returned null")
                -1
            } else {
                VpnDebugLogger.d(TAG, "tun_builder_establish: TUN established, pfd.fd=${pfd.fd}")

                // For UI/debugging only (do NOT close pfd here)
                onTunChanged(pfd)

                val fd = pfd.detachFd()
                pfd.close()

                VpnDebugLogger.d(TAG, "tun_builder_establish: detached fd=$fd")
                fd
            }
        } catch (t: Throwable) {
            VpnDebugLogger.e(TAG, "builder.establish() failed", t)
            -1
        }
    }

    /** @return number of routes actually passed to [VpnService.Builder.excludeRoute]. */
    private fun applyExcludedRoutes(b: VpnService.Builder): Int {
        if (excludedRoutes.isEmpty()) return 0

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            VpnDebugLogger.w(TAG, "Skipping ${excludedRoutes.size} excluded routes: excludeRoute requires Android 13+")
            return 0
        }

        var applied = 0
        for (route in excludedRoutes) {
            try {
                b.excludeRoute(
                    IpPrefix(
                        InetAddress.getByName(route.networkAddress),
                        route.prefixLength
                    )
                )
                applied++
            } catch (t: Throwable) {
                VpnDebugLogger.w(TAG, "excludeRoute failed for ${route.toCidrString()}", t)
            }
        }
        VpnDebugLogger.d(TAG, "Applied excluded routes: $applied/${excludedRoutes.size}")
        return applied
    }

    override fun tun_builder_persist(): Boolean {
        VpnDebugLogger.d(TAG, "tun_builder_persist() -> false")
        return false
    }

    override fun tun_builder_get_local_networks(ipv6: Boolean): ClientAPI_StringVec {
        VpnDebugLogger.d(TAG, "tun_builder_get_local_networks(ipv6=$ipv6)")
        return ClientAPI_StringVec()
    }

    override fun tun_builder_establish_lite() {
        VpnDebugLogger.d(TAG, "tun_builder_establish_lite()")
    }

    override fun tun_builder_teardown(disconnect: Boolean) {
        VpnDebugLogger.d(TAG, "tun_builder_teardown(disconnect=$disconnect)")
        if (disconnect) {
            VpnTunnelSessionStore.clear(service.applicationContext)
        }
        onTunChanged(null)
        builder = null
    }
}
