package com.imkolganov.datagate.vpn.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.imkolganov.datagate.logger.EngineJournal
import com.imkolganov.datagate.logger.VpnDebugLogger
import com.imkolganov.datagate.util.NetworkIdentityReader
import com.imkolganov.datagate.vpn.traffic.VpnTrafficUiState
import com.imkolganov.datagate.vpn.traffic.VpnTunIfaceCounters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * Extra EVENTs used to tell "site/app" vs "device internet" vs "VPN path" when
 * something like a video fails to load. Runs only when the Home journal or the
 * file debug logger is on — no extra probes for ordinary users.
 */
object VpnDiagnostics {
    private const val TAG = "VpnDiag"
    private const val PROBE_DELAY_MS = 2_000L
    private const val MIN_PROBE_GAP_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val stall = TrafficStallTracker()
    private val probeGeneration = VpnProbeGeneration()
    private var probeJob: Job? = null
    private var lastProbeAtMs = 0L
    private var lastEngine: String = "unknown"

    fun isEnabled(): Boolean =
        EngineJournal.isEnabled() || VpnDebugLogger.get()?.isEnabled() == true

    fun onMonitorStarted() {
        synchronized(lock) { stall.reset() }
    }

    fun cancel() {
        synchronized(lock) {
            probeGeneration.invalidate()
            probeJob?.cancel()
            probeJob = null
            stall.reset()
        }
    }

    fun schedulePostConnect(context: Context, engine: String, trigger: String = "post_connected") {
        if (!isEnabled()) return
        lastEngine = engine
        val app = context.applicationContext
        synchronized(lock) {
            probeJob?.cancel()
            val session = probeGeneration.next()
            probeJob = scope.launch {
                delay(PROBE_DELAY_MS)
                if (!probeGeneration.isCurrent(session)) return@launch
                runSessionAndProbes(app, engine, trigger, session)
            }
        }
    }

    fun onNetworkChanged(context: Context, engine: String) {
        if (!isEnabled()) return
        schedulePostConnect(context, engine, trigger = "network_changed")
    }

    fun onTrafficTick(state: VpnTrafficUiState, sourceMissing: Boolean) {
        if (!isEnabled()) return
        val tick = synchronized(lock) { stall.onTick(state, sourceMissing) }
        if (tick.emitSourceMissing) {
            VpnDebugLogger.event(
                category = "traffic",
                action = "source_missing",
                details = mapOf(
                    "forSec" to TrafficStallTracker.SOURCE_MISSING_AFTER_SEC,
                    "engine" to lastEngine,
                ),
            )
        }
        if (tick.emitStall) {
            VpnDebugLogger.event(
                category = "traffic",
                action = "stall",
                details = mapOf(
                    "zeroSec" to tick.zeroSeconds,
                    "sessionRx" to state.sessionBytesIn,
                    "sessionTx" to state.sessionBytesOut,
                    "samples" to state.samples.size,
                    "engine" to lastEngine,
                ),
            )
        }
        if (tick.emitRecovered) {
            VpnDebugLogger.event(
                category = "traffic",
                action = "recovered",
                details = mapOf(
                    "rxBps" to state.speedInBps,
                    "txBps" to state.speedOutBps,
                    "sessionRx" to state.sessionBytesIn,
                    "engine" to lastEngine,
                ),
            )
        }
    }

    fun emitSplitTunnel(requested: List<String>, applied: Int) {
        if (!isEnabled()) return
        VpnDebugLogger.event(
            category = "split_tunnel",
            action = "applied",
            details = mapOf(
                "applied" to applied,
                "requested" to requested.size,
                "packages" to requested.joinToString(",").ifEmpty { null },
            ),
        )
    }

