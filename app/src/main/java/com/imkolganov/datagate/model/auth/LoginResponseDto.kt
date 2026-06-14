package com.imkolganov.datagate.model.auth

/** Backend login / google-login / totp verify-login payload in `data`. */
data class LoginResponseDto(
    val token: String? = null,
    val expiration: String? = null,
    val refreshToken: String? = null,
    val refreshExpiration: String? = null,
    val requiresTotp: Boolean = false,
    val loginChallengeId: String? = null,
    val displayName: String? = null,
    val requiresTotpSetup: Boolean = false,
)

@Deprecated("Use LoginResponseDto", ReplaceWith("LoginResponseDto"))
typealias GoogleLoginResponseDto = LoginResponseDto
