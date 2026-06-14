package com.imkolganov.datagate.auth

import com.imkolganov.datagate.model.auth.LoginResponseDto

sealed class ResolvedLoginFlow {
    data class TotpChallenge(
        val loginChallengeId: String,
        val displayName: String?,
    ) : ResolvedLoginFlow()

    /** Tokens are present; persist before returning. [requiresTotpSetup] blocks main UI until enrollment. */
    data class Tokens(
        val response: LoginResponseDto,
        val requiresTotpSetup: Boolean,
    ) : ResolvedLoginFlow()
}

fun resolveLoginFlow(payload: LoginResponseDto): ResolvedLoginFlow {
    if (payload.requiresTotp && !payload.loginChallengeId.isNullOrBlank()) {
        return ResolvedLoginFlow.TotpChallenge(
            loginChallengeId = payload.loginChallengeId,
            displayName = payload.displayName,
        )
    }

    val token = payload.token?.trim().orEmpty()
    if (token.isEmpty()) {
        throw IllegalStateException("No token returned by API.")
    }

    return ResolvedLoginFlow.Tokens(
        response = payload,
        requiresTotpSetup = payload.requiresTotpSetup,
    )
}

fun isLoginChallengeExpiredMessage(message: String): Boolean {
    return Regex("challenge expired|too many invalid attempts|sign in again", RegexOption.IGNORE_CASE)
        .containsMatchIn(message)
}
