package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.core.content.edit

/**
 * Per-app split tunneling: apps listed here connect directly instead of through the VPN tunnel.
 * Delivered to the OS via [android.net.VpnService.Builder.addDisallowedApplication].
 */
data class SplitTunnelSettings(
    val enabled: Boolean = false,
    /** Package names that bypass the tunnel; ignored while [enabled] is false. */
    val bypassPackages: List<String> = emptyList(),
)

/**
 * SharedPreferences rather than DataStore because the connect path reads this while building the
 * service Intent, including from [VpnController.onPermissionGranted], which is a launcher callback
 * with no coroutine to suspend in. Same prefs file as the other VPN state for one place on disk.
 */
object SplitTunnelStore {
    private const val PREFS_NAME = "vpn_state"
    private const val KEY_ENABLED = "split_tunnel_enabled"
    private const val KEY_BYPASS_PACKAGES = "split_tunnel_bypass_packages"

    fun getSettings(context: Context): SplitTunnelSettings {
        val prefs = prefs(context)
        return SplitTunnelSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            bypassPackages = SplitTunnelPolicy.sanitizePackages(
                packages = decodePackages(prefs.getString(KEY_BYPASS_PACKAGES, null)),
                selfPackage = context.packageName,
            ),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun setBypassPackages(context: Context, packages: Collection<String>) {
        val sanitized = SplitTunnelPolicy.sanitizePackages(packages, context.packageName)
        prefs(context).edit {
            if (sanitized.isEmpty()) {
                remove(KEY_BYPASS_PACKAGES)
            } else {
                putString(KEY_BYPASS_PACKAGES, sanitized.joinToString("\n"))
            }
        }
    }

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_ENABLED)
            remove(KEY_BYPASS_PACKAGES)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun decodePackages(value: String?): List<String> =
        value?.lineSequence()?.toList().orEmpty()
}
