package com.imkolganov.datagate.servers

data class BestServerResult(
    val serverId: Int,
    val name: String? = null,
    val apiUrl: String? = null,
    val countConnectedClients: Int,
    val isDefault: Boolean
)