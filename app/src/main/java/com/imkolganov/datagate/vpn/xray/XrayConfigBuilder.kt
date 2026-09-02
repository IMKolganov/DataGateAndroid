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

    /** Default Mux TCP sub-connection concurrency when profile `mux` omits / invalidates the field. */
    const val DEFAULT_MUX_CONCURRENCY = 8

    /** Default XUDP concurrency when profile `mux` omits / invalidates the field. */
    const val DEFAULT_MUX_XUDP_CONCURRENCY = 16

    private const val MUX_CONCURRENCY_MIN = 1
    private const val MUX_CONCURRENCY_MAX = 128
    private const val MUX_XUDP_CONCURRENCY_MIN = 1
    private const val MUX_XUDP_CONCURRENCY_MAX = 1024

    /**
     * @param outboundsJson JSON array of outbound objects, or a full config / issued-profile object
     *   containing `outbounds` (and optionally top-level `mux`, same shape as `dnsServers`).
     * @param tunFd Android TUN file descriptor from [android.net.VpnService.Builder.establish].
     * @param directBypassCidrs Extra CIDRs routed to `direct` (freedom + VpnService.protect).
     *   Used on Android 12 and below where [android.net.VpnService.Builder.excludeRoute] is unavailable.
     * @param tunnelDnsServers Classic DNS IPs pushed via [android.net.VpnService.Builder.addDnsServer].
     *   Forced through [proxyTag] before the private/direct rule — otherwise addresses inside
     *   `172.16.0.0/12` would match private → direct and bypass the VLESS tunnel.
     *
     * No Xray built-in DNS / FakeDNS / DoH / DoT block: OS resolves via classic UDP/TCP :53 on TUN.
     *
     * When [outboundsJson] contains a top-level `mux` object, it is applied only to the first
     * (proxy) outbound. Absent `mux` leaves outbounds unchanged (no Mux). See [normalizeMux].
     */
    fun buildTunClientConfig(
        outboundsJson: String,
        tunFd: Int,
        mtu: Int = 1500,
        directBypassCidrs: List<String> = emptyList(),
        tunnelDnsServers: List<String> = emptyList(),
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
        normalizeMux(outboundsJson)?.let { first.put("mux", it) }

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
                    // http/tls/quic only — no fakedns / dns override.
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
            )

        val rules = JSONArray()
        // VPN DNS destinations must hit the proxy before private CIDRs → direct.
        appendProxyDnsRules(rules, proxyTag, tunnelDnsServers)
        val privateIps = JSONArray().also { arr -> PRIVATE_DIRECT_IPS.forEach { arr.put(it) } }
        rules.put(
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
     *
     * Only strips `sendThrough` — does not touch `streamSettings` (including `network=xhttp`
     * / `xhttpSettings` produced by libXray share-link convert).
     */
    fun sanitizeOutboundsForRuntime(outbounds: JSONArray) {
        for (i in 0 until outbounds.length()) {
            outbounds.getJSONObject(i).remove("sendThrough")
        }
    }

    /**
     * Builds `{ "outbounds": [...] }` and copies a validated top-level `mux` from [sourceRaw]
     * when present (issued profile / already-normalized config). Used by import + connect
     * normalize so Mux survives share-link conversion the same way `dnsServers` is read
     * from the original profile body.
     */
    fun wrapOutboundsPreservingProfileExtras(outbounds: JSONArray, sourceRaw: String): String {
        val result = JSONObject().put("outbounds", outbounds)
        normalizeMux(sourceRaw)?.let { result.put("mux", it) }
        return result.toString()
    }

    /**
     * Reads optional top-level `"mux"` from issued / normalized profile JSON.
     *
     * Returns `null` when the block is absent or `enabled` is explicitly `false` — callers
     * must not inject Mux (existing profiles stay unchanged).
     *
     * When present, returns a cleaned MuxObject with `enabled=true` and:
     * - `concurrency` in 1..128 (default [DEFAULT_MUX_CONCURRENCY])
     * - `xudpConcurrency` in 1..1024 (default [DEFAULT_MUX_XUDP_CONCURRENCY])
     * - `xudpProxyUDP443`: `reject` | `allow` | `skip` (default `reject`)
     *
     * Under a full TUN tunnel, `xudpProxyUDP443` decides how QUIC (UDP/443) is handled:
     * - `reject` — Mux drops UDP/443 (browsers usually fall back to TCP/HTTP2); fewer
     *   QUIC flows through the tunnel
     * - `allow` — UDP/443 is carried inside Mux
     * - `skip` — UDP/443 bypasses Mux (protocol-native UDP / UoT)
     *
     * Invalid numeric / enum values are ignored and replaced by the defaults above.
     */
    fun normalizeMux(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(trimmed)
            if (!root.has("mux") || root.isNull("mux")) return null
            val mux = root.optJSONObject("mux") ?: return null
            if (mux.has("enabled") && !mux.optBoolean("enabled", true)) return null
            JSONObject()
                .put("enabled", true)
                .put("concurrency", readMuxConcurrency(mux))
                .put("xudpConcurrency", readMuxXudpConcurrency(mux))
                .put("xudpProxyUDP443", readMuxXudpProxyUdp443(mux))
        }.getOrNull()
    }

    private fun readMuxConcurrency(mux: JSONObject): Int {
        if (!mux.has("concurrency")) return DEFAULT_MUX_CONCURRENCY
        val n = mux.optInt("concurrency", Int.MIN_VALUE)
        // optInt returns 0 for non-numeric; treat missing-range / garbage as default.
        if (n !in MUX_CONCURRENCY_MIN..MUX_CONCURRENCY_MAX) return DEFAULT_MUX_CONCURRENCY
        // Reject string garbage that JSONObject coerced oddly: require the raw value to be numeric.
        if (!isJsonNumber(mux.opt("concurrency"))) return DEFAULT_MUX_CONCURRENCY
        return n
    }

    private fun readMuxXudpConcurrency(mux: JSONObject): Int {
        if (!mux.has("xudpConcurrency")) return DEFAULT_MUX_XUDP_CONCURRENCY
        val n = mux.optInt("xudpConcurrency", Int.MIN_VALUE)
        if (n !in MUX_XUDP_CONCURRENCY_MIN..MUX_XUDP_CONCURRENCY_MAX) return DEFAULT_MUX_XUDP_CONCURRENCY
        if (!isJsonNumber(mux.opt("xudpConcurrency"))) return DEFAULT_MUX_XUDP_CONCURRENCY
        return n
    }

    private fun readMuxXudpProxyUdp443(mux: JSONObject): String {
        if (!mux.has("xudpProxyUDP443")) return "reject"
        val raw = mux.optString("xudpProxyUDP443", "").trim().lowercase()
        return when (raw) {
            "reject", "allow", "skip" -> raw
            else -> "reject"
        }
    }

    private fun isJsonNumber(value: Any?): Boolean =
        value is Number || (value is String && value.trim().toIntOrNull() != null)

    /** First `vless://` / `vmess://` / … line, or JSON object field `"vless"`. */
    fun extractShareLink(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            runCatching {
                val vless = JSONObject(trimmed).optString("vless").trim()
                if (vless.startsWith("vless://", ignoreCase = true)) return vless
            }
        }
        for (raw in trimmed.replace("\r\n", "\n").split('\n')) {
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

    private fun appendProxyDnsRules(rules: JSONArray, proxyTag: String, dnsServers: List<String>) {
        val ips = dnsServers
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.contains('/')) it else "$it/32" }
            .distinct()
        if (ips.isEmpty()) return
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("outboundTag", proxyTag)
                .put("ip", JSONArray().also { arr -> ips.forEach { arr.put(it) } }),
        )
    }
}
