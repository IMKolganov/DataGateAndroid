package com.imkolganov.datagate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.login.LoginScreen
import com.imkolganov.datagate.ui.screens.main.MainScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.vpn.VpnStatusUiState

@Composable
fun AppRoot(
    authViewModel: AuthViewModel,
    tokenStore: TokenStore,
    vpnState: VpnStatusUiState,
    onRequestConnect: () -> Unit,
    onRequestDisconnect: () -> Unit,
    authVersion: Int,
    onAuthChanged: () -> Unit,
    accessViewModel: AccessViewModel,
    statsViewModel: StatsViewModel
) {
    val authState by authViewModel.state.collectAsState()

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            onAuthChanged()
        }
    }

    val isLoggedIn = remember(authVersion) {
        !tokenStore.getAccessToken().isNullOrBlank()
    }

    if (isLoggedIn) {
        MainScreen(
            vpnState = vpnState,
            onRequestConnect = onRequestConnect,
            onRequestDisconnect = onRequestDisconnect,
            onLogout = {
                authViewModel.logout()
                onAuthChanged()
            },
            tokenStore = tokenStore,
            accessViewModel = accessViewModel,
            statsViewModel = statsViewModel
        )
    } else {
        LoginScreen(viewModel = authViewModel)
    }
}
