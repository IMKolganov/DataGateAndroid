package com.imkolganov.datagate.model.freetier

/** Mirrors backend [DataGateMonitor.SharedModels] FreeTierAccessStatusResponse. */
data class FreeTierAccessStatusResponse(
    val isApplicable: Boolean,
    val isCompliant: Boolean,
    val isMergedAccount: Boolean,
    val isChannelSubscribed: Boolean,
    val isGracePeriod: Boolean,
    val isLinkedToTelegram: Boolean,
    val canRequestAccountLinkCode: Boolean,
    val activePlanName: String?,
    val requiredChannel: String?,
    /** ISO-8601 UTC timestamp when the grace window ends; null unless [isGracePeriod] is true. */
    val graceExpiresAtUtc: String? = null,
)
