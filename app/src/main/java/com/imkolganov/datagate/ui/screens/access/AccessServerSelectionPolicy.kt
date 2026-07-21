package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.vpn.ServerSelectionMode

/**
 * Resolves which server id Access should keep/select after a refresh or mode switch.
 * Never prefers a server outside the user's quota plan (backend 400 is only a last resort).
 */
object AccessServerSelectionPolicy {

    fun resolveSelectedServerId(
        mode: ServerSelectionMode,
        previousSelectedId: Int?,
        servers: List<AccessContract.ServerItem>,
    ): Int? {
        val previousStillAllowed = previousSelectedId?.let { id ->
            selectableServerId(id, servers)
        }
        if (previousStillAllowed != null) return previousStillAllowed

        if (mode != ServerSelectionMode.MANUAL) return null

        return preferredManualServerId(servers)
    }

    fun preferredManualServerId(servers: List<AccessContract.ServerItem>): Int? =
        servers.firstOrNull { it.isOnline && it.isAccessibleForQuotaPlan }?.id
            ?: servers.firstOrNull { it.isAccessibleForQuotaPlan }?.id

    /** Null when the id is unknown or outside the user's quota plan. */
    fun selectableServerId(
        serverId: Int,
        servers: List<AccessContract.ServerItem>,
    ): Int? =
        servers.firstOrNull { it.id == serverId && it.isAccessibleForQuotaPlan }?.id
}
