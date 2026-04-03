package com.imkolganov.datagate.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.stats.FakeStatsApiClient
import com.imkolganov.datagate.ui.screens.connect.VpnStatusScreen
import com.imkolganov.datagate.ui.screens.access.AccessContract
import com.imkolganov.datagate.ui.screens.access.AccessScreen
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.settings.SettingsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.theme.DataGateAndroidTheme
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.vpn.VpnStatusUiState

enum class BottomTab {
    Home, Access, Statistics, Settings
}

@Composable
fun MainScreen(
    vpnState: VpnStatusUiState,
    onRequestConnect: () -> Unit,
    onRequestDisconnect: () -> Unit,
    onReconnectVpn: () -> Unit,
    onLogout: () -> Unit,
    tokenStore: TokenStore,
    accessViewModel: AccessViewModel,
    statsViewModel: StatsViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomTab.Home) }
    val accessState by accessViewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Home,
                    onClick = { selectedTab = BottomTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Access,
                    onClick = { selectedTab = BottomTab.Access },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Access") },
                    label = { Text("Access") }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Statistics,
                    onClick = { selectedTab = BottomTab.Statistics },
                    icon = { Icon(Icons.Default.AccountBox, contentDescription = "Statistics") },
                    label = { Text("Statistics") }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Settings,
                    onClick = { selectedTab = BottomTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                BottomTab.Home -> VpnStatusScreen(
                    state = vpnState,
                    onConnectClick = onRequestConnect,
                    onDisconnectClick = onRequestDisconnect
                )
                BottomTab.Access -> AccessScreen(
                    state = accessState,
                    vpnState = vpnState,
                    onEvent = accessViewModel::onEvent,
                    onConnectVpn = onRequestConnect,
                    onDisconnectVpn = onRequestDisconnect,
                    onReconnectVpn = onReconnectVpn
                )
                BottomTab.Statistics -> StatsScreen(
                    viewModel = statsViewModel
                )
                BottomTab.Settings -> SettingsScreen(
                    tokenStore = tokenStore,
                    onLogout = onLogout,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }
}

private class PreviewAccessViewModel(
    appContext: android.content.Context
) : AccessViewModel(
    repo = object : com.imkolganov.datagate.ui.screens.access.AccessRepository {
        override suspend fun getServers(): List<AccessContract.ServerItem> = emptyList()
        override suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem> = emptyList()
    },
    appContext = appContext
)

private class PreviewStatsViewModel :
    StatsViewModel(
        api = FakeStatsApiClient(),
        externalIdProvider = { "preview-external-id" }
    )

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val previewVm = remember(context) {
        PreviewAccessViewModel(context.applicationContext)
    }
    DataGateAndroidTheme {
        MainScreen(
            vpnState = VpnStatusUiState(),
            onRequestConnect = {},
            onRequestDisconnect = {},
            onReconnectVpn = {},
            onLogout = {},
            tokenStore = PreviewTokenStore(),
            accessViewModel = previewVm,
            statsViewModel = PreviewStatsViewModel(),
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {}
        )
    }
}


private class PreviewTokenStore : TokenStore {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var accessExpiration: String? = null
    private var refreshExpiration: String? = null

    override fun getAccessToken(): String? = accessToken
    override fun saveAccessToken(token: String) { accessToken = token }

    override fun getRefreshToken(): String? = refreshToken
    override fun saveRefreshToken(token: String) { refreshToken = token }

    override fun saveAccessTokenExpiration(value: String) { accessExpiration = value }
    override fun saveRefreshTokenExpiration(value: String?) { refreshExpiration = value }

    override fun clear() {
        accessToken = null
        refreshToken = null
        accessExpiration = null
        refreshExpiration = null
    }
}