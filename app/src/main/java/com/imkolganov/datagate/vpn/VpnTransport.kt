package com.imkolganov.datagate.vpn

/**
 * How OpenVPN reaches the remote:
 * - [Wss]: local TCP/UDP bridge rewritten to 127.0.0.1, forwarded over WebSocket proxy.
 * - [Direct]: use the OVPN `remote` as-is (socket_protect on the real peer).
 */
enum class VpnTransport {
    Wss,
    Direct;

    fun intentValue(): String = name.lowercase()

    companion object {
        fun fromIntentExtra(extra: String?): VpnTransport {
            if (extra.equals(Direct.intentValue(), ignoreCase = true)) return Direct
            return Wss
        }
    }
}
