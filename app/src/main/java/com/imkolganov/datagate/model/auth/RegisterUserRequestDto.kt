package com.imkolganov.datagate.model.auth

data class RegisterUserRequestDto(
    val displayName: String,
    val email: String?,
    val login: String,
    val password: String,
    val confirmPassword: String
)
