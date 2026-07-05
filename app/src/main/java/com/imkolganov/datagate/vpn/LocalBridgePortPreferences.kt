package com.imkolganov.datagate.vpn

import android.content.Context

data class LocalBridgePortSettings(
    val poolStart: Int,
    val poolEnd: Int,
)

object LocalBridgePortPreferences {
    private const val PREFS_NAME = "vpn_state"
    private const val KEY_POOL_START = "local_bridge_pool_start"
    private const val KEY_POOL_END = "local_bridge_pool_end"

    fun getSettings(context: Context): LocalBridgePortSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val start = prefs.getInt(KEY_POOL_START, LocalBridgePortPool.DEFAULT_POOL_START)
        val end = prefs.getInt(KEY_POOL_END, LocalBridgePortPool.DEFAULT_POOL_END)
        return LocalBridgePortPool.normalizeRange(start, end)
    }

    fun saveSettings(context: Context, poolStart: Int, poolEnd: Int): LocalBridgePortSettings {
        val normalized = LocalBridgePortPool.normalizeRange(poolStart, poolEnd)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_POOL_START, normalized.poolStart)
            .putInt(KEY_POOL_END, normalized.poolEnd)
            .apply()
        return normalized
    }

    fun resetToDefaults(context: Context): LocalBridgePortSettings =
        saveSettings(
            context,
            LocalBridgePortPool.DEFAULT_POOL_START,
            LocalBridgePortPool.DEFAULT_POOL_END
        )
}
