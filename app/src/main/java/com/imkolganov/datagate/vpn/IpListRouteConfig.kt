package com.imkolganov.datagate.vpn

data class Ipv4CidrRoute(
    val network: String,
    val netmask: String,
    val prefixLength: Int
) {
    fun toOpenVpnNetGatewayRoute(): String = "route $network $netmask net_gateway"
}

object IpListRouteConfig {
    private const val MAX_ROUTES = 12_000

    fun appendBypassRoutes(config: String, routes: List<Ipv4CidrRoute>): String {
        if (routes.isEmpty()) return config

        val out = StringBuilder(config.trimEnd())
        out.append("\n\n")
        out.append("# DataGate IP list bypass routes\n")
        for (route in routes) {
            out.append(route.toOpenVpnNetGatewayRoute()).append('\n')
        }
        return out.toString()
    }

    fun parseIpv4CidrRoutes(content: String): List<Ipv4CidrRoute> {
        val routes = LinkedHashSet<Ipv4CidrRoute>()

        for (rawLine in content.lineSequence()) {
            val token = rawLine
                .substringBefore('#')
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: continue

            val route = parseIpv4Cidr(token) ?: continue
            if (route.prefixLength == 0) continue
            routes.add(route)
            if (routes.size >= MAX_ROUTES) break
        }

        return routes.toList()
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
            network = longToIpv4(network),
            netmask = longToIpv4(mask),
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
}
