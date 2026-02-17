package com.imkolganov.datagate.model.auth

data class RefreshRequestDto(
    val refreshToken: String,
    val deviceId: String?,
    val userAgent: String?
)