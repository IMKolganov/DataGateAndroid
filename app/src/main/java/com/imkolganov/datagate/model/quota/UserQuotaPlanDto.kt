package com.imkolganov.datagate.model.quota

/** Mirrors backend UserQuotaPlanDto. */
data class UserQuotaPlanDto(
    val id: Int,
    val userId: Int,
    val quotaPlanId: Int,
    val effectiveFrom: String?,
    val effectiveTo: String?,
    val assignedBy: Int?,
    val note: String?
)
