package com.imkolganov.datagate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.imkolganov.datagate.auth.AuthModule
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.identity.InstallationIdDataStoreProvider
import com.imkolganov.datagate.ui.AppRoot
import com.imkolganov.datagate.ui.screens.access.AccessRepositoryImpl
import com.imkolganov.datagate.ui.screens.access.AccessViewModel
import com.imkolganov.datagate.ui.screens.access.AccessViewModelFactory
import com.imkolganov.datagate.ui.screens.stats.StatsViewModel
import com.imkolganov.datagate.ui.screens.stats.StatsViewModelFactory
import com.imkolganov.datagate.ui.theme.DataGateAndroidTheme
import com.imkolganov.datagate.ui.theme.ThemeMode
import com.imkolganov.datagate.ui.theme.ThemePreferenceStore
import com.imkolganov.datagate.vpn.VpnConnectInteractor
import com.imkolganov.datagate.vpn.VpnController
import com.imkolganov.datagate.vpn.VpnStatusUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OpenVPN3"
    }

    private lateinit var authViewModel: AuthViewModel

    private var vpnState by mutableStateOf(VpnStatusUiState())
    private var authVersion by mutableStateOf(0)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    private lateinit var vpnController: VpnController
    private lateinit var graph: AppGraph
    private lateinit var connectInteractor: VpnConnectInteractor

    private var installationId: String? = null

    private lateinit var accessViewModel: AccessViewModel
    private lateinit var statsViewModel: StatsViewModel
    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "VPN permission granted from launcher")
                vpnController.onPermissionGranted()
            } else {
                vpnController.onPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = 0xFFFFFFFF.toInt(),
                darkScrim = 0xFF000000.toInt()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = 0xFFFFFFFF.toInt(),
                darkScrim = 0xFF000000.toInt()
            )
        )

        requestNotificationsPermissionIfNeeded()

        vpnController = VpnController(
            activity = this,
            permissionLauncher = vpnPermissionLauncher,
            onStateChange = { vpnState = it },
            getState = { vpnState }
        )

        graph = AppGraph(
            activity = this,
            appContext = applicationContext,
            vpnController = vpnController,
            getInstallationId = { installationId },
        )

        authViewModel = AuthModule.createAuthViewModel(
            this,
            applicationContext,
            graph.httpPlain
        )

        val accessRepo = AccessRepositoryImpl(
            serversRepository = graph.serversRepository,
            tokenStore = graph.tokenStore
        )

        val accessFactory = AccessViewModelFactory(accessRepo, applicationContext)
        accessViewModel = androidx.lifecycle.ViewModelProvider(this, accessFactory)
            .get(AccessViewModel::class.java)

        val statsFactory = StatsViewModelFactory(
            api = graph.statsApi,
            externalIdProvider = { graph.tokenStore.getAuthInfo().externalId.toString() }
        )

        statsViewModel = androidx.lifecycle.ViewModelProvider(this, statsFactory)
            .get(StatsViewModel::class.java)


        connectInteractor = graph.createConnectInteractor(
            appContext = applicationContext,
            getInstallationId = { installationId }
        )

        lifecycleScope.launch {
            installationId = InstallationIdDataStoreProvider.getOrCreate(applicationContext)
            Log.d(TAG, "InstallationId ready: $installationId")
        }

        themeMode = ThemePreferenceStore.get(applicationContext)

        setContent {
            val systemDark = isSystemInDarkTheme()
            DataGateAndroidTheme(darkTheme = themeMode.resolveDark(systemDark)) {
                AppRoot(
                    authViewModel = authViewModel,
                    tokenStore = graph.tokenStore,
                    vpnState = vpnState,
                    onRequestConnect = { lifecycleScope.launch { connectInteractor.connect() } },
                    onRequestDisconnect = { vpnController.requestDisconnect() },
                    onReconnectVpn = {
                        lifecycleScope.launch {
                            vpnController.requestDisconnect()
                            delay(2500)
                            connectInteractor.connect()
                        }
                    },
                    onAuthChanged = { authVersion++ },
                    authVersion = authVersion,
                    accessViewModel = accessViewModel,
                    statsViewModel = statsViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { next ->
                        themeMode = next
                        ThemePreferenceStore.save(applicationContext, next)
                    }
                )
            }
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        vpnController.onStart()
    }

    override fun onStop() {
        vpnController.onStop()
        super.onStop()
    }
}
