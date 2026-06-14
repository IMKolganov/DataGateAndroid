package com.imkolganov.datagate.auth

import com.imkolganov.datagate.model.auth.LoginResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFlowTest {

    @Test
    fun resolveLoginFlow_totpChallenge_withoutTokens() {
        val flow = resolveLoginFlow(
            LoginResponseDto(
                requiresTotp = true,
                loginChallengeId = "challenge-1",
                displayName = "Admin User",
            )
        )
        assertTrue(flow is ResolvedLoginFlow.TotpChallenge)
        val c = flow as ResolvedLoginFlow.TotpChallenge
        assertEquals("challenge-1", c.loginChallengeId)
        assertEquals("Admin User", c.displayName)
    }

    @Test
    fun resolveLoginFlow_tokens_withSetupFlag() {
        val flow = resolveLoginFlow(
            LoginResponseDto(
                token = "access",
                expiration = "2026-01-01T00:00:00Z",
                refreshToken = "refresh",
                requiresTotpSetup = true,
            )
        )
        assertTrue(flow is ResolvedLoginFlow.Tokens)
        val t = flow as ResolvedLoginFlow.Tokens
        assertTrue(t.requiresTotpSetup)
        assertEquals("access", t.response.token)
    }

    @Test
    fun isLoginChallengeExpiredMessage_matchesApiPhrases() {
        assertTrue(isLoginChallengeExpiredMessage("Login challenge expired"))
        assertTrue(isLoginChallengeExpiredMessage("Too many invalid attempts"))
    }
}
