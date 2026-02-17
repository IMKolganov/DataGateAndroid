package com.imkolganov.datagate.auth

import android.content.Context
import androidx.core.content.edit

interface TokenStore {
    fun getAccessToken(): String?
    fun saveAccessToken(token: String)

    fun getRefreshToken(): String?
    fun saveRefreshToken(token: String)

    fun saveAccessTokenExpiration(value: String)
    fun saveRefreshTokenExpiration(value: String?)

    fun clear()
}