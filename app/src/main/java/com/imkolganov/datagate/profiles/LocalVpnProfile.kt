package com.imkolganov.datagate.profiles

import com.imkolganov.datagate.model.servers.VpnServerType

/**
 * User-managed VPN profile stored on device (imported config).
 */
data class LocalVpnProfile(
    val id: String,
    val name: String,
    val type: VpnServerType,
    /** Absolute path under app filesDir/profiles/. */
    val configFileName: String,
    val createdAtEpochMs: Long,
    val hasUsername: Boolean = false,
    val hasPassword: Boolean = false,
)

data class LocalVpnProfileCredentials(
    val username: String = "",
    val password: String = "",
)
