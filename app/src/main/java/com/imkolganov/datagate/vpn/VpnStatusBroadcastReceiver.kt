package com.imkolganov.datagate.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnStatusBroadcastReceiver(
    private val onStatus: (eventName: String, eventInfo: String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OpenVpn3Service.ACTION_STATUS) return

        val eventName = intent.getStringExtra(OpenVpn3Service.EXTRA_EVENT_NAME) ?: return
        val eventInfo = intent.getStringExtra(OpenVpn3Service.EXTRA_EVENT_INFO) ?: ""

        onStatus(eventName, eventInfo)
    }
}
