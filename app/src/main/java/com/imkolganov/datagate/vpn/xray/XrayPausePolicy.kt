package com.imkolganov.datagate.vpn.xray

/** Xray has no ovpncli pause; we drop TUN/core and keep the service + pending config. */
internal object XrayPausePolicy {
    fun shouldKeepServiceOnPause(): Boolean = true

    fun shouldAutoReconnect(
        desiredConnection: Boolean,
        stopping: Boolean,
        paused: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        connectInFlight: Boolean = false,
    ): Boolean = !paused &&
        XrayNetworkPolicy.shouldReconnect(
            desiredConnection = desiredConnection,
            stopping = stopping,
            running = running,
            networkAvailable = networkAvailable,
            connectInFlight = connectInFlight,
        )
}
