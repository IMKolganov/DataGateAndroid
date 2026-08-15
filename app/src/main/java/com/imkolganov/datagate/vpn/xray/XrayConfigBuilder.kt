package com.imkolganov.datagate.vpn.xray

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a full Xray client JSON (TUN inbound + proxy outbounds + routing)
 * suitable for [libXray.LibXray] `runXrayFromJson`.
 */
object XrayConfigBuilder {

    /**
     * @param outboundsJson JSON array of outbound objects, or a full config object containing `outbounds`.
     * @param tunFd Android TUN file descriptor from [android.net.VpnService.Builder.establish].
     */
    fun buildTunClientConfig(
        outboundsJson: String,
        tunFd: Int,
        dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
        mtu: Int = 1500,
    ): String {
        val outbounds = extractOutbounds(outboundsJson)
        require(outbounds.length() > 0) { "No Xray outbounds in config" }

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

        val routing = JSONObject()
            .put("domainStrategy", "AsIs")
            .put(
                "rules",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "field")
                            .put("outboundTag", "direct")
                            .put("ip", JSONArray().put("geoip:private")),
                    )
                    .put(
                        JSONObject()
                            .put("type", "field")
                            .put("outboundTag", proxyTag)
                            .put("network", "tcp,udp"),
                    ),
            )

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
}
