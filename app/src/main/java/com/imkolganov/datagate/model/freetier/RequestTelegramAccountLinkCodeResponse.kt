package com.imkolganov.datagate.model.freetier

/** Mirrors backend [DataGateMonitor.SharedModels] RequestTelegramAccountLinkCodeResponse. */
data class RequestTelegramAccountLinkCodeResponse(
    val code: String,
    val expiresInSeconds: Int,
)
