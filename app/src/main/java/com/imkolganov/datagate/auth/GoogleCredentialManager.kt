package com.imkolganov.datagate.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.imkolganov.datagate.configs.AuthConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleCredentialManager {
    private const val TAG = "GoogleAuth"

    suspend fun getGoogleIdTokenOrThrow(activity: Activity): String {
        val credentialManager = CredentialManager.create(activity)

        val option = GetSignInWithGoogleOption.Builder(
            serverClientId = AuthConfig.WEB_CLIENT_ID
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            return extractGoogleIdTokenOrThrow(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled sign-in", e)
            throw IllegalStateException("Sign-in cancelled")
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No credential available", e)
            throw IllegalStateException("No Google account available on device")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error", e)
            throw IllegalStateException("Credential error: ${e.javaClass.simpleName}")
        }
    }

    suspend fun tryGetGoogleIdTokenSilently(activity: Activity): String? {
        val credentialManager = CredentialManager.create(activity)

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(AuthConfig.WEB_CLIENT_ID)
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
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val token = googleCred.idToken
            if (token.isBlank()) {
                throw IllegalStateException("Google returned empty ID token")
            }
            return token
        }

        throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
    }
}
