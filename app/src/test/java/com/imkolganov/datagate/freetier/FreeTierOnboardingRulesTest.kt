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
    fun evaluateFreeTierStatusFetch_errorOnApiFailureMessage() {
        val result = evaluateFreeTierStatusFetch(
            response = ApiResponse(success = true, message = null, data = status()),
            apiFailureMessage = "Network error"
        )
        assertEquals(FreeTierStatusFetchOutcome.ShowStatusError, result.outcome)
        assertEquals("Network error", result.errorMessage)
    }

    @Test
    fun evaluateFreeTierStatusFetch_errorWhenSuccessFalse() {
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = false, message = "Unauthorized", data = null)
        )
        assertEquals(FreeTierStatusFetchOutcome.ShowStatusError, result.outcome)
        assertEquals("Unauthorized", result.errorMessage)
    }

    @Test
    fun evaluateFreeTierStatusFetch_hideWhenSuccessTrueButNotApplicable() {
        val result = evaluateFreeTierStatusFetch(
            ApiResponse(success = true, message = null, data = status(isApplicable = false))
        )
        assertEquals(FreeTierStatusFetchOutcome.HideOnboarding, result.outcome)
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
    fun parseTelegramUserId_acceptsPositiveNumericId() {
        assertEquals(123456789L, parseTelegramUserId("123456789"))
        assertEquals(123456789L, parseTelegramUserId(" 123456789 "))
    }

    @Test
    fun parseTelegramUserId_rejectsBlankOrInvalid() {
        assertEquals(null, parseTelegramUserId(""))
        assertEquals(null, parseTelegramUserId("   "))
        assertEquals(null, parseTelegramUserId("abc"))
        assertEquals(null, parseTelegramUserId("0"))
        assertEquals(null, parseTelegramUserId("-1"))
    }
}
