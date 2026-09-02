package com.imkolganov.datagate.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.tv.LocalIsTelevision
import com.imkolganov.datagate.ui.tv.tvClickable
import com.imkolganov.datagate.vpn.InstalledAppInfo
import com.imkolganov.datagate.vpn.InstalledAppsCatalog
import com.imkolganov.datagate.vpn.SplitTunnelListState
import com.imkolganov.datagate.vpn.SplitTunnelPolicy
import com.imkolganov.datagate.vpn.SplitTunnelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val APP_ICON_SIZE_PX = 96
private val rowShape = RoundedCornerShape(12.dp)

/**
 * Picker for apps that should bypass the tunnel. Selections are persisted immediately, but the OS
 * only reads them at `establish()`, so the hint tells the user to reconnect.
 */
@Composable
fun SplitTunnelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTelevision = LocalIsTelevision.current

    var enabled by remember { mutableStateOf(false) }
    var bypassPackages by remember { mutableStateOf(emptySet<String>()) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var bypassOnly by remember { mutableStateOf(false) }
    val iconCache = remember { mutableMapOf<String, ImageBitmap?>() }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        val settings = withContext(Dispatchers.IO) { SplitTunnelStore.getSettings(appContext) }
        enabled = settings.enabled
        bypassPackages = settings.bypassPackages.toSet()
        apps = withContext(Dispatchers.IO) { InstalledAppsCatalog.loadNetworkApps(appContext) }
    }

    fun persistBypassPackages(next: Set<String>) {
        bypassPackages = next
        val appContext = context.applicationContext
        scope.launch {
            withContext(Dispatchers.IO) { SplitTunnelStore.setBypassPackages(appContext, next) }
        }
    }

    val loadedApps = apps
    val visibleApps = remember(loadedApps, query, bypassOnly, bypassPackages) {
        SplitTunnelPolicy.visibleApps(
            apps = loadedApps.orEmpty(),
            query = query,
            bypassOnly = bypassOnly,
            bypassPackages = bypassPackages,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (isTelevision) 24.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    stringResource(R.string.settings_split_tunnel_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCards.shape,
                colors = AppCards.defaultColors(),
                elevation = AppCards.defaultElevation(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_split_tunnel_enable_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.settings_split_tunnel_enable_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { value ->
                                enabled = value
                                val appContext = context.applicationContext
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        SplitTunnelStore.setEnabled(appContext, value)
                                    }
                                }
                            },
                        )
                    }
                    if (!enabled) {
                        Text(
                            stringResource(R.string.settings_split_tunnel_disabled_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.settings_split_tunnel_reconnect_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCards.shape,
                colors = AppCards.defaultColors(),
                elevation = AppCards.defaultElevation(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.settings_split_tunnel_search_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = !bypassOnly,
                            onClick = { bypassOnly = false },
                            label = { Text(stringResource(R.string.settings_split_tunnel_filter_all)) },
                        )
                        FilterChip(
                            selected = bypassOnly,
                            onClick = { bypassOnly = true },
                            label = { Text(stringResource(R.string.settings_split_tunnel_filter_selected)) },
                        )
                    }
                    Text(
                        stringResource(
                            R.string.settings_split_tunnel_count,
                            bypassPackages.size,
                            loadedApps?.size ?: 0,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (bypassPackages.isNotEmpty()) {
                        TextButton(onClick = { persistBypassPackages(emptySet()) }) {
                            Text(stringResource(R.string.settings_split_tunnel_clear))
                        }
                    }
                }
            }
        }

        when (SplitTunnelPolicy.listState(loadedApps != null, visibleApps.size)) {
            SplitTunnelListState.Loading -> item {
                Text(
                    stringResource(R.string.settings_split_tunnel_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SplitTunnelListState.Empty -> item {
                Text(
                    stringResource(R.string.settings_split_tunnel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SplitTunnelListState.Apps -> items(visibleApps, key = { it.packageName }) { app ->
                AppBypassRow(
                    app = app,
                    bypassing = app.packageName in bypassPackages,
                    iconCache = iconCache,
                    onToggle = { bypassing ->
                        persistBypassPackages(
                            if (bypassing) {
                                bypassPackages + app.packageName
                            } else {
                                bypassPackages - app.packageName
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppBypassRow(
    app: InstalledAppInfo,
    bypassing: Boolean,
    iconCache: MutableMap<String, ImageBitmap?>,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = rowShape) { onToggle(!bypassing) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(
            packageName = app.packageName,
            iconCache = iconCache,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.isSystemApp) {
                Text(
                    stringResource(R.string.settings_split_tunnel_system_app),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = bypassing, onCheckedChange = onToggle)
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    iconCache: MutableMap<String, ImageBitmap?>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon by produceState(initialValue = iconCache[packageName], packageName) {
        if (value != null) return@produceState
        val appContext = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            InstalledAppsCatalog.loadIconBitmap(appContext, packageName, APP_ICON_SIZE_PX)
                ?.asImageBitmap()
        }
        iconCache[packageName] = loaded
        value = loaded
    }

    val bitmap = icon
    if (bitmap == null) {
        Box(
            modifier = modifier.background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = rowShape,
            ),
        )
    } else {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    }
}
