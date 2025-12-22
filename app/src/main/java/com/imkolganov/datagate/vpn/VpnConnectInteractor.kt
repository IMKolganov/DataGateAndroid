package com.imkolganov.datagate.vpn

import OvpnApiClient
import android.util.Log
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import java.util.concurrent.atomic.AtomicBoolean

class VpnConnectInteractor(
    private val getExternalId: () -> String?,
    private val getInstallationId: () -> String?,
    private val serversRepository: OpenVpnServersRepository,
    private val vpnController: VpnController,
    private val api: OvpnApiClient
) {
    private val isConnecting = AtomicBoolean(false)

    suspend fun connect() {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w("OpenVPN3", "Connect ignored: already in progress")
            return
        }

        try {
            vpnController.showStatus("SELECTING_SERVER", "Selecting best server")
            Log.d("OpenVPN3", "Selecting best server...")
            val best = serversRepository.pickBestServer()
            val serverName = best.name
            Log.d("OpenVPN3", "Selected serverId=${best.serverId}")
            vpnController.showStatus("SELECTED_SERVER", serverName)

            vpnController.showStatus("GETTING_INSTALLATION_ID", "Reading installation id")
            val installationId = getInstallationId()
            if (installationId.isNullOrBlank()) {
                vpnController.showError("InstallationId is not ready yet")
                return
            }

            vpnController.showStatus("GETTING_EXTERNAL_ID", "Reading external id for $serverName")
            val externalId = getExternalId()
            if (externalId.isNullOrBlank()) {
                vpnController.showError("ExternalId is not available")
                return
            }

            vpnController.showStatus("BUILDING_COMMON_NAME", "Preparing certificate identity for $serverName")
            val commonName = "adg-${best.serverId}-$externalId-$installationId"

            vpnController.showStatus("DOWNLOADING_CONFIG", "Requesting VPN profile for $serverName")
            val downloaded = api.ensureAndDownloadDeviceFile(
                vpnServerId = best.serverId,
                commonName = commonName,
                externalId = externalId,
                issuedTo = "datagate android user $externalId device $installationId"
            )

            vpnController.showStatus("CONFIG_RECEIVED", "size=${downloaded.content.size}")

            val configText = downloaded.content.toString(Charsets.UTF_8)
            Log.d("OpenVPN3", "OVPN FILE RECEIVED, size=${downloaded.content.size}")

            vpnController.startWithConfig(configText)
        } catch (t: Throwable) {
            Log.e("OpenVPN3", "Connect flow failed", t)
            vpnController.showError("Connect failed: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            isConnecting.set(false)
        }
    }
}
