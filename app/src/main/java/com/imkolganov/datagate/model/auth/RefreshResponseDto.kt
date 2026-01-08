package com.imkolganov.datagate.model.auth


data class RefreshResponseDto(
    val token: String,
    val expiration: String,
    val refreshToken: String?,
    val refreshExpiration: String?
)
