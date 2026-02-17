package com.imkolganov.datagate.auth

interface IdTokenStore {
    fun getIdToken(): String?
    fun saveIdToken(idToken: String)
    fun clear()
}
