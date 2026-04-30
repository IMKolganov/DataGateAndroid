package com.imkolganov.datagate.auth

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.configs.AuthConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.Locale

object GoogleCredentialManager {
    private const val TAG = "GoogleAuth"

    suspend fun getGoogleIdTokenOrThrow(activity: Activity): String {
        val credentialManager = CredentialManager.create(activity)
        val webClientId = AuthConfig.WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            throw IllegalStateException("Google sign-in is not configured: WEB_CLIENT_ID is empty.")
        }
        Log.d(
            TAG,
            "Google sign-in config: applicationId=${BuildConfig.APPLICATION_ID}, " +
                "runtimePackage=${activity.packageName}, buildType=${BuildConfig.BUILD_TYPE}, " +
                "flavor=${BuildConfig.FLAVOR}, webClientId=${maskClientId(webClientId)}"
        )
        Log.d(TAG, "Installed signing fingerprints: ${signingFingerprints(activity)}")

        try {
            return requestSignInWithGoogleToken(credentialManager, activity, webClientId)
        } catch (e: GetCredentialCancellationException) {
            val detail = describeCredentialException(e)
            Log.w(TAG, "Credential Manager cancelled or failed sign-in: $detail", e)
            if (isAccountReauthFailure(e)) {
                return recoverFromAccountReauthFailure(
                    credentialManager = credentialManager,
                    activity = activity,
                    webClientId = webClientId,
                    primaryDetail = detail
                )
            }
            throw IllegalStateException(
                "Google sign-in was cancelled or blocked by Credential Manager. $detail",
                e
            )
        } catch (e: NoCredentialException) {
            val detail = describeCredentialException(e)
            Log.w(TAG, "No Google credential available: $detail", e)
            throw IllegalStateException(
                "No Google credential is available. Add a Google account, update Google Play services, or choose an account. $detail",
                e
            )
        } catch (e: GetCredentialProviderConfigurationException) {
            val detail = describeCredentialException(e)
            Log.e(TAG, "Credential provider configuration error: $detail", e)
            throw IllegalStateException(
                "Google sign-in provider is not configured or is unavailable. Check Google Play services and OAuth client configuration. $detail",
                e
            )
        } catch (e: GetCredentialUnsupportedException) {
            val detail = describeCredentialException(e)
            Log.e(TAG, "Credential Manager is unsupported: $detail", e)
            throw IllegalStateException(
                "Google sign-in is not supported on this device or Credential Manager is disabled. $detail",
                e
            )
        } catch (e: GetCredentialException) {
            val detail = describeCredentialException(e)
            Log.e(TAG, "Credential Manager error: $detail", e)
            throw IllegalStateException("Google Credential Manager failed. $detail", e)
        }
    }

    private suspend fun requestSignInWithGoogleToken(
        credentialManager: CredentialManager,
        activity: Activity,
        webClientId: String
    ): String {
        val option = GetSignInWithGoogleOption.Builder(
            serverClientId = webClientId
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
банк
        val result = credentialManager.getCredential(
            request = request,
            context = activity
        )
        return extractGoogleIdTokenOrThrow(result.credential)
    }

    private suspend fun requestGoogleIdTokenWithAccountPicker(
        credentialManager: CredentialManager,
        activity: Activity,
        webClientId: String
    ): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = activity
        )
        return extractGoogleIdTokenOrThrow(result.credential)
    }

    private suspend fun recoverFromAccountReauthFailure(
        credentialManager: CredentialManager,
        activity: Activity,
        webClientId: String,
        primaryDetail: String
    ): String {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.w(TAG, "Cleared Credential Manager state before Google account picker fallback")
        } catch (e: ClearCredentialException) {
            Log.w(
                TAG,
                "Could not clear Credential Manager state before fallback: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }

        return try {
            Log.w(TAG, "Retrying Google sign-in with account picker fallback after reauth failure")
            requestGoogleIdTokenWithAccountPicker(credentialManager, activity, webClientId)
        } catch (fallback: GetCredentialException) {
            val fallbackDetail = describeCredentialException(fallback)
            Log.e(TAG, "Google account picker fallback failed: $fallbackDetail", fallback)
            throw IllegalStateException(
                "Google account reauth failed, and account picker fallback also failed. " +
                    "Primary: $primaryDetail. Fallback: $fallbackDetail. " +
                    "Verify Google Play services, the Google account state on this device, " +
                    "and the Android OAuth client for package ${activity.packageName} with signing fingerprints ${signingFingerprints(activity)}.",
                fallback
            )
        }
    }

    suspend fun tryGetGoogleIdTokenSilently(activity: Activity): String? {
        val credentialManager = CredentialManager.create(activity)

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(AuthConfig.WEB_CLIENT_ID.trim())
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            extractGoogleIdTokenOrThrow(result.credential)
        } catch (e: NoCredentialException) {
            null
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractGoogleIdTokenOrThrow(credential: Any): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Google returned a credential, but the ID token could not be parsed: ${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    e
                )
            }
            val token = googleCred.idToken
            if (token.isBlank()) {
                throw IllegalStateException("Google returned empty ID token")
            }
            return token
        }

        throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
    }

    private fun describeCredentialException(e: GetCredentialException): String {
        val message = e.message?.trim().orEmpty()
        val suffix = if (message.isBlank()) "" else ", message=$message"
        return "class=${e.javaClass.simpleName}, type=${e.type}$suffix"
    }

    private fun isAccountReauthFailure(e: GetCredentialException): Boolean {
        val message = "${e.type} ${e.message.orEmpty()}"
        return message.contains("Account reauth failed", ignoreCase = true) ||
            message.contains("[16]") ||
            message.contains("TYPE_NO_CREDENTIAL", ignoreCase = true)
    }

    private fun maskClientId(clientId: String): String {
        if (clientId.length <= 16) return "<set:${clientId.length}>"
        return "${clientId.take(6)}...${clientId.takeLast(10)}"
    }

    @Suppress("DEPRECATION")
    private fun signingFingerprints(activity: Activity): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.packageManager.getPackageInfo(
                    activity.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                activity.packageManager.getPackageInfo(
                    activity.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                packageInfo.signatures.orEmpty()
            }

            if (signatures.isEmpty()) {
                "<none>"
            } else {
                signatures.joinToString(prefix = "[", postfix = "]") { signature ->
                    "SHA-1=${fingerprint(signature.toByteArray(), "SHA-1")}, " +
                        "SHA-256=${fingerprint(signature.toByteArray(), "SHA-256")}"
                }
            }
        } catch (e: Throwable) {
            "<unavailable:${e.javaClass.simpleName}>"
        }
    }

    private fun fingerprint(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { byte ->
            "%02X".format(Locale.US, byte)
        }
    }
}
