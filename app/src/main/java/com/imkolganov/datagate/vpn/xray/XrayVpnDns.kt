package com.imkolganov.datagate.vpn.xray

import org.json.JSONObject

/**
 * Resolves classic VPN DNS for Xray/VLESS sessions.
 *
 * VLESS does not push system DNS (unlike OpenVPN `dhcp-option DNS`). DNS comes only from the
 * issued profile / client-link JSON (`dnsServers`), never from hostname heuristics or node /api/info.
 */
object XrayVpnDns {

    private val PUBLIC_FALLBACK = listOf("1.1.1.1", "8.8.8.8")

    /** DNS servers + Private DNS Off hint for an Xray connect handoff. */
    data class ConnectDnsPlan(
        val dnsServers: List<String>,
        val dnsIdentityEnabled: Boolean,
    )

    /** Prefer explicit IPv4 servers from issued profile; otherwise public classic resolvers. */
    fun resolve(explicitDnsServers: List<String>? = null): List<String> {
        val explicit = cleanList(explicitDnsServers)
        return explicit.ifEmpty { PUBLIC_FALLBACK }
    }

    /**
     * Same as [resolve] for [android.content.Intent] `EXTRA_DNS_SERVERS` (may be null/empty).
     * Non-IPv4 entries are dropped; empty → public fallback.
     */
    fun resolveFromIntentExtras(servers: List<String>?): List<String> =
        resolve(explicitDnsServers = servers)

    /**
     * Local Profiles connect: prefer index `dnsServers` / `dnsIdentityEnabled`, else parse [rawConfig].
     */
    fun planFromLocalProfile(
        profileDnsServers: List<String>,
        profileDnsIdentityEnabled: Boolean,
        rawConfig: String,
    ): ConnectDnsPlan {
        val servers = resolve(
            explicitDnsServers = profileDnsServers.ifEmpty {
                extractExplicitDnsServers(rawConfig)
            },
        )
        val identity = profileDnsIdentityEnabled ||
            (extractDnsIdentityEnabled(rawConfig) == true)
        return ConnectDnsPlan(dnsServers = servers, dnsIdentityEnabled = identity)
    }

    /**
     * Catalog Xray connect: DNS only from issued client-link / profile JSON body.
     * Catalog list fields (`BestServerResult.dnsServers`) must not be passed here.
     */
    fun planFromIssuedLink(linkText: String): ConnectDnsPlan {
        val servers = resolve(explicitDnsServers = extractExplicitDnsServers(linkText))
        val identity = extractDnsIdentityEnabled(linkText) == true
        return ConnectDnsPlan(dnsServers = servers, dnsIdentityEnabled = identity)
    }

    /**
     * Reads optional `dnsServers` / `DnsServers` from a JSON object (issued profile / client-link).
     * Plain-text share links have no such field.
     */
    fun extractExplicitDnsServers(raw: String): List<String> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return emptyList()
        return runCatching {
            val obj = JSONObject(trimmed)
            val arr = obj.optJSONArray("dnsServers")
                ?: obj.optJSONArray("DnsServers")
                ?: return emptyList()
            cleanList((0 until arr.length()).map { arr.optString(it) })
        }.getOrDefault(emptyList())
    }

    /**
     * `dnsIdentityEnabled` from issued profile JSON (Private DNS Off hint).
     * Null when the field is absent (plain share link / old profile).
     */
    fun extractDnsIdentityEnabled(raw: String): Boolean? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val obj = JSONObject(trimmed)
            when {
                obj.has("dnsIdentityEnabled") -> obj.optBoolean("dnsIdentityEnabled", false)
                obj.has("DnsIdentityEnabled") -> obj.optBoolean("DnsIdentityEnabled", false)
                else -> null
            }
        }.getOrNull()
    }

    /** IPv4 literals only — keeps [android.net.VpnService.Builder.addDnsServer] and `/32` routing in sync. */
    fun isIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return@all false
            n in 0..255 && part == n.toString()
        }
    }

    private fun cleanList(values: List<String>?): List<String> =
        values.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && isIpv4Literal(it) }
            .distinct()
}
