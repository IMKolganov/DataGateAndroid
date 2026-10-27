package com.imkolganov.datagate.auth.http

import java.io.IOException

/**
 * The refresh endpoint explicitly refused the refresh token (revoked, expired, unknown).
 * Only this means the session is gone; timeouts, DNS failures and 5xx keep the tokens.
 */
class RefreshRejectedException(val code: Int, message: String) : IOException(message) {
    companion object {
        fun isRejectionCode(code: Int): Boolean = code == 400 || code == 401 || code == 403
    }
}
