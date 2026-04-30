package com.imkolganov.datagate.model.auth

data class LoginPasswordRequestDto(
    val login: String,
    val password: String
)
