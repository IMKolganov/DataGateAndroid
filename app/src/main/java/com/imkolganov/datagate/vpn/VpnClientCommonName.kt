package com.imkolganov.datagate.vpn

/**
 * OpenVPN client certificate commonName prefix:
 * - phone/tablet: `adg-…`
 * - Android TV: `adgtv-…`
 */
object VpnClientCommonName {
    const val PREFIX_MOBILE = "adg"
    const val PREFIX_TELEVISION = "adgtv"

    fun prefix(isTelevision: Boolean): String =
        if (isTelevision) PREFIX_TELEVISION else PREFIX_MOBILE

    fun build(
        isTelevision: Boolean,
        serverId: Int,
        externalId: String,
        shortInstallationId: String,
    ): String =
        "${prefix(isTelevision)}-$serverId-$externalId-$shortInstallationId"
}
