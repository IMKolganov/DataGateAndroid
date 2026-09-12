package com.imkolganov.datagate.vpn.traffic

internal data class TunIfaceCandidate(
    val isVpn: Boolean,
    val interfaceName: String?,
    val hasTunnelIpv4: Boolean,
)

internal object VpnTunIfacePick {
    fun pickName(candidates: List<TunIfaceCandidate>): String? {
        var fallback: String? = null
        for (candidate in candidates) {
            if (!candidate.isVpn) continue
            val name = candidate.interfaceName?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            fallback = name
            if (candidate.hasTunnelIpv4) return name
        }
        return fallback
    }
}
