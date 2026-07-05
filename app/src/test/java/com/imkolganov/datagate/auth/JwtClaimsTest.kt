package com.imkolganov.datagate.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class JwtClaimsTest {

    @Test
    fun isAdmin_prefersAdminWhenRoleClaimIsJsonArray() {
        val token = jwt(
            payload = "eyJyb2xlIjpbIlVzZXIiLCJBZG1pbiJdLCJuYW1laWQiOiJ1c2VyLTEifQ"
        )
        assertTrue(JwtClaimsReader.isAdmin(token))
        assertEqualsIgnoreCase("Admin", JwtClaimsReader.read(token).role)
    }

    @Test
    fun isAdmin_falseForUserOnlyArray() {
        val token = jwt(
            payload = "eyJyb2xlIjpbIlVzZXIiXSwibmFtZWlkIjoidXNlci0xIn0"
        )
        assertFalse(JwtClaimsReader.isAdmin(token))
        assertEqualsIgnoreCase("User", JwtClaimsReader.read(token).role)
    }

    @Test
    fun isAdmin_trueForStringRoleClaim() {
        val token = jwt(
            payload = "eyJyb2xlIjoiQWRtaW4iLCJuYW1laWQiOiJ1c2VyLTEifQ"
        )
        assertTrue(JwtClaimsReader.isAdmin(token))
    }

    @Test
    fun read_prefersExternalIdClaim_forVpnIdentity() {
        val token = jwt(
            payload = "eyJleHRlcm5hbElkIjoiYWNjb3VudHMuZ29vZ2xlLmNvbTpzdWIxMjMiLCJuYW1laWQiOiI3In0"
        )

        val claims = JwtClaimsReader.read(token)

        assertEquals("accounts.google.com:sub123", claims.externalId)
        assertEquals("7", claims.userId)
    }

    @Test
    fun read_fallsBackToSubWhenExternalIdMissing() {
        val token = jwt(
            payload = "eyJzdWIiOiJmYWxsYmFjay1zdWIiLCJuYW1laWQiOiI4In0"
        )

        val claims = JwtClaimsReader.read(token)

        assertEquals("fallback-sub", claims.externalId)
    }

    @Test
    fun read_returnsNullExternalIdForBlankToken() {
        val claims = JwtClaimsReader.read(null)

        assertEquals(null, claims.externalId)
    }

    private fun jwt(payload: String): String =
        "eyJhbGciOiJub25lIiwidHlwIjoiSldU" + ".$payload.signature"

    private fun assertEqualsIgnoreCase(expected: String, actual: String?) {
        org.junit.Assert.assertTrue(
            "Expected '$expected' but was '$actual'",
            expected.equals(actual, ignoreCase = true)
        )
    }
}
