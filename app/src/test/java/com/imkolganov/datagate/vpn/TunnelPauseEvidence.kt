package com.imkolganov.datagate.vpn

/**
 * Test contract: what counts as proof the tunnel actually stopped (2ip.ru), vs UI/broadcast only.
 */
internal object TunnelPauseEvidence {

    fun uiClaimsPaused(state: VpnStatusUiState): Boolean =
        state.isVpnPaused && !state.isVpnConnected

    /**
     * Real tunnel stop requires native pause to execute while connect() is active.
     * UI/broadcast alone is never sufficient — see [OpenVpnNativePauseResumeSchedulingTest].
     */
    fun tunnelStopped(nativePauseExecuted: Boolean, ui: VpnStatusUiState): Boolean =
        nativePauseExecuted && uiClaimsPaused(ui)
}
