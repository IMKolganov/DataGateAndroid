package com.imkolganov.datagate.vpn.xray

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a full Xray client JSON (TUN inbound + proxy outbounds + routing)
 * suitable for [libXray.LibXray] `runXrayFromJson`.
 */
object XrayConfigBuilder {

    /**
     * Private / local CIDRs that `geoip:private` would match — listed explicitly so the
     * client does not need `geoip.dat` on device (libXray otherwise looks under
     * `/system/bin/geoip.dat` and fails config parse).
     */
    private val PRIVATE_DIRECT_IPS: List<String> = listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.0.0/24",
        "192.0.2.0/24",
        "192.168.0.0/16",
        "198.18.0.0/15",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "224.0.0.0/4",
        "240.0.0.0/4",
        "255.255.255.255/32",
        "::/128",
        "::1/128",
        "fc00::/7",
        "fe80::/10",
        "ff00::/8",
    )

    /**
     * Max CIDRs per Xray routing rule. Large IP lists are split into several `field` rules
     * so a single JSON array does not grow unbounded (Android 12- bypass via routing).
     */
    const val DIRECT_BYPASS_CIDRS_PER_RULE = 250

    /**
     * @param outboundsJson JSON array of outbound objects, or a full config object containing `outbounds`.
     * @param tunFd Android TUN file descriptor from [android.net.VpnService.Builder.establish].
     * @param directBypassCidrs Extra CIDRs routed to `direct` (freedom + VpnService.protect).
     *   Used on Android 12 and below where [android.net.VpnService.Builder.excludeRoute] is unavailable.
     */
    fun buildTunClientConfig(
        outboundsJson: String,
        tunFd: Int,
        dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
        mtu: Int = 1500,
        directBypassCidrs: List<String> = emptyList(),
    ): String {
        val outbounds = extractOutbounds(outboundsJson)
        require(outbounds.length() > 0) { "No Xray outbounds in config" }
        // libXray convertShareLinks stores the fragment/display name in sendThrough.
        // Xray-core treats sendThrough as a bind address → "unable to send through: <name>".
        // DataGateLinux never sets this field; strip before runXrayFromJson.
        sanitizeOutboundsForRuntime(outbounds)

        // Ensure first proxy outbound has a stable tag for routing.
        val first = outbounds.getJSONObject(0)
        if (!first.has("tag") || first.optString("tag").isBlank()) {
            first.put("tag", "proxy")
        }
        val proxyTag = first.getString("tag")

        // Append direct/block if missing.
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).optString("tag") }.toSet()
        if ("direct" !in tags) {
            outbounds.put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom")
                    .put("settings", JSONObject())
            )
        }
        if ("block" !in tags) {
            outbounds.put(
                JSONObject()
                    .put("tag", "block")
                    .put("protocol", "blackhole")
                    .put("settings", JSONObject())
            )
        }

        val dnsJson = JSONObject().put(
            "servers",
            JSONArray().also { arr -> dnsServers.forEach { arr.put(it) } },
        )

        val tunInbound = JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put(
                "settings",
                JSONObject()
                    .put("mtu", mtu)
                    .put("name", "xray0")
                    .put("stack", "system"),
            )
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
            )

        val privateIps = JSONArray().also { arr -> PRIVATE_DIRECT_IPS.forEach { arr.put(it) } }
        val rules = JSONArray()
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "direct")
                    .put("ip", privateIps),
            )
        // IP-list bypass must sit before the catch-all proxy rule.
        appendDirectBypassRules(rules, directBypassCidrs)
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("outboundTag", proxyTag)
                .put("network", "tcp,udp"),
        )
        val routing = JSONObject()
            .put("domainStrategy", "AsIs")
            .put("rules", rules)

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", dnsJson)
            .put("inbounds", JSONArray().put(tunInbound))
            .put("outbounds", outbounds)
            .put("routing", routing)
            .put(
                "env",
                JSONObject().put("xray.tun.fd", tunFd.toString()),
            )
            .toString()
    }

    fun extractOutbounds(raw: String): JSONArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        val obj = JSONObject(trimmed)
        val outbounds = obj.optJSONArray("outbounds")
            ?: obj.optJSONArray("OutboundConfigs")
            ?: error("Config has no outbounds")
        return outbounds
    }

    /**
     * Removes libXray share-link metadata that is invalid for a live Xray instance.
     * See native-libxray README: "libXray uses sendThrough to store outbound names."
     */
    fun sanitizeOutboundsForRuntime(outbounds: JSONArray) {
        for (i in 0 until outbounds.length()) {
            outbounds.getJSONObject(i).remove("sendThrough")
        }
    }

    /** First `vless://` / `vmess://` / `trojan://` / `ss://` line from exported link text. */
    fun extractShareLink(text: String): String? {
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val lower = line.lowercase()
            if (
                lower.startsWith("vless://") ||
                lower.startsWith("vmess://") ||
                lower.startsWith("trojan://") ||
                lower.startsWith("ss://") ||
                lower.startsWith("hy2://") ||
                lower.startsWith("hysteria2://")
            ) {
                return line
            }
        }
        return null
    }

    private fun appendDirectBypassRules(rules: JSONArray, cidrs: List<String>) {
        val cleaned = cidrs.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return
        cleaned.chunked(DIRECT_BYPASS_CIDRS_PER_RULE).forEach { chunk ->
            val ips = JSONArray().also { arr -> chunk.forEach { arr.put(it) } }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "direct")
                    .put("ip", ips),
            )
        }
    }
}
