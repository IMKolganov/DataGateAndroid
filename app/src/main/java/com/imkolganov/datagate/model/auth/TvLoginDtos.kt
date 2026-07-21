package com.imkolganov.datagate.model.auth

/** POST /api/auth/tv/session → data */
data class CreateTvLoginSessionResponse(
    val sessionId: String,
    val userCode: String,
    val verificationUrl: String?,
    val qrPayload: String,
    val expiresAt: String,
    val pollIntervalSeconds: Int,
    val signalRHubPath: String,
)

/** GET /api/auth/tv/session/{sessionId} → data */
data class TvLoginSessionPollResponse(
    val status: String,
    val expiresAt: String?,
    val userId: Int = 0,
    val displayName: String? = null,
    val email: String? = null,
    val token: String? = null,
    val expiration: String? = null,
    val refreshToken: String? = null,
    val refreshExpiration: String? = null,
    val requiresTotp: Boolean = false,
    val loginChallengeId: String? = null,
    val requiresTotpSetup: Boolean = false,
)

object TvLoginSessionStatus {
    const val PENDING = "pending"
    const val VIEWED = "viewed"
    const val APPROVED = "approved"
    const val DENIED = "denied"
    const val EXPIRED = "expired"
    const val CONSUMED = "consumed"

    fun normalize(raw: String?): String = raw?.trim()?.lowercase().orEmpty()

    fun isTerminal(status: String): Boolean =
        when (normalize(status)) {
            APPROVED, DENIED, EXPIRED, CONSUMED -> true
            else -> false
        }
}
