package com.imkolganov.datagate.vpn.xray

/** Guards Xray connect / disconnect against a later session claiming the service. */
internal object XrayConnectSessionPolicy {
    fun isCurrent(session: Int, generation: Int, stopping: Boolean): Boolean =
        session == generation && !stopping

    fun shouldApplyStop(stopGeneration: Int, currentGeneration: Int): Boolean =
        stopGeneration == currentGeneration
}