    internal fun runSessionAndProbes(
        context: Context,
        engine: String,
        trigger: String,
        session: Int,
    ) {
        if (!isEnabled() || !probeGeneration.isCurrent(session)) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastProbeAtMs < MIN_PROBE_GAP_MS && trigger == "network_changed") return
            lastProbeAtMs = now
        }

        val snapshot = readSnapshot(context)
        if (!probeGeneration.isCurrent(session)) return
        VpnDebugLogger.event(
            category = "session",
            action = "ready",
            details = snapshot.eventDetails() + mapOf("engine" to engine, "trigger" to trigger),
        )

        val vpnHttp = VpnHttpProbe.run()
        if (!probeGeneration.isCurrent(session)) return
        logHttpProbe("vpn", vpnHttp)
        val underNetwork = findUnderlyingNetwork(context)
        val underHttp = if (underNetwork != null) {
            VpnHttpProbe.run(open = { url -> underNetwork.openConnection(url) })
        } else {
            VpnHttpProbeResult(ok = false, code = null, latencyMs = 0, error = "no_underlying_network")
        }
        if (!probeGeneration.isCurrent(session)) return
        logHttpProbe("underlying", underHttp)

        val vpnDns = probeDns(via = "vpn") { InetAddress.getAllByName(VpnHttpProbe.DEFAULT_HOST) }
        val underDns = if (underNetwork != null) {
            probeDns(via = "underlying") { underNetwork.getAllByName(VpnHttpProbe.DEFAULT_HOST) }
        } else {
            null
        }
        if (!probeGeneration.isCurrent(session)) return

        val underOk = VpnPathHintPolicy.underlyingOk(
            hasUnderlyingNetwork = underNetwork != null,
            httpOk = underHttp.ok,
        )
        val hint = VpnPathHintPolicy.fromProbes(vpnHttp.ok, underOk)
        VpnDebugLogger.event(
            category = "connectivity",
            action = "verdict",
            details = mapOf(
                "hint" to hint.name.lowercase(),
                "vpnOk" to vpnHttp.ok,
                "underOk" to underOk,
                "vpnDnsOk" to vpnDns,
                "underDnsOk" to underDns,
                "engine" to engine,
                "trigger" to trigger,
                "read" to hintRead(hint),
            ),
        )
        VpnDebugLogger.i(TAG, "verdict hint=${hint.name.lowercase()} ${hintRead(hint)}")
    }

    private fun logHttpProbe(via: String, result: VpnHttpProbeResult) {
        VpnDebugLogger.event(
            category = "connectivity",
            action = "probe",
            details = mapOf(
                "via" to via,
                "ok" to result.ok,
                "code" to result.code,
                "latencyMs" to result.latencyMs,
                "error" to result.error,
                "host" to VpnHttpProbe.DEFAULT_HOST,
            ),
        )
    }

    private fun probeDns(via: String, resolve: () -> Array<InetAddress>): Boolean {
        val started = System.nanoTime()
        return try {
            val answers = resolve()
            val ok = answers.isNotEmpty()
            VpnDebugLogger.event(
                category = "dns",
                action = "probe",
                details = mapOf(
                    "via" to via,
                    "ok" to ok,
                    "answers" to answers.size,
                    "latencyMs" to ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L),
                    "qname" to VpnHttpProbe.DEFAULT_HOST,
                ),
            )
            ok
        } catch (t: Throwable) {
            VpnDebugLogger.event(
                category = "dns",
                action = "probe",
                details = mapOf(
                    "via" to via,
                    "ok" to false,
                    "error" to t.javaClass.simpleName,
                    "qname" to VpnHttpProbe.DEFAULT_HOST,
                ),
            )
            false
        }
    }

    private fun readSnapshot(context: Context): DnsPathSnapshot {
        val identity = runCatching { NetworkIdentityReader.read(context) }.getOrNull()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm?.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val vpnNetwork = findVpnNetwork(cm)
        val vpnProps = vpnNetwork?.let { cm?.getLinkProperties(it) }
        val privateMode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        val privateName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vpnProps?.privateDnsServerName
                ?: runCatching {
                    Settings.Global.getString(context.contentResolver, "private_dns_specifier")
                }.getOrNull()
        } else {
            null
        }
        val privateActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vpnProps?.isPrivateDnsActive
        } else {
            null
        }
        val mode = when {
            !privateMode.isNullOrBlank() -> privateMode
            privateActive == true -> "strict"
            privateActive == false -> "off"
            else -> null
        }
        val iface = cm?.let { runCatching { VpnTunIfaceCounters.findVpnInterfaceName(it) }.getOrNull() }
        return DnsPathSnapshot(
            vpnIp = identity?.vpnIpAddress,
            vpnDns = identity?.dnsServers.orEmpty(),
            privateDnsMode = mode,
            privateDnsName = privateName,
            iface = iface,
            hasVpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            activeTransport = transportLabel(caps),
        )
    }

    private fun findVpnNetwork(cm: ConnectivityManager?): Network? {
        if (cm == null) return null
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return network
        }
        return null
    }

    private fun findUnderlyingNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return network
        }
        return null
    }

    private fun transportLabel(caps: NetworkCapabilities?): String = when {
        caps == null -> "none"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    private fun hintRead(hint: VpnPathHint): String = when (hint) {
        VpnPathHint.SITE_OR_APP -> "vpn_and_internet_ok_if_broken_look_at_site_or_split_tunnel"
        VpnPathHint.VPN_PATH -> "internet_ok_vpn_path_failed"
        VpnPathHint.DEVICE_INTERNET -> "device_internet_down"
        VpnPathHint.UNKNOWN -> "probes_inconclusive"
    }
}