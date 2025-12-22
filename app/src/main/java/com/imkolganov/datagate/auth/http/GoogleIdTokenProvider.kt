package com.imkolganov.datagate.auth.http

interface GoogleIdTokenProvider {
    suspend fun getIdTokenOrNull(): String?
}
