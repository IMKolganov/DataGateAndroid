package com.imkolganov.datagate.vpn.diag

/**
 * How to read a pair of connectivity probes when something (e.g. a video) fails to load.
 *
 * - [SITE_OR_APP]: the VPN path works → look at the site/app or split-tunnel bypass.
 * - [VPN_PATH]: underlying works, VPN probe fails → tunnel / engine / VPN DNS.
 * - [DEVICE_INTERNET]: underlying is down → Wi‑Fi / cellular / ISP, not the site.
 * - [UNKNOWN]: probes did not finish or returned nothing useful.
 */
enum class VpnPathHint {
    SITE_OR_APP,
    VPN_PATH,
    DEVICE_INTERNET,
    UNKNOWN,
}

internal object VpnPathHintPolicy {
    /**
     * Missing underlying [Network] is [null], not false — many OEMs hide Wi‑Fi behind the VPN
     * iface, so "no underlying" must not be read as "internet is down".
     */
    fun underlyingOk(hasUnderlyingNetwork: Boolean, httpOk: Boolean): Boolean? =
        if (hasUnderlyingNetwork) httpOk else null

    fun fromProbes(vpnOk: Boolean?, underOk: Boolean?): VpnPathHint = when {
        vpnOk == true -> VpnPathHint.SITE_OR_APP
        underOk == true -> VpnPathHint.VPN_PATH
        underOk == false -> VpnPathHint.DEVICE_INTERNET
        else -> VpnPathHint.UNKNOWN
    }
}
