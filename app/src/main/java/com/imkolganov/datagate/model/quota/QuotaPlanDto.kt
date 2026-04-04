package com.imkolganov.datagate.model.quota

/** Mirrors backend QuotaPlanDto. */
data class QuotaPlanDto(
    val id: Int,
    val name: String,
    val description: String?,
    val dailyQuotaBytes: Long?,
    val monthlyQuotaBytes: Long?,
    val upKbps: Int?,
    val downKbps: Int?,
    val overlimitAction: Int?,
    val throttleUpKbps: Int?,
    val throttleDownKbps: Int?,
    val isActive: Boolean,
    val isDefault: Boolean
)
