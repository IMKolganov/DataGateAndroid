package com.imkolganov.datagate.vpn

import OvpnApiClient
import android.content.Context
import android.net.Uri
import android.util.Log
import com.imkolganov.datagate.servers.OpenVpnServersRepository
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
    private val api: OvpnApiClient
) {
    private val isConnecting = AtomicBoolean(false)

    /**
     * Starts VPN using [VpnServerSelectionStore] (same rules as Home and Access).
     */
    suspend fun connect() {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w("OpenVPN3", "Connect ignored: already in progress")
            return
        }

        try {
            val preferredServerId = when (VpnServerSelectionStore.getMode(appContext)) {
                ServerSelectionMode.AUTO -> null
                ServerSelectionMode.MANUAL -> VpnServerSelectionStore.getSelectedServerId(appContext)
            }
            val best = if (preferredServerId == null) {
                vpnController.showStatus("SELECTING_SERVER", "Selecting best server")
                Log.d("OpenVPN3", "Selecting best server...")
                serversRepository.pickBestServer()
            } else {
                vpnController.showStatus("SELECTING_SERVER", "Resolving server")
                Log.d("OpenVPN3", "Using selected serverId=$preferredServerId")
                serversRepository.getServerByIdOrThrow(preferredServerId)
            }
            val serverName = best.name
            Log.d("OpenVPN3", "Selected serverId=${best.serverId}")
            vpnController.notifyServerSelectedForConnection(
                best.serverId,
                serverName ?: "Server"
            )

            vpnController.showStatus("GETTING_INSTALLATION_ID", "Reading installation id")
            val installationId = getInstallationId()
            val shortInstallationId = uuidToShort(installationId)
            if (shortInstallationId.isBlank()) {
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
            val commonName = "adg-${best.serverId}-$externalId-$shortInstallationId"

            vpnController.showStatus("DOWNLOADING_CONFIG", "Requesting VPN profile for $serverName")
            val downloaded = api.ensureAndDownloadDeviceFile(
                vpnServerId = best.serverId,
                commonName = commonName,
                externalId = externalId,
                issuedTo = "datagate android user $externalId device $shortInstallationId"
            )

            vpnController.showStatus("CONFIG_RECEIVED", "size=${downloaded.content.size}")

            val configText = downloaded.content.toString(Charsets.UTF_8)
            val linkProtocol = VpnLinkProtocol.fromOvpnConfigContent(configText)
            Log.d(
                "OpenVPN3",
                "OVPN profile transport=$linkProtocol (from proto line in file), size=${downloaded.content.size}"
            )
            val patchedConfig = forceWssConfig(configText, linkProtocol)

            val apiUrl = best.apiUrl
                ?: error("Best server apiUrl is null")

            val wssUrl = httpsToWssProxy(apiUrl, linkProtocol)
            vpnController.startWithConfig(patchedConfig, wssUrl, linkProtocol)
        } catch (t: Throwable) {
            Log.e("OpenVPN3", "Connect flow failed", t)
            vpnController.showError("Connect failed: ${t.message ?: t.javaClass.simpleName}")
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
