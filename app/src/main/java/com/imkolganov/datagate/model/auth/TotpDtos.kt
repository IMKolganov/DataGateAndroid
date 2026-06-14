package com.imkolganov.datagate.model.auth

data class TotpStatusDto(
    val isAdmin: Boolean = false,
    val totpEnabled: Boolean = false,
    val requiresTotpSetup: Boolean = false,
)

data class TotpSetupDto(
    val sharedSecret: String,
    val otpAuthUri: String?,
    val issuer: String?,
    val accountName: String?,
)

data class TotpVerifyLoginRequestDto(
    val loginChallengeId: String,
    val code: String,
)

data class TotpConfirmRequestDto(
    val code: String,
)

data class TotpDisableRequestDto(
    val code: String,
    val password: String? = null,
)
