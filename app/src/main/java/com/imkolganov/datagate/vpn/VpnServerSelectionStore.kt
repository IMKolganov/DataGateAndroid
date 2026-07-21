package com.imkolganov.datagate.vpn

import android.content.Context

/**
 * Single source of truth for server selection mode and manual server id (shared with Access tab).
 * Same SharedPreferences file as other VPN prefs for one place on disk.
 */
object VpnServerSelectionStore {
    private const val PREFS_NAME = "vpn_state"
    private const val KEY_MODE = "access_server_mode"
    private const val KEY_SELECTED_SERVER_ID = "access_selected_server_id"

    fun getMode(context: Context): ServerSelectionMode {
        val v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ServerSelectionMode.AUTO.name) ?: ServerSelectionMode.AUTO.name
        return runCatching { ServerSelectionMode.valueOf(v) }.getOrDefault(ServerSelectionMode.AUTO)
    }

    fun setMode(context: Context, mode: ServerSelectionMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun getSelectedServerId(context: Context): Int? {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SELECTED_SERVER_ID, -1)
        return if (id >= 0) id else null
    }

    fun setSelectedServerId(context: Context, serverId: Int?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (serverId == null) {
                    remove(KEY_SELECTED_SERVER_ID)
                } else {
                    putInt(KEY_SELECTED_SERVER_ID, serverId)
                }
            }
            .apply()
    }

    /** Clears mode + selected id so a later login cannot reuse another account's Pro server. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MODE)
            .remove(KEY_SELECTED_SERVER_ID)
            .apply()
    }
}
