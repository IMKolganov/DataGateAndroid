package com.imkolganov.datagate.freetier

import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

const val FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS = 45_000L

/**
 * The status check can transiently fail with a network-level exception (e.g. "Socket closed") when
 * it happens to run while the VPN tunnel is being torn down/re-established — that's not a real
 * access-check failure, so we retry silently before surfacing an error dialog to the user.
 */
const val FREE_TIER_STATUS_FETCH_MAX_ATTEMPTS = 10
const val FREE_TIER_STATUS_FETCH_RETRY_DELAY_MS = 3_000L

/** Warn when little time remains to connect VPN and open Telegram before the code expires. */
const val FREE_TIER_LINK_CODE_EXPIRY_WARNING_SECONDS = 300

const val DEFAULT_REQUIRED_TELEGRAM_CHANNEL_HANDLE = "DataGateVPNBot"

enum class FreeTierOnboardingCopyMode {
    LinkAccount,
    SubscribeOnly,
    Generic,
}

enum class FreeTierStatusFetchOutcome {
    HideOnboarding,
    ShowOnboarding,
    ShowStatusError,
}

data class FreeTierStatusFetchResult(
    val outcome: FreeTierStatusFetchOutcome,
    val status: FreeTierAccessStatusResponse? = null,
    val errorMessage: String? = null,
)

fun shouldShowFreeTierOnboarding(status: FreeTierAccessStatusResponse?): Boolean {
    val s = status ?: return false
    return s.isApplicable && !s.isCompliant
}

fun shouldRefreshFreeTierStatusOnResume(
    lastStatusFetchMs: Long,
    refreshOnNextResume: Boolean,
    nowMs: Long,
    minIntervalMs: Long = FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS,
): Boolean = refreshOnNextResume || shouldRefreshFreeTierStatusOnPoll(lastStatusFetchMs, nowMs, minIntervalMs)

/** Tab-open / nonce polls respect the same minimum interval as resume refresh. */
fun shouldRefreshFreeTierStatusOnPoll(
    lastStatusFetchMs: Long,
    nowMs: Long,
    minIntervalMs: Long = FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS,
): Boolean = lastStatusFetchMs == 0L || nowMs - lastStatusFetchMs >= minIntervalMs

fun freeTierOnboardingCopyMode(status: FreeTierAccessStatusResponse): FreeTierOnboardingCopyMode =
    when {
        status.canRequestAccountLinkCode -> FreeTierOnboardingCopyMode.LinkAccount
        status.isLinkedToTelegram -> FreeTierOnboardingCopyMode.SubscribeOnly
        else -> FreeTierOnboardingCopyMode.Generic
}

fun evaluateFreeTierStatusFetch(
    response: ApiResponse<FreeTierAccessStatusResponse>,
    apiFailureMessage: String? = null,
): FreeTierStatusFetchResult {
    apiFailureMessage?.let { msg ->
        return FreeTierStatusFetchResult(
            outcome = FreeTierStatusFetchOutcome.ShowStatusError,
            errorMessage = msg,
        )
    }
    if (!response.success) {
        return FreeTierStatusFetchResult(
            outcome = FreeTierStatusFetchOutcome.ShowStatusError,
            errorMessage = response.message?.ifBlank { null },
        )
    }
    val status = response.data
    return if (shouldShowFreeTierOnboarding(status)) {
        FreeTierStatusFetchResult(
            outcome = FreeTierStatusFetchOutcome.ShowOnboarding,
            status = status,
        )
    } else {
        FreeTierStatusFetchResult(outcome = FreeTierStatusFetchOutcome.HideOnboarding)
    }
}

fun isFreeTierLinkCodeExpired(expiresAtMs: Long, nowMs: Long): Boolean =
    expiresAtMs > 0L && nowMs >= expiresAtMs

fun shouldWarnLinkCodeExpiringSoon(
    secondsLeft: Int,
    warningThresholdSeconds: Int = FREE_TIER_LINK_CODE_EXPIRY_WARNING_SECONDS,
): Boolean = secondsLeft in 1..warningThresholdSeconds

/** Parses the backend's `graceExpiresAtUtc` ISO-8601 string into epoch millis, or null if absent/unparseable. */
fun parseGraceExpiresAtMs(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

fun graceSecondsRemaining(expiresAtMs: Long, nowMs: Long): Int =
    ((expiresAtMs - nowMs) / 1000L).toInt().coerceAtLeast(0)

/**
 * True once a tracked grace window has passed its expiry — the backend's enforcement job runs on
 * an admin-configurable interval (`EnforcementIntervalMinutes`, default 15 min), not immediately
 * at expiry, so a grace-triggered disconnect can land anywhere from seconds to many minutes after
 * [graceExpiresAtMs]. There's no way to distinguish "server killed us for non-compliance" from an
 * unrelated network blip once grace has expired — but if the user is still non-compliant, that
 * distinction doesn't matter: enforcement will just kill any new connection again on its next
 * pass, so we shouldn't auto-reconnect either way.
 *
 * Used to suppress OpenVpn3Service's normal auto-reconnect-on-disconnect: retrying blindly would
 * just churn through more short grace windows without fixing the underlying non-compliance.
 */
fun isDisconnectAttributableToGraceExpiry(graceExpiresAtMs: Long?, nowMs: Long): Boolean {
    if (graceExpiresAtMs == null) return false
    return nowMs >= graceExpiresAtMs
}
