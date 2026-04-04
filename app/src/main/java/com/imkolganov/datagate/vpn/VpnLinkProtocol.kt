package com.imkolganov.datagate.vpn

/**
 * OpenVPN transport to the local WSS bridge (TCP or UDP).
 * Must match the profile: we derive this from the downloaded `.ovpn` `proto` line
 * (`proto udp`, `proto tcp-client`, etc.), not from user settings.
 */
enum class VpnLinkProtocol {
    TCP,
    UDP;

    fun configProtoLine(): String = when (this) {
        TCP -> "proto tcp-client"
        UDP -> "proto udp"
    }

    fun intentValue(): String = name.lowercase()

    companion object {
        /**
         * Reads the first non-comment `proto …` line from OpenVPN config text.
         * `proto udp` / `proto udp4` → UDP; `proto tcp` / `proto tcp-client` / `proto tcp-server` → TCP.
         * If there is no `proto` line, defaults to [TCP] (same as our previous app default).
         */
        fun fromOvpnConfigContent(content: String): VpnLinkProtocol {
            val lines = content.replace("\r\n", "\n").split("\n")
            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#") || line.startsWith(";")) continue
                val hashOrSemi = line.indexOfAny(charArrayOf('#', ';'))
                if (hashOrSemi > 0) {
                    line = line.substring(0, hashOrSemi).trim()
                    if (line.isEmpty()) continue
                }
                val lower = line.lowercase()
                if (!lower.startsWith("proto ")) continue
                val tokens = lower.split(Regex("\\s+"))
                if (tokens.size < 2) continue
                val p = tokens[1]
                return when {
                    p.startsWith("udp") -> UDP
                    p.startsWith("tcp") -> TCP
                    else -> TCP
                }
            }
            return TCP
        }

        fun fromIntentExtra(extra: String?): VpnLinkProtocol {
            if (extra.equals(UDP.intentValue(), ignoreCase = true)) return UDP
            return TCP
        }
    }
}
