package com.imkolganov.datagate

/**
 * When true: client ignores [com.imkolganov.datagate.model.servers.OpenVpnServerV2Dto.isAccessibleForUserQuotaPlan]
 * (no "not in your plan" UI, connect/auto-pick not blocked). For temporary backend testing only.
 */
const val TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS = false
