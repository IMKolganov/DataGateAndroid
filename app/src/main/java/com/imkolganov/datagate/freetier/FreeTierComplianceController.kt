package com.imkolganov.datagate.freetier

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signals when the Free/Default compliance onboarding dialog is visible so other overlays
 * (e.g. [com.imkolganov.datagate.update.UpdateCheckHost]) can defer auto prompts.
 */
object FreeTierComplianceController {

    private val _onboardingVisible = MutableStateFlow(false)
    val onboardingVisible: StateFlow<Boolean> = _onboardingVisible.asStateFlow()

    private val _statusRefreshNonce = MutableStateFlow(0)
    val statusRefreshNonce: StateFlow<Int> = _statusRefreshNonce.asStateFlow()

    fun setOnboardingVisible(visible: Boolean) {
        _onboardingVisible.value = visible
    }

    /** Triggers a free-tier status poll (e.g. when opening Home or Access). */
    fun requestStatusRefresh() {
        _statusRefreshNonce.value += 1
    }
}
