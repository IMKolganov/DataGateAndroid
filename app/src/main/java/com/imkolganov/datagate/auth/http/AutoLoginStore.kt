package com.imkolganov.datagate.auth

interface AutoLoginStore {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
