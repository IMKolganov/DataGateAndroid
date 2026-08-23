package com.imkolganov.datagate.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnStatusBroadcastReceiver(
    private val onStatus: (
        eventName: String,
        eventInfo: String,
        fromQuery: Boolean,
        statusEngine: String?,
    ) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OpenVpn3Service.ACTION_STATUS) return

        val eventName = intent.getStringExtra(OpenVpn3Service.EXTRA_EVENT_NAME) ?: return
        val eventInfo = intent.getStringExtra(OpenVpn3Service.EXTRA_EVENT_INFO) ?: ""
        val fromQuery = intent.getBooleanExtra(OpenVpn3Service.EXTRA_STATUS_FROM_QUERY, false)
        val statusEngine = intent.getStringExtra(OpenVpn3Service.EXTRA_STATUS_ENGINE)

        onStatus(eventName, eventInfo, fromQuery, statusEngine)
    }
}
