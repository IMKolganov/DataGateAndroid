package com.imkolganov.datagate.vpn

/**
 * Budget for [android.net.VpnService.Builder.excludeRoute] calls during [tun_builder_establish].
 *
 * AOSP stress-tests route evaluation at 4000 entries ([VpnTest.testDoesNotLockUpWithTooManyRoutes]).
 */
internal object IpListEstablishRoutePolicy {

    const val SAFE_ESTABLISH_EXCLUDE_ROUTE_BUDGET = IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT

    fun excludeRouteCallsForPlan(plan: IpListConnectionRoutePlan): Int =
        when (plan.delivery) {
            IpListRouteDelivery.ANDROID_EXCLUDE_ROUTE -> plan.androidExcludedRoutes.size
            IpListRouteDelivery.OVPN_PROFILE -> 0
        }

    fun isWithinEstablishBudget(excludeRouteCalls: Int): Boolean =
        excludeRouteCalls <= SAFE_ESTABLISH_EXCLUDE_ROUTE_BUDGET

    fun establishBudgetViolation(
        plan: IpListConnectionRoutePlan,
        coverageMode: IpListCoverageMode,
    ): String? {
        val calls = excludeRouteCallsForPlan(plan)
        if (isWithinEstablishBudget(calls)) return null
        return "Coverage=$coverageMode delivery=${plan.delivery} schedules $calls excludeRoute " +
            "calls at establish(); budget is $SAFE_ESTABLISH_EXCLUDE_ROUTE_BUDGET."
    }
}
