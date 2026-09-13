package com.imkolganov.datagate.vpn

/**
 * Pure rules for VPN session cycles (OpenVPN + Xray): which phases exist, which
 * engine may emit them, what the UI flags become, and how process-wide resources
 * (traffic monitor / tunnel session store) move between owners.
 */
object VpnSessionCyclePolicy {

    data class UiFlags(
        val connectRequested: Boolean,
        val connected: Boolean,
        val paused: Boolean,
    )

    data class CycleState(
        val phase: VpnSessionPhase = VpnSessionPhase.DISCONNECTED,
        val activeEngine: String? = null,
        val monitorOwner: String? = null,
        val storeOwner: String? = null,
        val xrayGeneration: Int = 0,
        val xrayStopping: Boolean = false,
        val ui: UiFlags = uiFlags(VpnSessionPhase.DISCONNECTED),
        val ignored: Boolean = false,
    )

    fun uiFlags(phase: VpnSessionPhase): UiFlags = when (phase) {
        VpnSessionPhase.DISCONNECTED,
        VpnSessionPhase.DISCONNECTING,
        VpnSessionPhase.ERROR,
        VpnSessionPhase.TUN_SETUP_FAILED -> UiFlags(
            connectRequested = false,
            connected = false,
            paused = false,
        )
        VpnSessionPhase.CONNECTED -> UiFlags(
            connectRequested = true,
            connected = true,
            paused = false,
        )
        VpnSessionPhase.PAUSED -> UiFlags(
            connectRequested = true,
            connected = false,
            paused = true,
        )
        VpnSessionPhase.CONNECTING,
        VpnSessionPhase.RESUMED,
        VpnSessionPhase.WAITING_NETWORK,
        VpnSessionPhase.RECONNECTING,
        VpnSessionPhase.SELECTING_SERVER,
        VpnSessionPhase.SELECTED_SERVER,
        VpnSessionPhase.GETTING_INSTALLATION_ID,
        VpnSessionPhase.GETTING_EXTERNAL_ID,
        VpnSessionPhase.BUILDING_COMMON_NAME,
        VpnSessionPhase.DOWNLOADING_CONFIG,
        VpnSessionPhase.CONFIG_RECEIVED,
        VpnSessionPhase.RESOLVE,
        VpnSessionPhase.WAIT,
        VpnSessionPhase.GET_CONFIG,
        VpnSessionPhase.ASSIGN_IP -> UiFlags(
            connectRequested = true,
            connected = false,
            paused = false,
        )
    }

    fun resourceOwner(engineName: String): String =
        if (engineName.equals(OpenVpn3Service.ENGINE_XRAY, ignoreCase = true) ||
            engineName.equals(VpnTunnelSessionStore.OWNER_XRAY, ignoreCase = true)
        ) {
            VpnTunnelSessionStore.OWNER_XRAY
        } else {
            VpnTunnelSessionStore.OWNER_OPENVPN
        }

    fun isSupportedBy(phase: VpnSessionPhase, engineName: String): Boolean {
        val xray = engineName.equals(OpenVpn3Service.ENGINE_XRAY, ignoreCase = true)
        return if (xray) phase in xrayPhases else phase in openVpnPhases
    }

    fun diesWithProcess(phase: VpnSessionPhase): Boolean = when (phase) {
        VpnSessionPhase.CONNECTED,
        VpnSessionPhase.CONNECTING,
        VpnSessionPhase.DISCONNECTING,
        VpnSessionPhase.PAUSED,
        VpnSessionPhase.RESUMED,
        VpnSessionPhase.RECONNECTING,
        VpnSessionPhase.WAITING_NETWORK -> true
        else -> false
    }

    fun isAllowedTransition(
        from: VpnSessionPhase,
        to: VpnSessionPhase,
        engineName: String,
    ): Boolean {
        if (!isSupportedBy(to, engineName)) return false
        if (from == to) return true
        return to in successors(from, engineName)
    }

    fun markActiveEngine(state: CycleState, engineName: String): CycleState =
        state.copy(activeEngine = engineName, xrayStopping = false, ignored = false)

