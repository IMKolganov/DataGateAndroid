package com.imkolganov.datagate.vpn

/**
 * When the UI may show «Paused» / broadcast [PAUSED] — prevents optimistic broadcasts
 * before the tunnel actually stops (2ip.ru).
 */
internal object OpenVpnPauseBroadcastPolicy {

    /** [OpenVpn3Service.processPause] must not broadcast PAUSED synchronously. */
    fun mayBroadcastPausedFromUserCommand(): Boolean = false

    /** Authoritative: ovpncli fired [ClientEvent::Pause] → event name "PAUSE". */
    fun shouldBroadcastPausedOnCoreEvent(eventName: String): Boolean =
        eventName.equals("PAUSE", ignoreCase = true)

    fun uiBroadcastEventForCorePause(): String = "PAUSED"

    /** Authoritative: ovpncli fired [ClientEvent::Resume] → event name "RESUME". */
    fun shouldBroadcastResumedOnCoreEvent(eventName: String): Boolean =
        eventName.equals("RESUME", ignoreCase = true)

    fun uiBroadcastEventForCoreResume(): String = "RESUMED"

    fun mayBroadcastResumedFromUserCommand(): Boolean = false
}
