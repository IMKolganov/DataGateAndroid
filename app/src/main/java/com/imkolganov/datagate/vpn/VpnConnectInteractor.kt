package com.imkolganov.datagate.vpn

import OvpnApiClient
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.imkolganov.datagate.R
import com.imkolganov.datagate.servers.ManualServerResolve
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import com.imkolganov.datagate.util.deepMessageForApiError
import com.imkolganov.datagate.util.userFriendlyApiError
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Base64
import java.nio.ByteBuffer
import java.util.UUID

class VpnConnectInteractor(
    private val appContext: Context,
    private val getExternalId: () -> String?,
    private val getInstallationId: () -> String?,
    private val serversRepository: OpenVpnServersRepository,
    private val vpnController: VpnController,
    private val api: OvpnApiClient,
    private val ipListRoutesRepository: IpListRoutesRepository
) {
    private val isConnecting = AtomicBoolean(false)

    /**
     * Starts VPN. [VpnConnectSource.Home] always picks the best online WSS server (lowest clients).
     * [VpnConnectSource.Access] uses [VpnServerSelectionStore] (AUTO = best server, MANUAL = selected id).
     */
    suspend fun connect(source: VpnConnectSource = VpnConnectSource.Access) {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w("OpenVPN3", "Connect ignored: already in progress")
            return
        }

        try {
            val preferredServerId = when (source) {
                VpnConnectSource.Home -> null
                VpnConnectSource.Access -> when (VpnServerSelectionStore.getMode(appContext)) {
                    ServerSelectionMode.AUTO -> null
                    ServerSelectionMode.MANUAL -> VpnServerSelectionStore.getSelectedServerId(appContext)
                }
            }
            val res = appContext.resources
            val best = if (preferredServerId == null) {
                vpnController.showStatus(
                    "SELECTING_SERVER",
                    res.getString(R.string.vpn_selecting_best_server)
                )
                Log.d("OpenVPN3", "Selecting best server...")
                serversRepository.pickBestServer()
            } else {
                vpnController.showStatus(
                    "SELECTING_SERVER",
                    res.getString(R.string.vpn_resolving_server)
                )
                Log.d("OpenVPN3", "Using selected serverId=$preferredServerId")
                when (val resolved = serversRepository.resolveManualConnection(preferredServerId)) {
                    is ManualServerResolve.Ok -> resolved.result
                    is ManualServerResolve.RequiresExternalOpenVpn -> {
                        vpnController.showError(
                            res.getString(
                                R.string.vpn_requires_openvpn_connect,
                                resolved.serverName
                                    ?: res.getString(R.string.vpn_fallback_server_name)
                            )
                        )
                        return
                    }
                    is ManualServerResolve.QuotaPlanBlocked -> {
                        vpnController.showError(
                            res.getString(
                                R.string.vpn_server_quota_blocked,
                                resolved.serverName
                                    ?: res.getString(R.string.vpn_fallback_server_name)
                            )
                        )
                        return
                    }
                    is ManualServerResolve.NotAvailable -> {
                        vpnController.showError(
                            res.getString(R.string.vpn_server_manual_unavailable, preferredServerId)
                        )
                        return
                    }
                }
            }
            val serverName = best.name
            Log.d("OpenVPN3", "Selected serverId=${best.serverId}")
            vpnController.notifyServerSelectedForConnection(
                best.serverId,
                serverName ?: res.getString(R.string.vpn_fallback_server_name)
            )

            vpnController.showStatus(
                "GETTING_INSTALLATION_ID",
                res.getString(R.string.vpn_reading_installation_id)
            )
            val installationId = getInstallationId()
            val shortInstallationId = uuidToShort(installationId)
            if (shortInstallationId.isBlank()) {
                vpnController.showError(res.getString(R.string.vpn_installation_id_not_ready))
                return
            }

            vpnController.showStatus(
                "GETTING_EXTERNAL_ID",
                res.getString(R.string.vpn_reading_external_id, serverName ?: "")
            )
            val externalId = getExternalId()
            if (externalId.isNullOrBlank()) {
                vpnController.showError(res.getString(R.string.vpn_external_id_unavailable))
                return
            }

            vpnController.showStatus(
                "BUILDING_COMMON_NAME",
                res.getString(R.string.vpn_preparing_cert_identity, serverName ?: "")
            )
            val commonName = "adg-${best.serverId}-$externalId-$shortInstallationId"

            vpnController.showStatus(
                "DOWNLOADING_CONFIG",
                res.getString(R.string.vpn_requesting_profile, serverName ?: "")
            )
            val downloaded = api.ensureAndDownloadDeviceFile(
                vpnServerId = best.serverId,
                commonName = commonName,
                externalId = externalId,
                issuedTo = "datagate android user $externalId device $shortInstallationId"
            )

            vpnController.showStatus(
                "CONFIG_RECEIVED",
                res.getString(R.string.vpn_config_received, downloaded.content.size)
            )

            val configText = downloaded.content.toString(Charsets.UTF_8)
            val linkProtocol = VpnLinkProtocol.fromOvpnConfigContent(configText)
            Log.d(
                "OpenVPN3",
                "OVPN profile transport=$linkProtocol (from proto line in file), size=${downloaded.content.size}"
            )
            val ipListSettings = IpListPreferences.getSettings(appContext)
            if (ipListSettings.cidrListsEnabled) {
                vpnController.showStatus(
                    "UPDATING_IP_LIST",
                    res.getString(R.string.vpn_updating_ip_list)
                )
            }
            val bypassRoutes = ipListRoutesRepository.getRoutesForConnection()
            if (bypassRoutes.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                vpnController.showError(res.getString(R.string.vpn_ip_list_requires_android_13))
                return
            }
            val androidExcludedRoutes = when (ipListSettings.coverageMode) {
                IpListCoverageMode.FAST -> IpListRouteConfig.selectAndroidExcludedRoutes(bypassRoutes)
                IpListCoverageMode.FULL -> bypassRoutes
            }
            val patchedConfig = forceWssConfig(configText, linkProtocol)
            Log.d(
                "OpenVPN3",
                "IP list routes prepared for Android excludeRoute: " +
                    "${androidExcludedRoutes.size}/${bypassRoutes.size}, coverage=${ipListSettings.coverageMode}"
            )
            if (bypassRoutes.isNotEmpty()) {
                vpnController.showStatus(
                    "IP_LIST_READY",
                    res.getString(R.string.vpn_ip_list_ready, androidExcludedRoutes.size)
                )
            }

            val apiUrl = best.apiUrl
                ?: error("Best server apiUrl is null")

            val wssUrl = httpsToWssProxy(apiUrl, linkProtocol)
            vpnController.startWithConfig(patchedConfig, wssUrl, linkProtocol, androidExcludedRoutes)
        } catch (t: Throwable) {
            Log.e("OpenVPN3", "Connect flow failed", t)
            val raw = t.deepMessageForApiError().ifBlank { t.message.orEmpty() }
            val detail = appContext.resources.userFriendlyApiError(raw)
                .ifBlank { t.javaClass.simpleName }
            vpnController.showError(
                appContext.getString(R.string.vpn_connect_failed, detail)
            )
        } finally {
            isConnecting.set(false)
        }
    }
    private val BRIDGE_PORT = 41194
    private fun forceWssConfig(original: String, linkProtocol: VpnLinkProtocol): String {
        val protoLine = linkProtocol.configProtoLine()
        val lines = original
            .replace("\r\n", "\n")
            .split("\n")

        val out = ArrayList<String>(lines.size + 2)

        var remoteWritten = false
        var protoWritten = false

        for (raw in lines) {
            val line = raw.trimEnd()
            val lower = line.trimStart().lowercase()

            when {
                lower.startsWith("remote ") -> {
                    if (!remoteWritten) {
                        out.add("remote 127.0.0.1 $BRIDGE_PORT")
                        remoteWritten = true
                    }
                    // drop all other remote lines
                }

                lower.startsWith("proto ") -> {
                    out.add(protoLine)
                    protoWritten = true
                }

                else -> out.add(line)
            }
        }

        if (!protoWritten) out.add(0, protoLine)
        if (!remoteWritten) out.add(0, "remote 127.0.0.1 $BRIDGE_PORT")

        return out.joinToString("\n").trimEnd() + "\n"
    }

    fun uuidToShort(uuid: String?): String {
        if (uuid.isNullOrBlank()) {
            error("UUID is null or blank")
        }

        val parsed = UUID.fromString(uuid)

        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(parsed.mostSignificantBits)
        buffer.putLong(parsed.leastSignificantBits)

        return Base64.encodeToString(
            buffer.array(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    fun shortToUuid(value: String?): String {
        if (value.isNullOrBlank()) {
            error("Short UUID is null or blank")
        }

        val bytes = Base64.decode(
            value,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val buffer = ByteBuffer.wrap(bytes)
        val uuid = UUID(buffer.long, buffer.long)

        return uuid.toString()
    }


    /**
     * Backend [OpenVpnProxyController] expects `GET /api/proxy?mode=tcp|udp` for WebSocket upgrade;
     * default without query is TCP.
     */
    fun httpsToWssProxy(baseUrl: String, linkProtocol: VpnLinkProtocol): String {
        val uri = Uri.parse(baseUrl)

        val scheme = when (uri.scheme) {
            "https" -> "wss"
            "http" -> "ws"
            else -> throw IllegalArgumentException("Unsupported scheme: ${uri.scheme}")
        }

        val mode = when (linkProtocol) {
            VpnLinkProtocol.UDP -> "udp"
            VpnLinkProtocol.TCP -> "tcp"
        }

        return uri.buildUpon()
            .scheme(scheme)
            .path("/api/proxy")
            .clearQuery()
            .appendQueryParameter("mode", mode)
            .fragment(null)
            .build()
            .toString()
    }
}
