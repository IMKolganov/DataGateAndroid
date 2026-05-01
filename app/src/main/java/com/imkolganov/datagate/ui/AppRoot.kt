package com.imkolganov.datagate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.login.LoginScreen
import com.imkolganov.datagate.ui.screens.main.MainScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.update.UpdateCheckHost
import com.imkolganov.datagate.update.UpdatePreferences
import com.imkolganov.datagate.update.UpdatePromptController
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
    onRequestDisconnect: () -> Unit,
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

    val isLoggedIn = remember(authVersion) {
        !tokenStore.getAccessToken().isNullOrBlank()
    }

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

    if (isLoggedIn) {
        Box(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                vpnState = vpnState,
                onConnectFromHome = onConnectFromHome,
                onConnectFromAccess = onConnectFromAccess,
                onRequestDisconnect = onRequestDisconnect,
                onReconnectVpn = onReconnectVpn,
                onLogout = {
                    authViewModel.logout()
                    onAuthChanged()
                },
                tokenStore = tokenStore,
                accessViewModel = accessViewModel,
                statsViewModel = statsViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                appLocale = appLocale,
                onAppLocaleChange = onAppLocaleChange
            )
            UpdateCheckHost(isLoggedIn = true, http = http)
        }
    } else {
        LoginScreen(
            viewModel = authViewModel,
            appLocale = appLocale,
            onAppLocaleChange = onAppLocaleChange,
        )
    }
}
