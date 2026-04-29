package com.imkolganov.datagate.vpn

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import net.openvpn.ovpn3.ClientAPI_Event
import net.openvpn.ovpn3.ClientAPI_LogInfo
import net.openvpn.ovpn3.ClientAPI_OpenVPNClient
import net.openvpn.ovpn3.ClientAPI_StringVec
import net.openvpn.ovpn3.DnsOptions
import java.net.InetAddress

class OpenVpn3Client(
    private val service: VpnService,
    private val onTunChanged: (ParcelFileDescriptor?) -> Unit,
    private val onCoreEvent: (String, String) -> Unit
) : ClientAPI_OpenVPNClient() {

    companion object {
        private const val TAG = "OpenVPN3"
    }

    private var builder: VpnService.Builder? = null

    // -------- Logging / events ----------

    override fun log(info: ClientAPI_LogInfo) {
        Log.d(TAG, "core log ]: ${info.text}")
    }

    override fun event(ev: ClientAPI_Event) {
        Log.d(TAG, "core event: name=${ev.name} info=${ev.info}")

        val info = ev.info ?: ""
        onCoreEvent(ev.name, info)

        val intent = android.content.Intent(OpenVpn3Service.ACTION_STATUS)
            .setPackage(service.packageName)
            .apply {
                putExtra(OpenVpn3Service.EXTRA_EVENT_NAME, ev.name)
                putExtra(OpenVpn3Service.EXTRA_EVENT_INFO, info)
            }

        service.sendBroadcast(intent)
    }

    // -------- Socket protect ----------

    override fun socket_protect(socket: Int, remote: String, ipv6: Boolean): Boolean {
        val protected = service.protect(socket)
        Log.d(TAG, "socket_protect(socket=$socket, remote=$remote, ipv6=$ipv6) -> $protected")
        return protected
    }

    // -------- TUN builder API ----------

    override fun tun_builder_new(): Boolean {
        Log.d(TAG, "tun_builder_new()")

        builder = service.Builder().apply {
            setSession("DataGate VPN")

            // DNS
            addDnsServer("8.8.8.8")
            addDnsServer("1.1.1.1")

            // Important on many devices
            setBlocking(true)
        }

        return builder != null
    }

    override fun tun_builder_set_layer(layer: Int): Boolean {
        Log.d(TAG, "tun_builder_set_layer($layer)")
        return true
    }

    override fun tun_builder_set_remote_address(address: String, ipv6: Boolean): Boolean {
        Log.d(TAG, "tun_builder_set_remote_address($address, ipv6=$ipv6)")
        return true
    }

    override fun tun_builder_add_address(
        address: String,
        prefix_length: Int,
        gateway: String,
        ipv6: Boolean,
        net30: Boolean
    ): Boolean {
        Log.d(
            TAG,
            "tun_builder_add_address($address/$prefix_length, gw=$gateway, ipv6=$ipv6, net30=$net30)"
        )
        return try {
            builder?.addAddress(address, prefix_length)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "addAddress failed", t)
            false
        }
    }

    override fun tun_builder_set_route_metric_default(metric: Int): Boolean {
        Log.d(TAG, "tun_builder_set_route_metric_default($metric)")
        return true
    }

    override fun tun_builder_reroute_gw(ipv4: Boolean, ipv6: Boolean, flags: Long): Boolean {
        Log.d(TAG, "tun_builder_reroute_gw(ipv4=$ipv4, ipv6=$ipv6, flags=$flags)")
        return try {
            if (ipv4) {
                builder?.addRoute("0.0.0.0", 1)
                builder?.addRoute("128.0.0.0", 1)
            }
            if (ipv6) {
                builder?.addRoute("2000::", 4)
                builder?.addRoute("3000::", 4)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "reroute gateway failed", t)
            false
        }
    }

    override fun tun_builder_add_route(
        address: String,
        prefix_length: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        Log.d(TAG, "tun_builder_add_route($address/$prefix_length, metric=$metric, ipv6=$ipv6)")
        return try {
            builder?.addRoute(address, prefix_length)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "addRoute failed", t)
            false
        }
    }

    override fun tun_builder_exclude_route(
        address: String,
        prefix_length: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        Log.d(
            TAG,
            "tun_builder_exclude_route($address/$prefix_length, metric=$metric, ipv6=$ipv6)"
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder?.excludeRoute(IpPrefix(InetAddress.getByName(address), prefix_length))
            } else {
                Log.d(TAG, "excludeRoute ignored below Android 13; OpenVPN route emulation should handle it")
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "excludeRoute failed", t)
            false
        }
    }

    override fun tun_builder_set_dns_options(dns: DnsOptions): Boolean {
        return try {
            builder?.addDnsServer("8.8.8.8")
            builder?.addDnsServer("1.1.1.1")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "addDnsServer failed", t)
            false
        }
    }

    override fun tun_builder_set_mtu(mtu: Int): Boolean {
        val safeMtu = mtu.coerceIn(1200, 1500)
        Log.d(TAG, "tun_builder_set_mtu($mtu) -> $safeMtu")
        builder?.setMtu(safeMtu)
        return true
    }

    override fun tun_builder_set_session_name(name: String): Boolean {
        Log.d(TAG, "tun_builder_set_session_name($name)")
        builder?.setSession(name)
        return true
    }

    override fun tun_builder_add_proxy_bypass(bypass_host: String): Boolean {
        Log.d(TAG, "tun_builder_add_proxy_bypass($bypass_host)")
        return true
    }

    override fun tun_builder_set_proxy_auto_config_url(url: String): Boolean {
        Log.d(TAG, "tun_builder_set_proxy_auto_config_url($url)")
        return true
    }

    override fun tun_builder_set_proxy_http(host: String, port: Int): Boolean {
        Log.d(TAG, "tun_builder_set_proxy_http($host, $port)")
        return true
    }

    override fun tun_builder_set_proxy_https(host: String, port: Int): Boolean {
        Log.d(TAG, "tun_builder_set_proxy_https($host, $port)")
        return true
    }

    override fun tun_builder_add_wins_server(address: String): Boolean {
        Log.d(TAG, "tun_builder_add_wins_server($address)")
        return true
    }

    override fun tun_builder_set_allow_family(af: Int, allow: Boolean): Boolean {
        Log.d(TAG, "tun_builder_set_allow_family(af=$af, allow=$allow)")
        return true
    }

    override fun tun_builder_set_allow_local_dns(allow: Boolean): Boolean {
        Log.d(TAG, "tun_builder_set_allow_local_dns($allow)")
        return true
    }

    override fun tun_builder_establish(): Int {
        Log.d(TAG, "tun_builder_establish()")

        val b = builder ?: run {
            Log.e(TAG, "tun_builder_establish: builder is null")
            return -1
        }

        return try {
            val pfd = b.establish()
            if (pfd == null) {
                Log.e(TAG, "tun_builder_establish: establish() returned null")
                -1
            } else {
                Log.d(TAG, "tun_builder_establish: TUN established, pfd.fd=${pfd.fd}")

                // For UI/debugging only (do NOT close pfd here)
                onTunChanged(pfd)

                val fd = pfd.detachFd()
                pfd.close()

                Log.d(TAG, "tun_builder_establish: detached fd=$fd")
                fd
            }
        } catch (t: Throwable) {
            Log.e(TAG, "builder.establish() failed", t)
            -1
        }
    }

    override fun tun_builder_persist(): Boolean {
        Log.d(TAG, "tun_builder_persist()")
        return true
    }

    override fun tun_builder_get_local_networks(ipv6: Boolean): ClientAPI_StringVec {
        Log.d(TAG, "tun_builder_get_local_networks(ipv6=$ipv6)")
        return ClientAPI_StringVec()
    }

    override fun tun_builder_establish_lite() {
        Log.d(TAG, "tun_builder_establish_lite()")
    }

    override fun tun_builder_teardown(disconnect: Boolean) {
        Log.d(TAG, "tun_builder_teardown(disconnect=$disconnect)")
        onTunChanged(null)
        builder = null
    }
}
