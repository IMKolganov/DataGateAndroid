package com.imkolganov.datagate.vpn.traffic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import com.imkolganov.datagate.logger.VpnDebugLogger
import java.net.Inet4Address

/**
 * Reads Android TUN iface counters the same way DataGateMac reads utun
 * (`ifi_ibytes` / `ifi_obytes`): RX = download, TX = upload.
 */
object VpnTunIfaceCounters {
    private const val TAG = "VpnTraffic"

    @Volatile
    private var warned = false

    private val ifaceMiss = IfaceMissTracker()

    fun read(context: Context): TrafficCounters? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val iface = findVpnInterfaceName(cm)
        val nowMs = SystemClock.elapsedRealtime()
        if (iface.isNullOrBlank()) {
            // Right after CONNECTED the system has not published the VPN network yet;
            // callers retry on the next tick, so only a miss that outlives the grace is a problem.
            ifaceMiss.onMiss(nowMs)?.let { missingMs ->
                VpnDebugLogger.w(TAG, "VPN interface name not found for ${missingMs}ms")
            }
            return null
        }
        ifaceMiss.onFound(nowMs)?.let { missingMs ->
            VpnDebugLogger.i(TAG, "VPN interface found after ${missingMs}ms: $iface")
        }
        val rx = TrafficStats.getRxBytes(iface)
        val tx = TrafficStats.getTxBytes(iface)
        val mapped = mapTunIfaceToUserCounters(rx, tx)
        if (mapped == null) {
            warnOnce("TrafficStats UNSUPPORTED for iface=$iface")
        }
        return mapped
    }

    fun mapTunIfaceToUserCounters(rxBytes: Long, txBytes: Long): TrafficCounters? {
        if (rxBytes < 0L || txBytes < 0L) return null
        return TrafficCounters(bytesIn = rxBytes, bytesOut = txBytes)
    }

    internal fun findVpnInterfaceName(cm: ConnectivityManager): String? {
        val candidates = ArrayList<TunIfaceCandidate>()
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val props = cm.getLinkProperties(network)
            val hasTunnelIpv4 = props?.linkAddresses.orEmpty().any { link ->
                val host = link.address
                host is Inet4Address && !host.isLoopbackAddress
            }
            candidates.add(
                TunIfaceCandidate(
                    isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    interfaceName = props?.interfaceName,
                    hasTunnelIpv4 = hasTunnelIpv4,
                )
            )
        }
        return VpnTunIfacePick.pickName(candidates)
    }

    private fun warnOnce(message: String) {
        if (warned) return
        warned = true
        VpnDebugLogger.w(TAG, message)
    }
}

/**
 * Tracks one streak of "VPN interface not found" samples. A streak is reported once it lasts
 * [graceMs]; a gap longer than [streakGapMs] between misses starts a new streak (new session).
 */
internal class IfaceMissTracker(
    private val graceMs: Long = 5_000L,
    private val streakGapMs: Long = 5_000L,
) {
    private var firstMissAtMs = -1L
    private var lastMissAtMs = -1L
    private var reported = false

    /** Returns the streak length when it should be reported now, else null. */
    @Synchronized
    fun onMiss(nowMs: Long): Long? {
        if (firstMissAtMs < 0 || nowMs - lastMissAtMs > streakGapMs) {
            firstMissAtMs = nowMs
            reported = false
        }
        lastMissAtMs = nowMs
        val missingMs = nowMs - firstMissAtMs
        if (reported || missingMs < graceMs) return null
        reported = true
        return missingMs
    }

    /** Returns the streak length when a reported streak just ended, else null. */
    @Synchronized
    fun onFound(nowMs: Long): Long? {
        val missingMs = if (reported) nowMs - firstMissAtMs else null
        firstMissAtMs = -1L
        lastMissAtMs = -1L
        reported = false
        return missingMs
    }
}
