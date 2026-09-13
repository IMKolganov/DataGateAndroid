package com.imkolganov.datagate.vpn.xray

/** When Xray should wait, reconnect, or warn after a network / health change. */
internal object XrayNetworkPolicy {
    fun hasUsableNetwork(hasInternet: Boolean, validated: Boolean): Boolean =
        hasInternet && validated

    fun shouldReconnect(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
    ): Boolean = desiredConnection && !stopping && !paused && !running && networkAvailable

    fun shouldWaitForNetwork(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
    ): Boolean = desiredConnection && !stopping && !paused && !running && !networkAvailable

    fun shouldRestartUnhealthySession(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        coreRunning: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
    ): Boolean = desiredConnection && !stopping && !paused && running && !coreRunning && networkAvailable

    fun shouldWarnMissingVpnTransport(running: Boolean, hasVpnTransport: Boolean): Boolean =
        running && !hasVpnTransport
}
