package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.GoogleLoginResponseDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto

interface BackendAuthApi {
    suspend fun googleLogin(request: GoogleLoginRequestDto): GoogleLoginResponseDto
    suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto
}
