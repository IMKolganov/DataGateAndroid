package com.imkolganov.datagate.auth.http

data class GoogleLoginRequestDto(val idToken: String)

data class GoogleLoginResponseDto(
    val token: String,
    val expirationEpochSeconds: Long
)

interface BackendAuthApi {
    suspend fun googleLogin(request: GoogleLoginRequestDto): GoogleLoginResponseDto
}