    fun beginXrayConnect(state: CycleState): Pair<Int, CycleState> {
        val next = state.copy(
            activeEngine = OpenVpn3Service.ENGINE_XRAY,
            xrayStopping = false,
            xrayGeneration = state.xrayGeneration + 1,
            ignored = false,
        )
        return next.xrayGeneration to next
    }

    fun beginXrayStop(state: CycleState): Pair<Int, CycleState> {
        val next = state.copy(
            xrayStopping = true,
            xrayGeneration = state.xrayGeneration + 1,
            ignored = false,
        )
        return next.xrayGeneration to next
    }

    fun teardownEngine(state: CycleState, owner: String): CycleState {
        val monitor = state.monitorOwner?.takeIf { it != owner }
        val store = state.storeOwner?.takeIf { it != owner }
        return state.copy(monitorOwner = monitor, storeOwner = store, ignored = false)
    }

    fun userDisconnect(state: CycleState): CycleState {
        var next = teardownEngine(state, VpnTunnelSessionStore.OWNER_OPENVPN)
        next = teardownEngine(next, VpnTunnelSessionStore.OWNER_XRAY)
        return next.copy(
            phase = VpnSessionPhase.DISCONNECTED,
            activeEngine = null,
            ui = uiFlags(VpnSessionPhase.DISCONNECTED),
            xrayStopping = true,
            ignored = false,
        )
    }

