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

    /**
     * Android 13+ keeps the base OVPN text and applies [excludedRoutes] via excludeRoute.
     * Android 12 rewrites the `net_gateway` block from the live IP list on every establish.
     */
    fun resolveOpenVpnEstablish(
        storedConfig: String,
        intentRoutes: List<IpCidrRoute>,
        live: ExcludeRouteLiveInput?,
        supportsAndroidRouteExclusion: Boolean,
    ): OpenVpnEstablishPlan {
        val base = IpListRouteConfig.stripAppendedBypassRoutes(storedConfig)
        if (supportsAndroidRouteExclusion) {
            return OpenVpnEstablishPlan(
                configText = base,
                excludedRoutes = resolveForEstablish(
                    intentRoutes = intentRoutes,
                    live = live,
                    forXray = false,
                    supportsAndroidRouteExclusion = true,
                ),
            )
        }
        if (live == null) {
            return OpenVpnEstablishPlan(configText = storedConfig, excludedRoutes = emptyList())
        }
        if (!live.enabled) {
            return OpenVpnEstablishPlan(configText = base, excludedRoutes = emptyList())
        }
        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = base,
            routes = live.generalRoutes,
            priorityRoutes = live.priorityRoutes,
            coverageMode = live.coverageMode,
            android12OvpnRouteLimit = live.android12OvpnRouteLimit,
            supportsAndroidRouteExclusion = false,
            safeRouteLimitEnabled = live.safeRouteLimitEnabled,
        )
        return OpenVpnEstablishPlan(
            configText = plan.config,
            excludedRoutes = emptyList(),
        )
    }
}

data class OpenVpnEstablishPlan(
    val configText: String,
    val excludedRoutes: List<IpCidrRoute>,
)

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

    fun resolveOpenVpnEstablish(
        storedConfig: String,
        intentRoutes: List<IpCidrRoute>,
        supportsAndroidRouteExclusion: Boolean,
    ): OpenVpnEstablishPlan = ExcludeRouteSessionPolicy.resolveOpenVpnEstablish(
        storedConfig = storedConfig,
        intentRoutes = intentRoutes,
        live = live,
        supportsAndroidRouteExclusion = supportsAndroidRouteExclusion,
    )

    fun resetForTests() {
        live = null
    }
}
