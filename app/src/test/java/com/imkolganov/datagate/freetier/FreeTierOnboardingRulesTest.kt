package com.imkolganov.datagate.freetier

import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTierOnboardingRulesTest {

    private fun status(
        isApplicable: Boolean = true,
        isCompliant: Boolean = false,
        canRequestAccountLinkCode: Boolean = false,
        isLinkedToTelegram: Boolean = false,
    ) = FreeTierAccessStatusResponse(
        isApplicable = isApplicable,
        isCompliant = isCompliant,
        isMergedAccount = false,
        isChannelSubscribed = false,
        isGracePeriod = false,
        isLinkedToTelegram = isLinkedToTelegram,
        canRequestAccountLinkCode = canRequestAccountLinkCode,
        activePlanName = "Free",
        requiredChannel = "@DataGateVPNBot",
    )

    @Test
    fun shouldShowFreeTierOnboarding_whenApplicableAndNotCompliant() {
        assertTrue(shouldShowFreeTierOnboarding(status()))
    }

    @Test
    fun shouldShowFreeTierOnboarding_falseWhenCompliant() {
        assertFalse(shouldShowFreeTierOnboarding(status(isCompliant = true)))
    }

    @Test
    fun shouldShowFreeTierOnboarding_falseWhenNotApplicable() {
        assertFalse(shouldShowFreeTierOnboarding(status(isApplicable = false)))
    }

    @Test
    fun shouldShowFreeTierOnboarding_falseWhenStatusNull() {
        assertFalse(shouldShowFreeTierOnboarding(null))
    }

    @Test
    fun shouldRefreshOnResume_whenForced() {
        assertTrue(
            shouldRefreshFreeTierStatusOnResume(
                lastStatusFetchMs = 0L,
                refreshOnNextResume = true,
                nowMs = 1_000L,
            )
        )
    }

    @Test
    fun shouldRefreshOnResume_whenIntervalElapsed() {
        assertTrue(
            shouldRefreshFreeTierStatusOnResume(
                lastStatusFetchMs = 0L,
                refreshOnNextResume = false,
                nowMs = FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS,
            )
        )
    }

    @Test
    fun shouldRefreshOnPoll_whenNeverFetchedOrIntervalElapsed() {
        assertTrue(shouldRefreshFreeTierStatusOnPoll(lastStatusFetchMs = 0L, nowMs = 1L))
        assertTrue(
            shouldRefreshFreeTierStatusOnPoll(
                lastStatusFetchMs = 0L,
                nowMs = FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS,
            )
        )
        assertFalse(
            shouldRefreshFreeTierStatusOnPoll(
                lastStatusFetchMs = 10_000L,
                nowMs = 20_000L,
            )
        )
    }

    @Test
    fun shouldRefreshOnResume_falseWhenTooSoon() {
        assertFalse(
            shouldRefreshFreeTierStatusOnResume(
                lastStatusFetchMs = 10_000L,
                refreshOnNextResume = false,
                nowMs = 20_000L,
            )
        )
    }

    @Test
    fun freeTierOnboardingCopyMode_prefersLinkAccount() {
        assertEquals(
            FreeTierOnboardingCopyMode.LinkAccount,
            freeTierOnboardingCopyMode(status(canRequestAccountLinkCode = true, isLinkedToTelegram = true))
        )
    }

    @Test
    fun freeTierOnboardingCopyMode_subscribeOnlyWhenLinked() {
        assertEquals(
            FreeTierOnboardingCopyMode.SubscribeOnly,
            freeTierOnboardingCopyMode(status(isLinkedToTelegram = true))
        )
    }

    @Test
    fun freeTierOnboardingCopyMode_genericOtherwise() {
        assertEquals(
            FreeTierOnboardingCopyMode.Generic,
            freeTierOnboardingCopyMode(status())
        )
    }

    @Test
    fun evaluateFreeTierStatusFetch_showOnboarding() {
        val s = status(canRequestAccountLinkCode = true)
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = true, message = null, data = s)
        )
        assertEquals(FreeTierStatusFetchOutcome.ShowOnboarding, result.outcome)
        assertEquals(s, result.status)
    }

    @Test
    fun evaluateFreeTierStatusFetch_hideWhenCompliant() {
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = true, message = null, data = status(isCompliant = true))
        )
        assertEquals(FreeTierStatusFetchOutcome.HideOnboarding, result.outcome)
    }

    @Test
    fun evaluateFreeTierStatusFetch_errorOnApiFailureMessage_whenSurfaceErrors() {
        val result = evaluateFreeTierStatusFetch(
            response = ApiResponse(success = true, message = null, data = status()),
            apiFailureMessage = "Network error",
            surfaceErrors = true,
        )
        assertEquals(FreeTierStatusFetchOutcome.ShowStatusError, result.outcome)
        assertEquals("Network error", result.errorMessage)
    }

    @Test
    fun evaluateFreeTierStatusFetch_silentOnApiFailure_byDefault() {
        val result = evaluateFreeTierStatusFetch(
            response = ApiResponse(success = true, message = null, data = status()),
            apiFailureMessage = "Network error",
        )
        assertEquals(FreeTierStatusFetchOutcome.NoChange, result.outcome)
    }

    @Test
    fun evaluateFreeTierStatusFetch_errorWhenSuccessFalse_whenSurfaceErrors() {
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = false, message = "Unauthorized", data = null),
            surfaceErrors = true,
        )
        assertEquals(FreeTierStatusFetchOutcome.ShowStatusError, result.outcome)
        assertEquals("Unauthorized", result.errorMessage)
    }

    @Test
    fun evaluateFreeTierStatusFetch_silentWhenSuccessFalse_byDefault() {
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = false, message = "Unauthorized", data = null),
        )
        assertEquals(FreeTierStatusFetchOutcome.NoChange, result.outcome)
    }

    @Test
    fun evaluateFreeTierStatusFetch_hideWhenSuccessTrueButNotApplicable() {
        val s = status(isApplicable = false)
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = true, message = null, data = s)
        )
        assertEquals(FreeTierStatusFetchOutcome.HideOnboarding, result.outcome)
        assertEquals(s, result.status)
    }

    @Test
    fun isFreeOrDefaultPlan_onlyFreeAndDefault() {
        assertTrue(isFreeOrDefaultPlan("Free"))
        assertTrue(isFreeOrDefaultPlan("default"))
        assertFalse(isFreeOrDefaultPlan("Pro"))
        assertFalse(isFreeOrDefaultPlan("Unlimited"))
        assertFalse(isFreeOrDefaultPlan(null))
        assertFalse(isFreeOrDefaultPlan(""))
    }

    @Test
    fun shouldSkipFreeTierClientChecks_adminOrPaidPlan() {
        assertTrue(shouldSkipFreeTierClientChecks(isAdmin = true, knownPlanName = null))
        assertTrue(shouldSkipFreeTierClientChecks(isAdmin = true, knownPlanName = "Free"))
        assertTrue(shouldSkipFreeTierClientChecks(isAdmin = false, knownPlanName = "Pro"))
        assertTrue(shouldSkipFreeTierClientChecks(isAdmin = false, knownPlanName = "Unlimited"))
        assertFalse(shouldSkipFreeTierClientChecks(isAdmin = false, knownPlanName = null))
        assertFalse(shouldSkipFreeTierClientChecks(isAdmin = false, knownPlanName = "Free"))
        assertFalse(shouldSkipFreeTierClientChecks(isAdmin = false, knownPlanName = "Default"))
    }

    @Test
    fun isFreeTierLinkCodeExpired_trueWhenPastExpiry() {
        assertTrue(isFreeTierLinkCodeExpired(expiresAtMs = 1_000L, nowMs = 1_000L))
        assertTrue(isFreeTierLinkCodeExpired(expiresAtMs = 1_000L, nowMs = 2_000L))
    }

    @Test
    fun isFreeTierLinkCodeExpired_falseWhenActive() {
        assertFalse(isFreeTierLinkCodeExpired(expiresAtMs = 5_000L, nowMs = 1_000L))
    }

    @Test
    fun isFreeTierLinkCodeExpired_falseWhenNotStarted() {
        assertFalse(isFreeTierLinkCodeExpired(expiresAtMs = 0L, nowMs = 1_000L))
    }

    @Test
    fun shouldWarnLinkCodeExpiringSoon_withinThreshold() {
        assertTrue(shouldWarnLinkCodeExpiringSoon(secondsLeft = 300))
        assertTrue(shouldWarnLinkCodeExpiringSoon(secondsLeft = 1))
        assertFalse(shouldWarnLinkCodeExpiringSoon(secondsLeft = 301))
        assertFalse(shouldWarnLinkCodeExpiringSoon(secondsLeft = 0))
    }

    @Test
    fun parseGraceExpiresAtMs_parsesUtcZFormat() {
        assertEquals(1_783_800_900_000L, parseGraceExpiresAtMs("2026-07-11T20:15:00Z"))
    }

    @Test
    fun parseGraceExpiresAtMs_parsesOffsetFormat() {
        assertEquals(1_783_800_900_000L, parseGraceExpiresAtMs("2026-07-11T22:15:00+02:00"))
    }

    @Test
    fun parseGraceExpiresAtMs_nullOrBlank_returnsNull() {
        assertEquals(null, parseGraceExpiresAtMs(null))
        assertEquals(null, parseGraceExpiresAtMs(""))
    }

    @Test
    fun parseGraceExpiresAtMs_malformed_returnsNull() {
        assertEquals(null, parseGraceExpiresAtMs("not-a-date"))
    }

    @Test
    fun graceSecondsRemaining_computesWholeSecondsAndFloorsAtZero() {
        assertEquals(60, graceSecondsRemaining(expiresAtMs = 60_000L, nowMs = 0L))
        assertEquals(0, graceSecondsRemaining(expiresAtMs = 0L, nowMs = 60_000L))
    }

    @Test
    fun isDisconnectAttributableToGraceExpiry_trueOnceExpired() {
        assertTrue(isDisconnectAttributableToGraceExpiry(graceExpiresAtMs = 100_000L, nowMs = 100_000L))
        // Backend enforcement runs on an admin-configurable interval (default 15 min), not
        // immediately at expiry, so this must stay true arbitrarily long after expiry too.
        assertTrue(isDisconnectAttributableToGraceExpiry(graceExpiresAtMs = 100_000L, nowMs = 100_000L + 3_600_000L))
    }

    @Test
    fun isDisconnectAttributableToGraceExpiry_falseBeforeExpiry() {
        assertFalse(isDisconnectAttributableToGraceExpiry(graceExpiresAtMs = 100_000L, nowMs = 50_000L))
    }

    @Test
    fun isDisconnectAttributableToGraceExpiry_falseWhenNull() {
        assertFalse(isDisconnectAttributableToGraceExpiry(graceExpiresAtMs = null, nowMs = 100_000L))
    }
}
