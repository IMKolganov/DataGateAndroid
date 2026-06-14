package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.model.auth.ConfirmEmailResultDto
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.LoginPasswordRequestDto
import com.imkolganov.datagate.model.auth.LoginResponseDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto
import com.imkolganov.datagate.model.auth.RegisterUserRequestDto
import com.imkolganov.datagate.model.auth.RegisterUserResponseDto
import com.imkolganov.datagate.model.auth.ResetPasswordResultDto
import com.imkolganov.datagate.model.auth.TotpConfirmRequestDto
import com.imkolganov.datagate.model.auth.TotpDisableRequestDto
import com.imkolganov.datagate.model.auth.TotpSetupDto
import com.imkolganov.datagate.model.auth.TotpStatusDto
import com.imkolganov.datagate.model.auth.TotpVerifyLoginRequestDto

interface BackendAuthApi {
    suspend fun googleLogin(request: GoogleLoginRequestDto): LoginResponseDto
    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto

    suspend fun register(request: RegisterUserRequestDto): RegisterUserResponseDto

    suspend fun loginWithPassword(request: LoginPasswordRequestDto): LoginResponseDto

    /** No Authorization header. */
    suspend fun totpVerifyLogin(request: TotpVerifyLoginRequestDto): LoginResponseDto

    suspend fun totpStatus(accessToken: String): TotpStatusDto

    suspend fun totpSetup(accessToken: String): TotpSetupDto

    suspend fun totpConfirm(accessToken: String, request: TotpConfirmRequestDto)

    suspend fun totpDisable(accessToken: String, request: TotpDisableRequestDto)

    /** Backend always returns a generic success line (anti-enumeration). */
    suspend fun requestEmailConfirmation(email: String): String

    suspend fun confirmEmail(email: String, code: String): ConfirmEmailResultDto

    suspend fun forgotPassword(loginOrEmail: String): String

    suspend fun resetPassword(code: String, newPassword: String, confirmPassword: String): ResetPasswordResultDto
}
