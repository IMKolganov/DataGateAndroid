package com.imkolganov.datagate.util

import android.app.Application
import android.content.res.Resources
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.imkolganov.datagate.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApiErrorHumanizerTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Application>().resources

    // --- Throwable-type based classification (Resources.userFriendlyApiError(Throwable)) ---

    @Test
    fun unknownHostException_mapsToNoInternet() {
        val msg = resources.userFriendlyApiError(UnknownHostException("api.example.com"))
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    @Test
    fun socketTimeoutException_mapsToTimeout() {
        val msg = resources.userFriendlyApiError(SocketTimeoutException("timeout"))
        assertEquals(resources.getString(R.string.error_network_timeout), msg)
    }

    @Test
    fun connectException_mapsToNoInternet() {
        val msg = resources.userFriendlyApiError(ConnectException("Failed to connect to /1.2.3.4 (port 443)"))
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    @Test
    fun noRouteToHostException_mapsToNoInternet() {
        val msg = resources.userFriendlyApiError(NoRouteToHostException("No route to host"))
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    @Test
    fun portUnreachableException_mapsToNoInternet() {
        val msg = resources.userFriendlyApiError(PortUnreachableException("Port unreachable"))
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    @Test
    fun sslHandshakeException_mapsToTls() {
        val msg = resources.userFriendlyApiError(SSLHandshakeException("handshake failed"))
        assertEquals(resources.getString(R.string.error_network_tls), msg)
    }

    @Test
    fun socketException_mapsToInterrupted() {
        val msg = resources.userFriendlyApiError(SocketException("Socket closed"))
        assertEquals(resources.getString(R.string.error_network_interrupted), msg)
    }

    @Test
    fun socketException_softwareCausedConnectionAbort_mapsToInterrupted() {
        val msg = resources.userFriendlyApiError(SocketException("Software caused connection abort"))
        assertEquals(resources.getString(R.string.error_network_interrupted), msg)
    }

    @Test
    fun networkExceptionWrappedInOtherThrowable_isFoundByWalkingCauseChain() {
        val wrapped = RuntimeException("Free tier status fetch failed", SocketException("Socket closed"))
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_network_interrupted), msg)
    }

    @Test
    fun deeplyNestedNetworkException_isFoundByWalkingCauseChain() {
        val level3 = UnknownHostException("api.example.com")
        val level2 = IOException("Unable to resolve host", level3)
        val level1 = RuntimeException("API call failed", level2)
        val msg = resources.userFriendlyApiError(level1)
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    // --- Google Credential Manager (Sign-in with Google) classification ---
    // GoogleCredentialManager.getGoogleIdTokenOrThrow always wraps the androidx.credentials
    // exception it caught as the `cause` of an IllegalStateException with a technical
    // "class=..., type=..., message=..." string — these tests mirror that shape.

    @Test
    fun googleCredentialCancellation_mapsToFriendlySignInCancelled() {
        val wrapped = IllegalStateException(
            "Google sign-in was cancelled or blocked by Credential Manager. " +
                "class=GetCredentialCancellationException, type=TYPE_USER_CANCELED, message=User cancelled the selector",
            GetCredentialCancellationException("User cancelled the selector")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_signin_cancelled), msg)
    }

    @Test
    fun noCredentialException_mapsToNoAccountFound() {
        val wrapped = IllegalStateException(
            "No Google credential is available. Add a Google account, update Google Play services, or choose an account.",
            NoCredentialException("No credentials available")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_no_account), msg)
    }

    @Test
    fun getCredentialProviderConfigurationException_mapsToSignInConfigError() {
        val wrapped = IllegalStateException(
            "Google sign-in provider is not configured or is unavailable.",
            GetCredentialProviderConfigurationException("Provider misconfigured")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_signin_config), msg)
    }

    @Test
    fun getCredentialUnsupportedException_mapsToSignInUnsupported() {
        val wrapped = IllegalStateException(
            "Google sign-in is not supported on this device or Credential Manager is disabled.",
            GetCredentialUnsupportedException("Unsupported")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_signin_unsupported), msg)
    }

    @Test
    fun otherGetCredentialException_mapsToGenericSignInFailed() {
        val wrapped = IllegalStateException(
            "Google Credential Manager failed.",
            GetCredentialUnknownException("Unknown failure")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_signin_failed), msg)
    }

    @Test
    fun googleReauthFallbackFailure_stillTakesPriorityOverGenericCancellationType() {
        // The wrapper message names both flows explicitly; even though its `cause` is a plain
        // GetCredentialCancellationException, the richer, more actionable reauth-fallback message
        // must win over the generic "sign-in cancelled" classification.
        val wrapped = IllegalStateException(
            "Google account reauth failed, and account picker fallback also failed. " +
                "Primary: class=GetCredentialCancellationException, type=[16]. " +
                "Fallback: class=GetCredentialCancellationException, type=TYPE_USER_CANCELED.",
            GetCredentialCancellationException("User cancelled the selector")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_account_reauth_fallback_failed), msg)
    }

    @Test
    fun googleReauthFailure_stillTakesPriorityOverGenericCancellationType() {
        val wrapped = IllegalStateException(
            "Google sign-in was cancelled or blocked by Credential Manager. " +
                "class=GetCredentialCancellationException, type=[16], message=Account reauth failed",
            GetCredentialCancellationException("Account reauth failed")
        )
        val msg = resources.userFriendlyApiError(wrapped)
        assertEquals(resources.getString(R.string.error_google_account_reauth_failed), msg)
    }

    @Test
    fun nonNetworkException_fallsBackToMessageBasedHumanizer() {
        val msg = resources.userFriendlyApiError(IllegalStateException("Something odd happened"))
        assertEquals("Something odd happened", msg)
    }

    @Test
    fun nullMessageException_fallsBackToGenericRequestFailed() {
        val msg = resources.userFriendlyApiError(IllegalStateException())
        assertEquals(resources.getString(R.string.error_request_failed), msg)
    }

    // --- Message-string based classification (Resources.userFriendlyApiError(String?)) ---
    // Exercised for callers that only have a message (e.g. from a data layer) and not the
    // original Throwable.

    @Test
    fun rawMessage_socketClosed_mapsToInterrupted() {
        val msg = resources.userFriendlyApiError("java.net.SocketException: Socket closed")
        assertEquals(resources.getString(R.string.error_network_interrupted), msg)
    }

    @Test
    fun rawMessage_connectionReset_mapsToInterrupted() {
        val msg = resources.userFriendlyApiError("Connection reset by peer")
        assertEquals(resources.getString(R.string.error_network_interrupted), msg)
    }

    @Test
    fun rawMessage_unableToResolveHost_mapsToNoInternet() {
        val msg = resources.userFriendlyApiError("Unable to resolve host \"api.example.com\": No address associated with hostname")
        assertEquals(resources.getString(R.string.error_network_no_internet), msg)
    }

    @Test
    fun rawMessage_timeout_mapsToTimeout() {
        val msg = resources.userFriendlyApiError("failed to connect to api.example.com after 10000ms: connect timed out")
        assertEquals(resources.getString(R.string.error_network_timeout), msg)
    }

    @Test
    fun rawMessage_sslCertificate_mapsToTls() {
        val msg = resources.userFriendlyApiError("Trust anchor for certification path not found")
        assertEquals(resources.getString(R.string.error_network_tls), msg)
    }

    @Test
    fun rawMessage_nullOrBlank_fallsBackToGenericRequestFailed() {
        assertEquals(resources.getString(R.string.error_request_failed), resources.userFriendlyApiError(null as String?))
        assertEquals(resources.getString(R.string.error_request_failed), resources.userFriendlyApiError(""))
        assertEquals(resources.getString(R.string.error_request_failed), resources.userFriendlyApiError("   "))
    }

    // --- Pre-existing HTTP-status / Google reauth classification must still take priority ---

    @Test
    fun rawMessage_badGatewayHtml_stillClassifiedAsBadGateway_notNetworkError() {
        val msg = resources.userFriendlyApiError("<html><head><title>502 Bad Gateway</title></head><body>bad gateway</body></html>")
        assertEquals(resources.getString(R.string.error_http_bad_gateway), msg)
    }

    @Test
    fun rawMessage_googleReauthFallback_stillClassifiedCorrectly() {
        val msg = resources.userFriendlyApiError("Account picker fallback also failed")
        assertEquals(resources.getString(R.string.error_google_account_reauth_fallback_failed), msg)
    }

    // --- deepMessageForApiError ---

    @Test
    fun deepMessageForApiError_picksLongestMessageInCauseChain() {
        val cause = IOException("A short root cause")
        val wrapper = RuntimeException("A much longer, more descriptive wrapper message with detail", cause)
        assertEquals(
            "A much longer, more descriptive wrapper message with detail",
            wrapper.deepMessageForApiError()
        )
    }

    @Test
    fun deepMessageForApiError_returnsEmptyStringWhenNoMessageAnywhere() {
        assertEquals("", RuntimeException(null as String?).deepMessageForApiError())
    }
}
