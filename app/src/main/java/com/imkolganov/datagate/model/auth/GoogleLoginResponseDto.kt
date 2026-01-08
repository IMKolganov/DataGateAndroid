package com.imkolganov.datagate.model.auth

data class GoogleLoginResponseDto(
    val token: String,
    val expiration: String,
    val refreshToken: String?,
    val refreshExpiration: String?
)
