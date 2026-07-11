package com.imkolganov.datagate.auth

import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AuthRepositorySessionTest {

    @Test
    fun tryRestoreSession_noTokens_returnsFalse() = runBlocking {
        val repo = authRepo(FakeTokenStore())
        assertFalse(repo.tryRestoreSession())
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun tryRestoreSession_validAccessToken_returnsTrueWithoutApiCall() = runBlocking {
        val store = FakeTokenStore(access = "access-1", refresh = "refresh-1")
        val api = RecordingAuthApi()
        val repo = authRepo(store, api)

        assertTrue(repo.tryRestoreSession())
        assertTrue(repo.isLoggedIn())
        assertEquals(0, api.refreshCalls)
    }

    @Test
    fun tryRestoreSession_onlyRefreshToken_refreshesAndPersists() = runBlocking {
        val store = FakeTokenStore(refresh = "refresh-old")
        val api = RecordingAuthApi(
            refreshResponse = RefreshResponseDto(
                token = "access-new",
                expiration = "2026-12-31T00:00:00Z",
                refreshToken = "refresh-new",
                refreshExpiration = "2027-01-01T00:00:00Z",
            )
        )
        val repo = authRepo(store, api)

        assertTrue(repo.tryRestoreSession())
        assertEquals("access-new", store.getAccessToken())
        assertEquals("refresh-new", store.getRefreshToken())
        assertTrue(repo.isLoggedIn())
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun tryRestoreSession_refreshFails_clearsTokens() = runBlocking {
        val store = FakeTokenStore(refresh = "refresh-stale")
        val api = RecordingAuthApi(refreshThrows = true)
        val autoLogin = FakeAutoLoginStore(autoLoginEnabled = true)
        val repo = AuthRepository(api, store, autoLogin)

        assertFalse(repo.tryRestoreSession())
        assertNull(store.getAccessToken())
        assertNull(store.getRefreshToken())
        assertFalse(autoLogin.isEnabled())
    }

    @Test
    fun logout_clearsSession() {
        val store = FakeTokenStore(access = "a", refresh = "r")
        val autoLogin = FakeAutoLoginStore(autoLoginEnabled = true)
        val repo = authRepo(store, autoLogin = autoLogin)

        repo.logout()

        assertNull(store.getAccessToken())
        assertFalse(autoLogin.isEnabled())
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun firstLaunchScenario_noStoredTokens_userNotLoggedIn() = runBlocking {
        val repo = authRepo(FakeTokenStore())
        val restored = repo.tryRestoreSession()
        assertFalse(restored)
        assertFalse(repo.isLoggedIn())
    }

    private fun authRepo(
        store: FakeTokenStore,
        api: RecordingAuthApi = RecordingAuthApi(),
        autoLogin: FakeAutoLoginStore = FakeAutoLoginStore(),
    ) = AuthRepository(api, store, autoLogin)
}

class LegacyAuthMigrationTest {
    @Test
    fun evaluate_freshInstallWithRestoredBackup_clearsSession() {
        val decision = LegacyAuthMigration.evaluate(
            LegacyAuthMigration.Input(
                migrationAlreadyDone = false,
                isAppUpdate = false,
                isFreshInstall = true,
                hasLegacySessionData = true,
            )
        )
        assertTrue(decision.shouldClearSession)
        assertTrue(decision.shouldMarkMigrationDone)
    }

    @Test
    fun evaluate_freshInstallNoTokens_doesNotClear() {
        val decision = LegacyAuthMigration.evaluate(
            LegacyAuthMigration.Input(
                migrationAlreadyDone = false,
                isAppUpdate = false,
                isFreshInstall = true,
                hasLegacySessionData = false,
            )
        )
        assertFalse(decision.shouldClearSession)
        assertTrue(decision.shouldMarkMigrationDone)
    }

    @Test
    fun evaluate_alreadyMigrated_isNoOp() {
        val decision = LegacyAuthMigration.evaluate(
            LegacyAuthMigration.Input(
                migrationAlreadyDone = true,
                isAppUpdate = true,
                isFreshInstall = false,
                hasLegacySessionData = true,
            )
        )
        assertFalse(decision.shouldClearSession)
        assertFalse(decision.shouldMarkMigrationDone)
    }

    @Test
    fun shouldClearSessionOnFreshInstallWithRestoredBackup_detectsTokens() {
        assertTrue(
            LegacyAuthMigration.shouldClearSessionOnFreshInstallWithRestoredBackup(
                isFreshInstall = true,
                hasAccessToken = true,
                hasRefreshToken = false,
            )
        )
        assertFalse(
            LegacyAuthMigration.shouldClearSessionOnFreshInstallWithRestoredBackup(
                isFreshInstall = false,
                hasAccessToken = true,
                hasRefreshToken = true,
            )
        )
    }
}

private class FakeTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
) : TokenStore {
    override fun getAccessToken(): String? = access
    override fun saveAccessToken(token: String) { access = token }
    override fun getRefreshToken(): String? = refresh
    override fun saveRefreshToken(token: String) { refresh = token }
    override fun saveAccessTokenExpiration(value: String) = Unit
    override fun saveRefreshTokenExpiration(value: String?) = Unit
    override fun clear() {
        access = null
        refresh = null
    }
}

private class FakeAutoLoginStore(private var autoLoginEnabled: Boolean = false) : AutoLoginStore {
    override fun isEnabled(): Boolean = autoLoginEnabled
    override fun setEnabled(enabled: Boolean) { autoLoginEnabled = enabled }
}

private class RecordingAuthApi(
    val refreshResponse: RefreshResponseDto = RefreshResponseDto(
        token = "access",
        expiration = "2026-01-01T00:00:00Z",
        refreshToken = "refresh",
        refreshExpiration = "2027-01-01T00:00:00Z",
    ),
    private val refreshThrows: Boolean = false,
) : com.imkolganov.datagate.auth.http.BackendAuthApi {
    var refreshCalls = 0

    override suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto {
        refreshCalls++
        if (refreshThrows) throw IllegalStateException("refresh failed")
        return refreshResponse
    }

    override suspend fun googleLogin(request: com.imkolganov.datagate.model.auth.GoogleLoginRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun loginWithPassword(request: com.imkolganov.datagate.model.auth.LoginPasswordRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun totpVerifyLogin(request: com.imkolganov.datagate.model.auth.TotpVerifyLoginRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun totpStatus(accessToken: String) = throw UnsupportedOperationException()
    override suspend fun totpSetup(accessToken: String) = throw UnsupportedOperationException()
    override suspend fun totpConfirm(accessToken: String, request: com.imkolganov.datagate.model.auth.TotpConfirmRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun totpDisable(accessToken: String, request: com.imkolganov.datagate.model.auth.TotpDisableRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun register(request: com.imkolganov.datagate.model.auth.RegisterUserRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun requestEmailConfirmation(email: String) = throw UnsupportedOperationException()
    override suspend fun confirmEmail(email: String, code: String) = throw UnsupportedOperationException()
    override suspend fun forgotPassword(loginOrEmail: String) = throw UnsupportedOperationException()
    override suspend fun resetPassword(code: String, newPassword: String, confirmPassword: String) =
        throw UnsupportedOperationException()
}
