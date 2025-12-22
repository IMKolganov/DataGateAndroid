package com.imkolganov.datagate

import OvpnApiClient
import android.app.Activity
import android.content.Context
import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.auth.AuthModule
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.SharedPrefsTokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.GoogleIdTokenProvider
import com.imkolganov.datagate.auth.http.OkHttpBackendAuthApi
import com.imkolganov.datagate.network.HttpClients
import com.imkolganov.datagate.servers.OpenVpnServersApi
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import com.imkolganov.datagate.stats.StatsApiClient
import com.imkolganov.datagate.vpn.VpnConnectInteractor
import com.imkolganov.datagate.vpn.VpnController
import okhttp3.OkHttpClient

class AppGraph(
    activity: Activity,
    appContext: Context,
    private val vpnController: VpnController
) {
    val tokenStore: TokenStore = SharedPrefsTokenStore(appContext)

    val httpPlain: OkHttpClient = HttpClients.createPlain()

    val backendAuthApi: BackendAuthApi =
        OkHttpBackendAuthApi(
            http = httpPlain,
            baseUrl = AuthConfig.BACKEND_BASE_URL
        )

    val idTokenProvider: GoogleIdTokenProvider =
        AuthModule.createGoogleIdTokenProvider(
            activity = activity,
            context = appContext
        )

    val httpAuth: OkHttpClient =
        HttpClients.createAuth(
            tokenStore = tokenStore,
            idTokenProvider = idTokenProvider,
            backendAuthApi = backendAuthApi
        )

    val serversRepository: OpenVpnServersRepository =
        OpenVpnServersRepository(
            api = OpenVpnServersApi(http = httpAuth)
        )

    val ovpnApi: OvpnApiClient =
        OvpnApiClient(
            http = httpAuth,
            baseUrl = AuthConfig.BACKEND_BASE_URL,
            tokenProvider = { tokenStore.getAccessToken() }
        )

    fun createConnectInteractor(
        getInstallationId: () -> String?
    ): VpnConnectInteractor =
        VpnConnectInteractor(
            getExternalId = { tokenStore.getAuthInfo().externalId },
            getInstallationId = getInstallationId,
            serversRepository = serversRepository,
            vpnController = vpnController,
            api = ovpnApi
        )

    val statsApi: StatsApiClient =
        StatsApiClient(
            http = httpAuth,
            baseUrl = AuthConfig.BACKEND_BASE_URL,
            tokenProvider = { tokenStore.getAccessToken() }
        )

}
