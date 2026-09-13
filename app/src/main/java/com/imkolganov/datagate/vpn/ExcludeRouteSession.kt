package com.imkolganov.datagate.vpn

/**
 * Live IP-list snapshot for TUN establish. Bypass apps already refresh per
 * [SplitTunnelSession]; exclude-routes used to stay frozen in the connect intent.
 */
data class ExcludeRouteLiveInput(
    val generalRoutes: List<IpCidrRoute>,
    val priorityRoutes: List<IpCidrRoute>,
    val enabled: Boolean,
    val coverageMode: IpListCoverageMode,
    val android12OvpnRouteLimit: Int,
    val safeRouteLimitEnabled: Boolean,
)

object ExcludeRouteSessionPolicy {
    fun resolveForEstablish(
        intentRoutes: List<IpCidrRoute>,
        live: ExcludeRouteLiveInput?,
        forXray: Boolean,
        supportsAndroidRouteExclusion: Boolean,
        constrainedDevice: Boolean = false,
    ): List<IpCidrRoute> {
        if (live == null) return intentRoutes
        if (!live.enabled) return emptyList()
        return if (forXray) {
            IpListRouteConfig.prepareXrayBypassRoutes(
                routes = live.generalRoutes,
                priorityRoutes = live.priorityRoutes,
                coverageMode = live.coverageMode,
                android12OvpnRouteLimit = live.android12OvpnRouteLimit,
                supportsAndroidRouteExclusion = supportsAndroidRouteExclusion,
                safeRouteLimitEnabled = live.safeRouteLimitEnabled,
                constrainedDevice = constrainedDevice,
            ).androidExcludedRoutes
        } else {
            IpListRouteConfig.prepareConnectionRoutes(
                config = "",
                routes = live.generalRoutes,
                priorityRoutes = live.priorityRoutes,
                coverageMode = live.coverageMode,
                android12OvpnRouteLimit = live.android12OvpnRouteLimit,
                supportsAndroidRouteExclusion = supportsAndroidRouteExclusion,
                safeRouteLimitEnabled = live.safeRouteLimitEnabled,
            ).androidExcludedRoutes
        }
    }
}

object ExcludeRouteSession {
    @Volatile
    private var live: ExcludeRouteLiveInput? = null

    fun publish(routes: IpListConnectionRoutes, settings: IpListSettings) {
        live = ExcludeRouteLiveInput(
            generalRoutes = routes.generalRoutes,
            priorityRoutes = routes.priorityRoutes,
            enabled = settings.cidrListsEnabled,
            coverageMode = settings.coverageMode,
            android12OvpnRouteLimit = settings.android12OvpnRouteLimit,
            safeRouteLimitEnabled = settings.safeRouteLimitEnabled,
        )
    }

    fun publishDisabled() {
        live = ExcludeRouteLiveInput(
            generalRoutes = emptyList(),
            priorityRoutes = emptyList(),
            enabled = false,
            coverageMode = IpListCoverageMode.FAST,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            safeRouteLimitEnabled = true,
        )
    }

    fun resolveForEstablish(
        intentRoutes: List<IpCidrRoute>,
        forXray: Boolean,
        supportsAndroidRouteExclusion: Boolean,
        constrainedDevice: Boolean = false,
    ): List<IpCidrRoute> = ExcludeRouteSessionPolicy.resolveForEstablish(
        intentRoutes = intentRoutes,
        live = live,
        forXray = forXray,
        supportsAndroidRouteExclusion = supportsAndroidRouteExclusion,
        constrainedDevice = constrainedDevice,
    )

    fun resetForTests() {
        live = null
    }
}
