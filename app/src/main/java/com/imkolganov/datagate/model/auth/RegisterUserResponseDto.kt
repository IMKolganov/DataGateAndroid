package com.imkolganov.datagate.model.auth

data class RegisterUserResponseDto(
    val userId: Int,
    val displayName: String,
    val email: String?,
    val hasDashboardAccess: Boolean
)