    fun applyBroadcast(
        state: CycleState,
        eventName: String,
        eventEngine: String,
        fromQuery: Boolean = false,
    ): CycleState {
        if (!VpnEngineStatusPolicy.shouldApplyStatusBroadcast(state.activeEngine, eventEngine)) {
            return state.copy(ignored = true)
        }
        if (OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
                fromQuery = fromQuery,
                eventName = eventName,
                isConnectRequested = state.ui.connectRequested,
                isVpnConnected = state.ui.connected,
            )
        ) {
            return state.copy(ignored = true)
        }
        val phase = VpnSessionPhase.fromEventName(eventName) ?: return state.copy(ignored = true)
        val owner = resourceOwner(eventEngine)
        val leavingConnected = state.phase == VpnSessionPhase.CONNECTED &&
            phase != VpnSessionPhase.CONNECTED
        var monitor = state.monitorOwner
        var store = state.storeOwner
        if (phase == VpnSessionPhase.CONNECTED) {
            monitor = owner
            store = owner
        } else {
            if (leavingConnected && (monitor == null || monitor == owner)) {
                monitor = null
            }
            if (clearsSessionStore(phase) && (store == null || store == owner)) {
                store = null
            }
            if (clearsSessionStore(phase) && (monitor == null || monitor == owner)) {
                monitor = null
            }
        }
        return state.copy(
            phase = phase,
            monitorOwner = monitor,
            storeOwner = store,
            ui = uiFlags(phase),
            ignored = false,
        )
    }

    private fun clearsSessionStore(phase: VpnSessionPhase): Boolean = when (phase) {
        VpnSessionPhase.DISCONNECTED,
        VpnSessionPhase.DISCONNECTING,
        VpnSessionPhase.ERROR,
        VpnSessionPhase.TUN_SETUP_FAILED,
        VpnSessionPhase.RECONNECTING -> true
        else -> false
    }

    private val openVpnPhases: Set<VpnSessionPhase> = VpnSessionPhase.catalog.toSet()

    private val xrayPhases: Set<VpnSessionPhase> = setOf(
        VpnSessionPhase.DISCONNECTED,
        VpnSessionPhase.CONNECTING,
        VpnSessionPhase.CONNECTED,
        VpnSessionPhase.DISCONNECTING,
        VpnSessionPhase.WAITING_NETWORK,
        VpnSessionPhase.RECONNECTING,
        VpnSessionPhase.ERROR,
        VpnSessionPhase.TUN_SETUP_FAILED,
        VpnSessionPhase.SELECTING_SERVER,
        VpnSessionPhase.SELECTED_SERVER,
        VpnSessionPhase.GETTING_INSTALLATION_ID,
        VpnSessionPhase.GETTING_EXTERNAL_ID,
        VpnSessionPhase.BUILDING_COMMON_NAME,
        VpnSessionPhase.DOWNLOADING_CONFIG,
        VpnSessionPhase.CONFIG_RECEIVED,
    )

    private val handshake: Set<VpnSessionPhase> = setOf(
        VpnSessionPhase.RESOLVE,
        VpnSessionPhase.WAIT,
        VpnSessionPhase.GET_CONFIG,
        VpnSessionPhase.ASSIGN_IP,
    )

    private val preconnect: Set<VpnSessionPhase> = setOf(
        VpnSessionPhase.SELECTING_SERVER,
        VpnSessionPhase.SELECTED_SERVER,
        VpnSessionPhase.GETTING_INSTALLATION_ID,
        VpnSessionPhase.GETTING_EXTERNAL_ID,
        VpnSessionPhase.BUILDING_COMMON_NAME,
        VpnSessionPhase.DOWNLOADING_CONFIG,
        VpnSessionPhase.CONFIG_RECEIVED,
        VpnSessionPhase.CONNECTING,
    )

    private fun successors(from: VpnSessionPhase, engineName: String): Set<VpnSessionPhase> {
        val xray = engineName.equals(OpenVpn3Service.ENGINE_XRAY, ignoreCase = true)
        val sharedTerminal = setOf(
            VpnSessionPhase.DISCONNECTING,
            VpnSessionPhase.DISCONNECTED,
            VpnSessionPhase.ERROR,
            VpnSessionPhase.TUN_SETUP_FAILED,
        )
        return when (from) {
            VpnSessionPhase.DISCONNECTED -> preconnect + VpnSessionPhase.ERROR
            VpnSessionPhase.SELECTING_SERVER,
            VpnSessionPhase.SELECTED_SERVER,
            VpnSessionPhase.GETTING_INSTALLATION_ID,
            VpnSessionPhase.GETTING_EXTERNAL_ID,
            VpnSessionPhase.BUILDING_COMMON_NAME,
            VpnSessionPhase.DOWNLOADING_CONFIG,
            VpnSessionPhase.CONFIG_RECEIVED ->
                preconnect + sharedTerminal - from
            VpnSessionPhase.CONNECTING ->
                handshake + setOf(
                    VpnSessionPhase.CONNECTED,
                    VpnSessionPhase.WAITING_NETWORK,
                    VpnSessionPhase.RECONNECTING,
                ) + sharedTerminal
            VpnSessionPhase.RESOLVE,
            VpnSessionPhase.WAIT,
            VpnSessionPhase.GET_CONFIG,
            VpnSessionPhase.ASSIGN_IP ->
                handshake + setOf(VpnSessionPhase.CONNECTING, VpnSessionPhase.CONNECTED) + sharedTerminal
            VpnSessionPhase.CONNECTED ->
                sharedTerminal + setOf(
                    VpnSessionPhase.RECONNECTING,
                    VpnSessionPhase.WAITING_NETWORK,
                    VpnSessionPhase.CONNECTING,
                ) + if (xray) {
                    emptySet()
                } else {
                    setOf(VpnSessionPhase.PAUSED)
                }
            VpnSessionPhase.PAUSED -> setOf(
                VpnSessionPhase.RESUMED,
                VpnSessionPhase.DISCONNECTING,
                VpnSessionPhase.DISCONNECTED,
                VpnSessionPhase.ERROR,
            )
            VpnSessionPhase.RESUMED -> setOf(
                VpnSessionPhase.CONNECTING,
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.PAUSED,
                VpnSessionPhase.WAITING_NETWORK,
            ) + sharedTerminal
            VpnSessionPhase.WAITING_NETWORK -> setOf(
                VpnSessionPhase.CONNECTING,
                VpnSessionPhase.RECONNECTING,
            ) + sharedTerminal
            VpnSessionPhase.RECONNECTING -> setOf(
                VpnSessionPhase.CONNECTING,
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.WAITING_NETWORK,
            ) + sharedTerminal
            VpnSessionPhase.DISCONNECTING -> setOf(VpnSessionPhase.DISCONNECTED, VpnSessionPhase.ERROR)
            VpnSessionPhase.ERROR,
            VpnSessionPhase.TUN_SETUP_FAILED -> preconnect + setOf(VpnSessionPhase.DISCONNECTED)
        }.let { allowed ->
            if (xray) allowed.intersect(xrayPhases) else allowed
        }
    }
}
