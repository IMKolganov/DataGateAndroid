package com.imkolganov.datagate.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.update.UpdatePreferences
import com.imkolganov.datagate.update.UpdatePromptController
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.stats.FakeStatsApiClient
import com.imkolganov.datagate.ui.screens.connect.VpnStatusScreen
import com.imkolganov.datagate.ui.screens.access.AccessContract
import com.imkolganov.datagate.ui.screens.access.AccessScreen
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.settings.SettingsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.DataGateAndroidTheme
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.vpn.VpnStatusUiState

enum class BottomTab {
    Home, Access, Statistics, Settings
}

@Composable
fun MainScreen(
    vpnState: VpnStatusUiState,
    onConnectFromHome: () -> Unit,
    onConnectFromAccess: () -> Unit,
    onRequestDisconnect: () -> Unit,
    onRequestPause: () -> Unit = {},
    onRequestResume: () -> Unit = {},
    onReconnectVpn: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel? = null,
    tokenStore: TokenStore,
    accessViewModel: AccessViewModel,
    statsViewModel: StatsViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bannerFlow = remember(context) {
        UpdatePreferences.homeBannerReleaseFlow(context, BuildConfig.VERSION_NAME)
    }
    val homeUpdateBanner by bannerFlow.collectAsState(initial = null)

    var selectedTabKey by rememberSaveable { mutableStateOf(BottomTab.Home.name) }
    val selectedTab = BottomTab.entries.find { it.name == selectedTabKey } ?: BottomTab.Home
    val accessState by accessViewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Home,
                    onClick = { selectedTabKey = BottomTab.Home.name },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_home)) },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Access,
                    onClick = { selectedTabKey = BottomTab.Access.name },
                    icon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.nav_access)) },
                    label = { Text(stringResource(R.string.nav_access)) }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Statistics,
                    onClick = { selectedTabKey = BottomTab.Statistics.name },
                    icon = {
                        Icon(
                            Icons.Default.AccountBox,
                            contentDescription = stringResource(R.string.nav_statistics)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_statistics)) }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.Settings,
                    onClick = { selectedTabKey = BottomTab.Settings.name },
                    icon = {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                BottomTab.Home -> VpnStatusScreen(
                    state = vpnState,
                    onConnectClick = onConnectFromHome,
                    onDisconnectClick = onRequestDisconnect,
                    onPauseClick = onRequestPause,
                    onResumeClick = onRequestResume,
                    homeUpdateBanner = homeUpdateBanner,
                    onHomeUpdateBannerAction = { release ->
                        UpdatePromptController.requestUpdateDialog(release)
                    },
                    onHomeUpdateBannerDismiss = { release ->
                        scope.launch {
                            UpdatePreferences.dismissRelease(context, release.tagName)
                        }
                    },
                )
                BottomTab.Access -> AccessScreen(
                    state = accessState,
                    vpnState = vpnState,
                    onEvent = accessViewModel::onEvent,
                    onConnectVpn = onConnectFromAccess,
                    onDisconnectVpn = onRequestDisconnect,
                    onPauseVpn = onRequestPause,
                    onResumeVpn = onRequestResume,
                    onReconnectVpn = onReconnectVpn
                )
                BottomTab.Statistics -> StatsScreen(
                    viewModel = statsViewModel
                )
                BottomTab.Settings -> SettingsScreen(
                    tokenStore = tokenStore,
                    authViewModel = authViewModel,
                    onLogout = onLogout,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    appLocale = appLocale,
                    onAppLocaleChange = onAppLocaleChange
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
        override suspend fun loadQuotaUi(): AccessContract.QuotaUiState = AccessContract.QuotaUiState()
    },
    appContext = appContext
)

private class PreviewStatsViewModel(
    app: android.app.Application
) : StatsViewModel(
    app,
    api = FakeStatsApiClient(),
    externalIdProvider = { "preview-external-id" }
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val previewVm = remember(context) {
        PreviewAccessViewModel(context.applicationContext)
    }
    val previewStats = remember(app) { PreviewStatsViewModel(app) }
    DataGateAndroidTheme {
        MainScreen(
            vpnState = VpnStatusUiState(),
            onConnectFromHome = {},
            onConnectFromAccess = {},
            onRequestDisconnect = {},
            onReconnectVpn = {},
            onLogout = {},
            authViewModel = null,
            tokenStore = PreviewTokenStore(),
            accessViewModel = previewVm,
            statsViewModel = previewStats,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            appLocale = AppLocale.SYSTEM,
            onAppLocaleChange = {}
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