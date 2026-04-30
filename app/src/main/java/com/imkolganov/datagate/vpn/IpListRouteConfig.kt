package com.imkolganov.datagate.vpn

import java.net.Inet6Address
import java.net.InetAddress

sealed interface IpCidrRoute {
    val prefixLength: Int
    val networkAddress: String

    fun toOpenVpnNetGatewayRoute(): String
    fun toCidrString(): String = "$networkAddress/$prefixLength"
}

data class Ipv4CidrRoute(
    override val networkAddress: String,
    val netmask: String,
    override val prefixLength: Int
) : IpCidrRoute {
    override fun toOpenVpnNetGatewayRoute(): String = "route $networkAddress $netmask net_gateway"
}

data class Ipv6CidrRoute(
    override val networkAddress: String,
    override val prefixLength: Int
) : IpCidrRoute {
    override fun toOpenVpnNetGatewayRoute(): String = "route-ipv6 $networkAddress/$prefixLength net_gateway"
}

data class IpListParseResult(
    val routes: List<IpCidrRoute>,
    val reachedRouteLimit: Boolean
)

data class IpListAppendResult(
    val config: String,
    val appliedRouteCount: Int,
    val reachedProfileSizeLimit: Boolean
)

object IpListRouteConfig {
    const val MAX_ROUTES = 12_000
    const val MAX_ANDROID_EXCLUDED_ROUTES = 3_000
    private const val MAX_OPENVPN_PROFILE_BYTES = 180_000

    fun selectAndroidExcludedRoutes(routes: List<IpCidrRoute>): List<IpCidrRoute> {
        if (routes.size <= MAX_ANDROID_EXCLUDED_ROUTES) return routes
        return routes
            .sortedWith(
                compareBy<IpCidrRoute> { it is Ipv6CidrRoute }
                    .thenBy { it.prefixLength }
                    .thenBy { it.networkAddress }
            )
            .take(MAX_ANDROID_EXCLUDED_ROUTES)
    }

    fun appendBypassRoutes(config: String, routes: List<IpCidrRoute>): String {
        return appendBypassRoutesResult(config, routes).config
    }

    fun appendBypassRoutesResult(config: String, routes: List<IpCidrRoute>): IpListAppendResult {
        if (routes.isEmpty()) {
            return IpListAppendResult(
                config = config,
                appliedRouteCount = 0,
                reachedProfileSizeLimit = false
            )
        }

        val out = StringBuilder(config.trimEnd())
        val header = "\n\n# DataGate IP list bypass routes\n"
        out.append(header)

        var projectedBytes = out.toString().toByteArray(Charsets.UTF_8).size
        var appliedRouteCount = 0
        var reachedProfileSizeLimit = false

        for (route in routes) {
            val line = route.toOpenVpnNetGatewayRoute() + '\n'
            val lineBytes = line.toByteArray(Charsets.UTF_8).size
            if (projectedBytes + lineBytes > MAX_OPENVPN_PROFILE_BYTES) {
                reachedProfileSizeLimit = true
                break
            }
            out.append(line)
            projectedBytes += lineBytes
            appliedRouteCount++
        }

        return IpListAppendResult(
            config = out.toString(),
            appliedRouteCount = appliedRouteCount,
            reachedProfileSizeLimit = reachedProfileSizeLimit
        )
    }

    fun parseIpv4CidrRoutes(content: String): List<Ipv4CidrRoute> =
        parseCidrRoutesResult(content).routes.filterIsInstance<Ipv4CidrRoute>()

    fun parseCidrRoutesResult(content: String): IpListParseResult {
        val routes = LinkedHashSet<IpCidrRoute>()
        var reachedRouteLimit = false

        for (rawLine in content.lineSequence()) {
            val token = rawLine
                .substringBefore('#')
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: continue

            val route = parseIpCidr(token) ?: continue
            if (route.prefixLength == 0) continue
            routes.add(route)
            if (routes.size >= MAX_ROUTES) {
                reachedRouteLimit = true
                break
            }
        }

        return IpListParseResult(
            routes = routes.toList(),
            reachedRouteLimit = reachedRouteLimit
        )
    }

    private fun parseIpCidr(value: String): IpCidrRoute? =
        if (value.contains(':')) {
            parseIpv6Cidr(value)
        } else {
            parseIpv4Cidr(value)
        }

    private fun parseIpv4Cidr(value: String): Ipv4CidrRoute? {
        val parts = value.split('/', limit = 2)
        val ip = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val prefixLength = parts.getOrNull(1)?.toIntOrNull() ?: 32
        if (prefixLength !in 0..32) return null

        val ipLong = parseIpv4ToLong(ip) ?: return null
        val mask = prefixToMask(prefixLength)
        val network = ipLong and mask

        return Ipv4CidrRoute(
            networkAddress = longToIpv4(network),
            netmask = longToIpv4(mask),
            prefixLength = prefixLength
        )
    }

    private fun parseIpv6Cidr(value: String): Ipv6CidrRoute? {
        val parts = value.split('/', limit = 2)
        val ip = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val prefixLength = parts.getOrNull(1)?.toIntOrNull() ?: 128
        if (prefixLength !in 0..128) return null

        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() as? Inet6Address
            ?: return null
        val network = address.address.copyOf()
        applyIpv6Prefix(network, prefixLength)

        return Ipv6CidrRoute(
            networkAddress = InetAddress.getByAddress(network).hostAddress ?: return null,
            prefixLength = prefixLength
        )
    }

    private fun parseIpv4ToLong(value: String): Long? {
        val octets = value.split('.')
        if (octets.size != 4) return null

        var result = 0L
        for (octet in octets) {
            val n = octet.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            result = (result shl 8) or n.toLong()
        }
        return result and 0xffffffffL
    }

    private fun prefixToMask(prefixLength: Int): Long {
        if (prefixLength == 0) return 0L
        return (0xffffffffL shl (32 - prefixLength)) and 0xffffffffL
    }

    private fun longToIpv4(value: Long): String =
        listOf(
            (value shr 24) and 0xff,
            (value shr 16) and 0xff,
            (value shr 8) and 0xff,
            value and 0xff
        ).joinToString(".")

    private fun applyIpv6Prefix(bytes: ByteArray, prefixLength: Int) {
        var remaining = prefixLength
        for (i in bytes.indices) {
            when {
                remaining >= 8 -> remaining -= 8
                remaining <= 0 -> bytes[i] = 0
                else -> {
                    val mask = (0xff shl (8 - remaining)) and 0xff
                    bytes[i] = (bytes[i].toInt() and mask).toByte()
                    remaining = 0
                }
            }
        }
    }
}
