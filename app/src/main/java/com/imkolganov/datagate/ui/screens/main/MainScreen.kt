package com.imkolganov.datagate.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.freetier.FreeTierComplianceController
import com.imkolganov.datagate.stats.FakeStatsApiClient
import com.imkolganov.datagate.ui.screens.access.AccessContract
import com.imkolganov.datagate.ui.screens.access.AccessScreen
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.connect.VpnStatusScreen
import com.imkolganov.datagate.ui.screens.settings.SettingsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsScreen
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.DataGateAndroidTheme
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.ui.tv.LocalIsTelevision
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.update.UpdatePreferences
import com.imkolganov.datagate.update.UpdatePromptController
import com.imkolganov.datagate.vpn.VpnStatusUiState
import kotlinx.coroutines.launch

/** Main app tabs (bottom bar on phone, navigation rail on TV). */
enum class MainTab {
    Home, Access, Statistics, Settings
}

/** @deprecated Use [MainTab]; kept as typealias for any leftover call sites. */
typealias BottomTab = MainTab

private data class MainTabSpec(
    val tab: MainTab,
    val labelRes: Int,
    val icon: ImageVector,
)

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
    val isTelevision = LocalIsTelevision.current
    val bannerFlow = remember(context) {
        UpdatePreferences.homeBannerReleaseFlow(context, BuildConfig.VERSION_NAME)
    }
    val homeUpdateBanner by bannerFlow.collectAsState(initial = null)
    val graceExpiresAtUtcMs by FreeTierComplianceController.graceExpiresAtUtcMs.collectAsState()

    var selectedTabKey by rememberSaveable { mutableStateOf(MainTab.Home.name) }
    val selectedTab = MainTab.entries.find { it.name == selectedTabKey } ?: MainTab.Home
    val accessState by accessViewModel.state.collectAsState()

    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            MainTab.Home, MainTab.Access -> FreeTierComplianceController.requestStatusRefresh()
            else -> Unit
        }
        if (isTelevision) {
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    val tabs = listOf(
        MainTabSpec(MainTab.Home, R.string.nav_home, Icons.Default.Home),
        MainTabSpec(MainTab.Access, R.string.nav_access, Icons.Default.Lock),
        MainTabSpec(MainTab.Statistics, R.string.nav_statistics, Icons.Default.AccountBox),
        MainTabSpec(MainTab.Settings, R.string.nav_settings, Icons.Default.Settings),
    )

    @Composable
    fun TabContent() {
        when (selectedTab) {
            MainTab.Home -> VpnStatusScreen(
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
                graceExpiresAtUtcMs = graceExpiresAtUtcMs,
                primaryFocusRequester = if (isTelevision) contentFocusRequester else null,
            )
            MainTab.Access -> AccessScreen(
                state = accessState,
                vpnState = vpnState,
                onEvent = accessViewModel::onEvent,
                onConnectVpn = onConnectFromAccess,
                onDisconnectVpn = onRequestDisconnect,
                onPauseVpn = onRequestPause,
                onResumeVpn = onRequestResume,
                onReconnectVpn = onReconnectVpn,
                primaryFocusRequester = if (isTelevision) contentFocusRequester else null,
            )
            MainTab.Statistics -> StatsScreen(
                viewModel = statsViewModel,
                primaryFocusRequester = if (isTelevision) contentFocusRequester else null,
            )
            MainTab.Settings -> SettingsScreen(
                tokenStore = tokenStore,
                authViewModel = authViewModel,
                onLogout = onLogout,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                appLocale = appLocale,
                onAppLocaleChange = onAppLocaleChange,
                primaryFocusRequester = if (isTelevision) contentFocusRequester else null,
            )
        }
    }

    if (isTelevision) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .focusProperties {
                        right = contentFocusRequester
                    }
            ) {
                tabs.forEach { spec ->
                    NavigationRailItem(
                        selected = selectedTab == spec.tab,
                        onClick = { selectedTabKey = spec.tab.name },
                        icon = {
                            Icon(
                                spec.icon,
                                contentDescription = stringResource(spec.labelRes),
                                modifier = Modifier.padding(4.dp),
                            )
                        },
                        label = { Text(stringResource(spec.labelRes)) },
                        modifier = Modifier.tvFocusBorder(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TabContent()
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { spec ->
                        NavigationBarItem(
                            selected = selectedTab == spec.tab,
                            onClick = { selectedTabKey = spec.tab.name },
                            icon = {
                                Icon(spec.icon, contentDescription = stringResource(spec.labelRes))
                            },
                            label = { Text(stringResource(spec.labelRes)) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                TabContent()
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
        PreviewAccessViewModel(context.applicationContext).also { it.onUserSessionReady() }
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
