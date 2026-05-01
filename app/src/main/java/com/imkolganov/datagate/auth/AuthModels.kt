package com.imkolganov.datagate.auth

data class GoogleLoginResult(
    val accessToken: String
)

data class AuthInfo(
    val userId: String?,
    val externalId: String?,
    val role: String?,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?
)
