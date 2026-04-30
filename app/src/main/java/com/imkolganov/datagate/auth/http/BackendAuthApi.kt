package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.model.auth.ConfirmEmailResultDto
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.GoogleLoginResponseDto
import com.imkolganov.datagate.model.auth.LoginPasswordRequestDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto
import com.imkolganov.datagate.model.auth.RegisterUserRequestDto
import com.imkolganov.datagate.model.auth.RegisterUserResponseDto

interface BackendAuthApi {
    suspend fun googleLogin(request: GoogleLoginRequestDto): GoogleLoginResponseDto
    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto

    suspend fun register(request: RegisterUserRequestDto): RegisterUserResponseDto

    suspend fun loginWithPassword(request: LoginPasswordRequestDto): GoogleLoginResponseDto

    /** Backend always returns a generic success line (anti-enumeration). */
    suspend fun requestEmailConfirmation(email: String): String

    suspend fun confirmEmail(email: String, code: String): ConfirmEmailResultDto
}
