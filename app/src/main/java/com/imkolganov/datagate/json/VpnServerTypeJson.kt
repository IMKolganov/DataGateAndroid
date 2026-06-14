package com.imkolganov.datagate.json

import com.imkolganov.datagate.model.servers.VpnServerType
import org.json.JSONObject

/** Parses [VpnServerType] from API JSON; defaults to [VpnServerType.OpenVpn] when absent. */
fun JSONObject.optVpnServerType(default: VpnServerType = VpnServerType.OpenVpn): VpnServerType =
    parseVpnServerTypeValue(opt("serverType"))
        ?: parseVpnServerTypeValue(opt("ServerType"))
        ?: default

private fun parseVpnServerTypeValue(raw: Any?): VpnServerType? = when (raw) {
    null, JSONObject.NULL -> null
    is Number -> when (raw.toInt()) {
        0 -> VpnServerType.OpenVpn
        1 -> VpnServerType.Xray
        else -> VpnServerType.Unknown
    }
    is String -> when (raw.trim().lowercase()) {
        "openvpn", "0" -> VpnServerType.OpenVpn
        "xray", "1" -> VpnServerType.Xray
        else -> VpnServerType.Unknown
    }
    else -> null
}
