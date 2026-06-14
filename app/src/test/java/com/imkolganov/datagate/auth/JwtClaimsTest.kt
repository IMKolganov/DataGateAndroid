package com.imkolganov.datagate.auth

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

    private fun jwt(payload: String): String =
        "eyJhbGciOiJub25lIiwidHlwIjoiSldU" + ".$payload.signature"

    private fun assertEqualsIgnoreCase(expected: String, actual: String?) {
        org.junit.Assert.assertTrue(
            "Expected '$expected' but was '$actual'",
            expected.equals(actual, ignoreCase = true)
        )
    }
}
