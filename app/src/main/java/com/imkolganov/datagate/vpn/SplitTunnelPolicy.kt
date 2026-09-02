package com.imkolganov.datagate.vpn

/** What the picker shows below its filters. */
enum class SplitTunnelListState {
    /** Installed apps are still being read from the package manager. */
    Loading,

    /** Catalog is loaded but the query/filter matched nothing. */
    Empty,

    /** At least one app to show. */
    Apps,
}

/**
 * Pure selection rules for per-app split tunneling, kept out of the Android layer so both the
 * connect path and the picker UI derive the same list.
 */
object SplitTunnelPolicy {

    /**
     * Our own package is never bypassable: the tunnel it manages would then be invisible to it,
     * and its server sockets are already protected via [android.net.VpnService.protect].
     */
    fun sanitizePackages(packages: Collection<String>, selfPackage: String): List<String> =
        packages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != selfPackage }
            .distinct()
            .sorted()
            .toList()

    /** Empty while the feature is off, so a stale list cannot leak traffic out of the tunnel. */
    fun bypassPackagesForSession(
        settings: SplitTunnelSettings,
        selfPackage: String,
    ): List<String> =
        if (!settings.enabled) {
            emptyList()
        } else {
            sanitizePackages(settings.bypassPackages, selfPackage)
        }

    /** Picker list: [apps] order is preserved, so the catalog decides sorting. */
    fun visibleApps(
        apps: List<InstalledAppInfo>,
        query: String,
        bypassOnly: Boolean,
        bypassPackages: Set<String>,
    ): List<InstalledAppInfo> =
        apps.filter { app ->
            (!bypassOnly || app.packageName in bypassPackages) && matchesQuery(app, query)
        }

    fun listState(catalogLoaded: Boolean, visibleAppCount: Int): SplitTunnelListState = when {
        !catalogLoaded -> SplitTunnelListState.Loading
        visibleAppCount == 0 -> SplitTunnelListState.Empty
        else -> SplitTunnelListState.Apps
    }

    fun matchesQuery(app: InstalledAppInfo, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true)
    }
}
