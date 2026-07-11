package com.imkolganov.datagate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.auth.AdminTotpGate
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.ui.screens.auth.AdminTotpSetupScreen
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.login.LoginScreen
import com.imkolganov.datagate.ui.screens.main.MainScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.update.UpdateCheckHost
import com.imkolganov.datagate.update.UpdatePreferences
import com.imkolganov.datagate.update.UpdatePromptController
import com.imkolganov.datagate.freetier.FreeTierApi
import com.imkolganov.datagate.ui.freetier.FreeTierOnboardingHost
import com.imkolganov.datagate.vpn.VpnStatusUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun AppRoot(
    authViewModel: AuthViewModel,
    tokenStore: TokenStore,
    vpnState: VpnStatusUiState,
    onConnectFromHome: () -> Unit,
    onConnectFromAccess: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestDisconnect: () -> Unit,
    onRequestPause: () -> Unit = {},
    onRequestResume: () -> Unit = {},
    onReconnectVpn: () -> Unit,
    authVersion: Int,
    onAuthChanged: () -> Unit,
    accessViewModel: AccessViewModel,
    statsViewModel: StatsViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
    http: OkHttpClient,
    freeTierApi: FreeTierApi,
    openUpdateFromNotificationPending: Boolean,
    onConsumedOpenUpdateFromNotification: () -> Unit,
) {
    val authState by authViewModel.state.collectAsState()
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            onAuthChanged()
        }
    }

    val isLoggedIn = authState.isLoggedIn

    LaunchedEffect(openUpdateFromNotificationPending, isLoggedIn) {
        if (!openUpdateFromNotificationPending) return@LaunchedEffect
        onConsumedOpenUpdateFromNotification()
        if (!isLoggedIn) return@LaunchedEffect
        delay(400)
        val rel = withContext(Dispatchers.IO) {
            UpdatePreferences.getCachedNewerRelease(appContext, BuildConfig.VERSION_NAME)
        }
        rel?.let { UpdatePromptController.requestUpdateDialog(it) }
    }

    if (authState.isLoading && !isLoggedIn) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (isLoggedIn) {
        when (authState.adminTotpGate) {
            AdminTotpGate.Unknown -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return
            }
            AdminTotpGate.SetupRequired -> {
                AdminTotpSetupScreen(
                    isLoading = authState.isLoading,
                    totpSetupConfirmLoading = authState.totpSetupConfirmLoading,
                    setup = authState.totpSetup,
                    errorMessage = authState.errorMessage,
                    infoMessage = authState.infoMessage,
                    onBeginSetup = { authViewModel.beginAdminTotpSetup(appContext.resources) },
                    onCancelSetup = { authViewModel.cancelAdminTotpSetup() },
                    onConfirm = { authViewModel.confirmAdminTotpSetup(appContext.resources, it) },
                    onLogout = {
                        performLogout(vpnState, onRequestDisconnect, authViewModel::logout, onAuthChanged)
                    },
                )
                return
            }
            AdminTotpGate.Allowed -> Unit
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                vpnState = vpnState,
                onConnectFromHome = onConnectFromHome,
                onConnectFromAccess = onConnectFromAccess,
                onRequestVpnPermission = onRequestVpnPermission,
                onRequestDisconnect = onRequestDisconnect,
                onRequestPause = onRequestPause,
                onRequestResume = onRequestResume,
                onReconnectVpn = onReconnectVpn,
                onLogout = {
                    performLogout(vpnState, onRequestDisconnect, authViewModel::logout, onAuthChanged)
                },
                authViewModel = authViewModel,
                tokenStore = tokenStore,
                accessViewModel = accessViewModel,
                statsViewModel = statsViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                appLocale = appLocale,
                onAppLocaleChange = onAppLocaleChange
            )
            UpdateCheckHost(isLoggedIn = true, http = http)
            FreeTierOnboardingHost(
                adminTotpGate = authState.adminTotpGate,
                freeTierApi = freeTierApi,
                isVpnConnected = vpnState.isVpnConnected,
            )
        }
    } else {
        LoginScreen(
            viewModel = authViewModel,
            appLocale = appLocale,
            onAppLocaleChange = onAppLocaleChange,
        )
    }
}
