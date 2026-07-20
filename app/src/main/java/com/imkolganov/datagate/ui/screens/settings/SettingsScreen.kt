package com.imkolganov.datagate.ui.screens.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.JwtClaimsReader
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.logger.DebugPreferences
import com.imkolganov.datagate.logger.VpnDebugLogger
import com.imkolganov.datagate.network.HttpClients
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.theme.AppLanguageDropdown
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.ThemeMode
import java.util.Locale
import com.imkolganov.datagate.update.ApkUpdateInstaller
import com.imkolganov.datagate.update.ManualUpdateCheckResult
import com.imkolganov.datagate.update.UpdateManualCheck
import com.imkolganov.datagate.update.UpdatePreferences
import com.imkolganov.datagate.vpn.IpListRouteConfig
import com.imkolganov.datagate.vpn.IpListCoverageMode
import com.imkolganov.datagate.vpn.IpListPreferences
import com.imkolganov.datagate.vpn.IpListRoutesRepository
import com.imkolganov.datagate.vpn.IpListStatus
import com.imkolganov.datagate.vpn.IpListUpdateFrequency
import com.imkolganov.datagate.vpn.LocalBridgePortPool
import com.imkolganov.datagate.vpn.LocalBridgePortPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tokenStore: TokenStore,
    authViewModel: AuthViewModel? = null,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit
) {
    val context = LocalContext.current
    val uiLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val noErrorLogsLabel = stringResource(R.string.no_error_logs)
    val noDebugLogsLabel = stringResource(R.string.no_debug_logs)
    val debugLogsClearedLabel = stringResource(R.string.debug_logs_cleared)
    val projectWebsiteUrl = stringResource(R.string.project_website_url)
    val projectTelegramUrl = stringResource(R.string.project_telegram_url)
    var crashFilesCount by remember { mutableStateOf(0) }
    var crashShareMessage by remember { mutableStateOf<String?>(null) }
    var vpnDebugModeEnabled by remember { mutableStateOf(false) }
    var debugLogStatus by remember { mutableStateOf("0 B") }
    var hasDebugLogs by remember { mutableStateOf(false) }
    var debugLogPath by remember { mutableStateOf("") }
    var debugLogPreview by remember { mutableStateOf("") }
    var showDebugPreview by remember { mutableStateOf(false) }
    var debugShareMessage by remember { mutableStateOf<String?>(null) }
    var githubUpdatesEnabled by remember { mutableStateOf(true) }
    var pushNotificationsForUpdates by remember { mutableStateOf(true) }
    var autoDownloadSuggest by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    var updateCheckIsError by remember { mutableStateOf(false) }
    val updateCheckInFlight = remember { AtomicBoolean(false) }
    var showIpListSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var authInfo by remember { mutableStateOf(tokenStore.getAuthInfo()) }
    var cidrListsEnabledMain by remember { mutableStateOf(true) }
    var bridgePoolStartText by remember { mutableStateOf(LocalBridgePortPool.DEFAULT_POOL_START.toString()) }
    var bridgePoolEndText by remember { mutableStateOf(LocalBridgePortPool.DEFAULT_POOL_END.toString()) }
    var bridgePortsSavedVisible by remember { mutableStateOf(false) }

    if (showIpListSettings) {
        IpListSettingsScreen(onBack = { showIpListSettings = false })
        return
    }

    LaunchedEffect(Unit) {
        authInfo = withContext(Dispatchers.IO) { tokenStore.getAuthInfo() }
    }

    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        cidrListsEnabledMain = withContext(Dispatchers.IO) {
            IpListPreferences.getSettings(appCtx).cidrListsEnabled
        }
        val bridgePorts = withContext(Dispatchers.IO) {
            LocalBridgePortPreferences.getSettings(appCtx)
        }
        bridgePoolStartText = bridgePorts.poolStart.toString()
        bridgePoolEndText = bridgePorts.poolEnd.toString()
    }

    LaunchedEffect(showIpListSettings) {
        if (!showIpListSettings) {
            val appCtx = context.applicationContext
            cidrListsEnabledMain = withContext(Dispatchers.IO) {
                IpListPreferences.getSettings(appCtx).cidrListsEnabled
            }
        }
    }

    LaunchedEffect(Unit) {
        crashFilesCount = withContext(Dispatchers.IO) {
            getCrashFiles(context.applicationContext).size
        }
        val appCtx = context.applicationContext
        vpnDebugModeEnabled = withContext(Dispatchers.IO) {
            DebugPreferences.isVpnDebugModeEnabled(appCtx)
        }
        refreshDebugLogUi(appCtx) { size, has, path, preview ->
            debugLogStatus = size
            hasDebugLogs = has
            debugLogPath = path
            debugLogPreview = preview
        }
    }

    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        githubUpdatesEnabled = withContext(Dispatchers.IO) {
            UpdatePreferences.isCheckEnabled(appCtx)
        }
        pushNotificationsForUpdates = withContext(Dispatchers.IO) {
            UpdatePreferences.isPushForUpdatesEnabled(appCtx)
        }
        autoDownloadSuggest = withContext(Dispatchers.IO) {
            UpdatePreferences.isAutoDownloadEnabled(appCtx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        SessionLogoutCard(
            displayName = authInfo.displayName,
            role = authInfo.role,
            email = authInfo.email,
            avatarUrl = authInfo.avatarUrl,
            onLogout = onLogout
        )

        val isAdmin = remember(authInfo.role, tokenStore) {
            JwtClaimsReader.isAdmin(tokenStore.getAccessToken())
        }

        if (authViewModel != null && isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCards.shape,
                colors = AppCards.defaultColors(),
                elevation = AppCards.defaultElevation()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AdminSecuritySection(
                        tokenStore = tokenStore,
                        authViewModel = authViewModel,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_appearance_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.settings_ip_lists), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_ip_lists_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_ip_lists_enable_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.settings_ip_lists_enable_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cidrListsEnabledMain,
                        onCheckedChange = { v ->
                            cidrListsEnabledMain = v
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    IpListPreferences.setCidrListsEnabled(context.applicationContext, v)
                                }
                            }
                        }
                    )
                }
                TextButton(onClick = { showIpListSettings = true }) {
                    Icon(Icons.Outlined.Route, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_ip_lists_open))
                }
            }
        }

        LocalBridgePortSettingsCard(
            poolStartText = bridgePoolStartText,
            poolEndText = bridgePoolEndText,
            savedMessageVisible = bridgePortsSavedVisible,
            onPoolStartChange = {
                bridgePoolStartText = it.filter(Char::isDigit).take(5)
                bridgePortsSavedVisible = false
            },
            onPoolEndChange = {
                bridgePoolEndText = it.filter(Char::isDigit).take(5)
                bridgePortsSavedVisible = false
            },
            onSave = {
                scope.launch {
                    val start = bridgePoolStartText.toIntOrNull()
                    val end = bridgePoolEndText.toIntOrNull()
                    if (start == null || end == null || !LocalBridgePortPool.isValidInput(start, end)) {
                        return@launch
                    }
                    val saved = withContext(Dispatchers.IO) {
                        LocalBridgePortPreferences.saveSettings(context.applicationContext, start, end)
                    }
                    bridgePoolStartText = saved.poolStart.toString()
                    bridgePoolEndText = saved.poolEnd.toString()
                    bridgePortsSavedVisible = true
                }
            },
            onReset = {
                scope.launch {
                    val defaults = withContext(Dispatchers.IO) {
                        LocalBridgePortPreferences.resetToDefaults(context.applicationContext)
                    }
                    bridgePoolStartText = defaults.poolStart.toString()
                    bridgePoolEndText = defaults.poolEnd.toString()
                    bridgePortsSavedVisible = false
                }
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppLanguageDropdown(
                    current = appLocale,
                    uiLocale = uiLocale,
                    onSelect = onAppLocaleChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_about_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KeyValueRow(
                    stringResource(R.string.settings_app_version_label),
                    stringResource(R.string.login_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            ApkUpdateInstaller.openUrl(
                                context,
                                projectWebsiteUrl
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_link_website))
                    }
                    TextButton(
                        onClick = {
                            ApkUpdateInstaller.openUrl(
                                context,
                                projectTelegramUrl
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_link_telegram))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_updates_github_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.settings_updates_github_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = githubUpdatesEnabled,
                        onCheckedChange = { v ->
                            githubUpdatesEnabled = v
                            scope.launch {
                                UpdatePreferences.setCheckEnabled(context.applicationContext, v)
                            }
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_push_updates_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.settings_push_updates_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pushNotificationsForUpdates,
                        onCheckedChange = { v ->
                            pushNotificationsForUpdates = v
                            scope.launch {
                                UpdatePreferences.setPushForUpdatesEnabled(context.applicationContext, v)
                            }
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_auto_download_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.settings_auto_download_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoDownloadSuggest,
                        onCheckedChange = { v ->
                            autoDownloadSuggest = v
                            scope.launch {
                                UpdatePreferences.setAutoDownloadEnabled(context.applicationContext, v)
                            }
                        }
                    )
                }
                Button(
                    onClick = {
                        if (!updateCheckInFlight.compareAndSet(false, true)) return@Button
                        checkingUpdates = true
                        updateCheckMessage = null
                        updateCheckIsError = false
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    UpdateManualCheck.checkNow(
                                        context = context.applicationContext,
                                        http = HttpClients.createPlain(),
                                    )
                                }
                                when (result) {
                                    is ManualUpdateCheckResult.UpdateAvailable -> {
                                        // Dialog already requested inside checkNow.
                                        updateCheckMessage = null
                                        updateCheckIsError = false
                                    }
                                    is ManualUpdateCheckResult.UpToDate -> {
                                        updateCheckIsError = false
                                        updateCheckMessage = context.getString(
                                            R.string.settings_check_updates_up_to_date,
                                            result.latestTag,
                                        )
                                    }
                                    is ManualUpdateCheckResult.AheadOfLatest -> {
                                        updateCheckIsError = false
                                        updateCheckMessage = context.getString(
                                            R.string.settings_check_updates_ahead,
                                            result.latestTag,
                                            result.installedVersion,
                                        )
                                    }
                                    is ManualUpdateCheckResult.Failed -> {
                                        updateCheckIsError = true
                                        updateCheckMessage = context.getString(
                                            R.string.settings_check_updates_failed,
                                            result.message,
                                        )
                                    }
                                    ManualUpdateCheckResult.RepoNotConfigured -> {
                                        updateCheckIsError = true
                                        updateCheckMessage =
                                            context.getString(R.string.settings_check_updates_repo_missing)
                                    }
                                }
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                updateCheckIsError = true
                                updateCheckMessage = context.getString(
                                    R.string.settings_check_updates_failed,
                                    t.message ?: t.javaClass.simpleName,
                                )
                            } finally {
                                checkingUpdates = false
                                updateCheckInFlight.set(false)
                            }
                        }
                    },
                    enabled = !checkingUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (checkingUpdates) {
                            stringResource(R.string.settings_check_updates_now_loading)
                        } else {
                            stringResource(R.string.settings_check_updates_now)
                        }
                    )
                }
                updateCheckMessage?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateCheckIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.settings_vpn_debug_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_debug_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.settings_debug_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vpnDebugModeEnabled,
                        onCheckedChange = { enabled ->
                            vpnDebugModeEnabled = enabled
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    DebugPreferences.setVpnDebugModeEnabled(
                                        context.applicationContext,
                                        enabled
                                    )
                                }
                                refreshDebugLogUi(context.applicationContext) { size, has, path, preview ->
                                    debugLogStatus = size
                                    hasDebugLogs = has
                                    debugLogPath = path
                                    debugLogPreview = preview
                                }
                            }
                        }
                    )
                }

                KeyValueRow(
                    stringResource(R.string.settings_debug_log_path),
                    debugLogPath.ifBlank { "—" }
                )
                Text(
                    stringResource(R.string.settings_debug_logs_status, debugLogStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showDebugPreview = true
                            scope.launch {
                                debugLogPreview = withContext(Dispatchers.IO) {
                                    VpnDebugLogger.get()?.readTail()
                                        ?: ""
                                }.ifBlank {
                                    context.getString(R.string.settings_debug_empty_preview)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_debug_log_preview))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                refreshDebugLogUi(context.applicationContext) { size, has, path, preview ->
                                    debugLogStatus = size
                                    hasDebugLogs = has
                                    debugLogPath = path
                                    debugLogPreview = preview
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_debug_log_refresh))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val files = getDebugLogFiles(context.applicationContext)
                            scope.launch {
                                refreshDebugLogUi(context.applicationContext) { size, has, path, preview ->
                                    debugLogStatus = size
                                    hasDebugLogs = has
                                    debugLogPath = path
                                    debugLogPreview = preview
                                }
                            }
                            if (files.isEmpty()) {
                                debugShareMessage = noDebugLogsLabel
                                return@Button
                            }
                            debugShareMessage = shareDebugLogFiles(context.applicationContext, files)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = hasDebugLogs
                    ) {
                        Text(stringResource(R.string.settings_share_debug_logs))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    VpnDebugLogger.get()?.clearLogs()
                                }
                                refreshDebugLogUi(context.applicationContext) { size, has, path, preview ->
                                    debugLogStatus = size
                                    hasDebugLogs = has
                                    debugLogPath = path
                                    debugLogPreview = preview
                                }
                                debugShareMessage = debugLogsClearedLabel
                            }
                        },
                        enabled = hasDebugLogs || vpnDebugModeEnabled
                    ) {
                        Text(stringResource(R.string.settings_clear_debug_logs))
                    }
                }

                Text(
                    stringResource(R.string.settings_debug_share_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = {
                        val path = debugLogPath
                        if (path.isNotBlank()) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("vpn_debug_path", path)
                            )
                            debugShareMessage = context.getString(R.string.copied)
                        }
                    },
                    enabled = debugLogPath.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_copy_debug_path))
                }

                debugShareMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (showDebugPreview) {
            AlertDialog(
                onDismissRequest = { showDebugPreview = false },
                title = { Text(stringResource(R.string.settings_debug_preview_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            debugLogPreview.ifBlank {
                                stringResource(R.string.settings_debug_empty_preview)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDebugPreview = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
                KeyValueRow(
                    stringResource(R.string.settings_error_logs),
                    stringResource(R.string.settings_error_logs_count, crashFilesCount)
                )

                Button(
                    onClick = {
                        val files = getCrashFiles(context.applicationContext)
                        crashFilesCount = files.size

                        if (files.isEmpty()) {
                            crashShareMessage = noErrorLogsLabel
                            return@Button
                        }

                        crashShareMessage = shareCrashFiles(context.applicationContext, files)
                    },
                    enabled = crashFilesCount > 0
                ) {
                    Text(stringResource(R.string.settings_share_logs))
                }

                crashShareMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private sealed interface IpListUpdateMessage {
    data class Failed(val error: String, val usedFallback: Boolean) : IpListUpdateMessage
    data class Ready(val routeCount: Int, val priorityRouteCount: Int) : IpListUpdateMessage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpListSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUrls by remember { mutableStateOf(IpListPreferences.DEFAULT_SOURCE_URLS) }
    var newSourceUrl by remember { mutableStateOf("") }
    var priorityUrls by remember { mutableStateOf(IpListPreferences.DEFAULT_PRIORITY_URLS) }
    var newPriorityUrl by remember { mutableStateOf("") }
    var safeRouteLimitEnabled by remember { mutableStateOf(true) }
    var updateFrequency by remember { mutableStateOf(IpListUpdateFrequency.DAILY) }
    var coverageMode by remember { mutableStateOf(IpListCoverageMode.FULL) }
    var android12OvpnRouteLimitText by remember {
        mutableStateOf(IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT.toString())
    }
    var cidrListsEnabled by remember { mutableStateOf(true) }
    var status by remember {
        mutableStateOf(
            IpListStatus(
                lastUpdatedEpochMs = null,
                loadedRouteCount = 0,
                priorityRouteCount = 0,
                lastError = null,
                reachedRouteLimit = false
            )
        )
    }
    var savedMessageVisible by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<IpListUpdateMessage?>(null) }
    var updateInProgress by remember { mutableStateOf(false) }
    val repository = remember(context.applicationContext) {
        IpListRoutesRepository(
            appContext = context.applicationContext,
            http = HttpClients.createPlain()
        )
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            IpListPreferences.getSettings(context.applicationContext) to
                IpListPreferences.getStatus(context.applicationContext)
        }
        val settings = loaded.first
        sourceUrls = settings.sourceUrls
        priorityUrls = settings.priorityUrls
        safeRouteLimitEnabled = settings.safeRouteLimitEnabled
        updateFrequency = settings.updateFrequency
        coverageMode = settings.coverageMode
        android12OvpnRouteLimitText = settings.android12OvpnRouteLimit.toString()
        cidrListsEnabled = settings.cidrListsEnabled
        status = loaded.second
    }

    val trimmedNewSourceUrl = newSourceUrl.trim()
    val newUrlError = remember(trimmedNewSourceUrl) {
        trimmedNewSourceUrl.isNotEmpty() && !isHttpUrl(trimmedNewSourceUrl)
    }
    val trimmedNewPriorityUrl = newPriorityUrl.trim()
    val newPriorityUrlError = remember(trimmedNewPriorityUrl) {
        trimmedNewPriorityUrl.isNotEmpty() && !isHttpUrl(trimmedNewPriorityUrl)
    }
    val android12OvpnRouteLimit = android12OvpnRouteLimitText.toIntOrNull()
    val android12OvpnRouteLimitError = android12OvpnRouteLimit == null ||
        android12OvpnRouteLimit !in IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT..IpListRouteConfig.MAX_ANDROID12_OVPN_ROUTE_LIMIT
    val hasSourceUrlError = sourceUrls.any { !isHttpUrl(it) }
    val hasPriorityUrlError = priorityUrls.any { !isHttpUrl(it) }
    val canSaveSources = sourceUrls.isNotEmpty() && !hasSourceUrlError &&
        !hasPriorityUrlError && !android12OvpnRouteLimitError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Text(
                stringResource(R.string.settings_ip_lists_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_ip_lists_enable_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.settings_ip_lists_enable_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cidrListsEnabled,
                        onCheckedChange = { v ->
                            cidrListsEnabled = v
                            savedMessageVisible = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    IpListPreferences.setCidrListsEnabled(context.applicationContext, v)
                                }
                            }
                        }
                    )
                }
                if (!cidrListsEnabled) {
                    Text(
                        stringResource(R.string.settings_ip_lists_detail_disabled_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_ip_lists_source_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_ip_lists_source_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sourceUrls.forEachIndexed { index, url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = url,
                                onValueChange = { value ->
                                    sourceUrls = sourceUrls.toMutableList().also {
                                        it[index] = value
                                    }
                                    savedMessageVisible = false
                                },
                                label = { Text(stringResource(R.string.settings_ip_lists_url_label)) },
                                isError = url.isNotBlank() && !isHttpUrl(url),
                                singleLine = true
                            )
                            TextButton(
                                onClick = {
                                    sourceUrls = sourceUrls.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                    savedMessageVisible = false
                                },
                                enabled = sourceUrls.size > 1
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newSourceUrl,
                    onValueChange = {
                        newSourceUrl = it
                        savedMessageVisible = false
                    },
                    label = { Text(stringResource(R.string.settings_ip_lists_add_url_label)) },
                    supportingText = {
                        Text(
                            if (newUrlError) {
                                stringResource(R.string.settings_ip_lists_url_error)
                            } else {
                                stringResource(R.string.settings_ip_lists_url_hint)
                            }
                        )
                    },
                    isError = newUrlError,
                    singleLine = true
                )
                Button(
                    onClick = {
                        sourceUrls = (sourceUrls + trimmedNewSourceUrl).map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                        newSourceUrl = ""
                        savedMessageVisible = false
                    },
                    enabled = trimmedNewSourceUrl.isNotEmpty() && !newUrlError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape
                ) {
                    Text(stringResource(R.string.settings_ip_lists_add_url))
                }

                IpListFrequencyDropdown(
                    current = updateFrequency,
                    onSelect = {
                        updateFrequency = it
                        savedMessageVisible = false
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    IpListCoverageModeSelector(
                        current = coverageMode,
                        onSelect = {
                            coverageMode = it
                            savedMessageVisible = false
                        }
                    )
                } else {
                    Android12OvpnRouteLimitField(
                        value = android12OvpnRouteLimitText,
                        isError = android12OvpnRouteLimitError,
                        onValueChange = {
                            android12OvpnRouteLimitText = it.filter(Char::isDigit).take(4)
                            savedMessageVisible = false
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    stringResource(R.string.settings_ip_lists_priority_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.settings_ip_lists_priority_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorityUrls.forEachIndexed { index, url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = url,
                                onValueChange = { value ->
                                    priorityUrls = priorityUrls.toMutableList().also {
                                        it[index] = value
                                    }
                                    savedMessageVisible = false
                                },
                                label = { Text(stringResource(R.string.settings_ip_lists_url_label)) },
                                isError = url.isNotBlank() && !isHttpUrl(url),
                                singleLine = true
                            )
                            TextButton(
                                onClick = {
                                    priorityUrls = priorityUrls.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                    savedMessageVisible = false
                                }
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newPriorityUrl,
                    onValueChange = {
                        newPriorityUrl = it
                        savedMessageVisible = false
                    },
                    label = { Text(stringResource(R.string.settings_ip_lists_priority_add_url_label)) },
                    supportingText = {
                        Text(
                            if (newPriorityUrlError) {
                                stringResource(R.string.settings_ip_lists_url_error)
                            } else {
                                stringResource(R.string.settings_ip_lists_url_hint)
                            }
                        )
                    },
                    isError = newPriorityUrlError,
                    singleLine = true
                )
                Button(
                    onClick = {
                        priorityUrls = (priorityUrls + trimmedNewPriorityUrl).map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                        newPriorityUrl = ""
                        savedMessageVisible = false
                    },
                    enabled = trimmedNewPriorityUrl.isNotEmpty() && !newPriorityUrlError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape
                ) {
                    Text(stringResource(R.string.settings_ip_lists_priority_add_url))
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_ip_lists_safe_limit_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.settings_ip_lists_safe_limit_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = safeRouteLimitEnabled,
                        onCheckedChange = { v ->
                            safeRouteLimitEnabled = v
                            savedMessageVisible = false
                        }
                    )
                }
                if (!safeRouteLimitEnabled) {
                    Text(
                        stringResource(R.string.settings_ip_lists_safe_limit_disabled_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            IpListPreferences.saveSettings(
                                context.applicationContext,
                                sourceUrls,
                                updateFrequency,
                                coverageMode,
                                android12OvpnRouteLimit
                                    ?: IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                                cidrListsEnabled,
                                priorityUrls,
                                safeRouteLimitEnabled
                            )
                            savedMessageVisible = true
                        }
                    },
                    enabled = canSaveSources,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape
                ) {
                    Text(stringResource(R.string.action_save))
                }

                if (savedMessageVisible) {
                    Text(
                        stringResource(R.string.settings_ip_lists_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_ip_lists_status_title),
                    style = MaterialTheme.typography.titleMedium
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        stringResource(R.string.settings_ip_lists_android_12_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                KeyValueRow(
                    stringResource(R.string.settings_ip_lists_last_updated),
                    status.lastUpdatedEpochMs?.let(::formatIpListUpdatedAt)
                        ?: stringResource(R.string.settings_ip_lists_last_updated_never)
                )
                KeyValueRow(
                    stringResource(R.string.settings_ip_lists_loaded_routes),
                    stringResource(R.string.settings_ip_lists_loaded_routes_value, status.loadedRouteCount)
                )
                KeyValueRow(
                    stringResource(R.string.settings_ip_lists_loaded_priority_routes),
                    stringResource(
                        R.string.settings_ip_lists_loaded_priority_routes_value,
                        status.priorityRouteCount
                    )
                )
                KeyValueRow(
                    stringResource(R.string.settings_ip_lists_last_error),
                    status.lastError ?: stringResource(R.string.settings_ip_lists_last_error_none)
                )

                if (status.reachedRouteLimit) {
                    Text(
                        stringResource(
                            R.string.settings_ip_lists_route_limit_warning,
                            IpListRouteConfig.MAX_ROUTES
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.settings_ip_lists_route_limit_notice,
                            IpListRouteConfig.MAX_ROUTES
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        updateInProgress = true
                        updateMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repository.updateNow()
                            }
                            status = withContext(Dispatchers.IO) {
                                IpListPreferences.getStatus(context.applicationContext)
                            }
                            updateMessage = result.error
                                ?.let { IpListUpdateMessage.Failed(it, result.usedFallback) }
                                ?: IpListUpdateMessage.Ready(
                                    result.routeCount,
                                    result.priorityRouteCount
                                )
                            updateInProgress = false
                        }
                    },
                    enabled = canSaveSources && !updateInProgress && cidrListsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape
                ) {
                    Text(
                        if (updateInProgress) {
                            stringResource(R.string.settings_ip_lists_update_now_loading)
                        } else {
                            stringResource(R.string.settings_ip_lists_update_now)
                        }
                    )
                }

                updateMessage?.let { message ->
                    val isError = message is IpListUpdateMessage.Failed
                    Text(
                        when (message) {
                            is IpListUpdateMessage.Failed -> {
                                if (message.usedFallback) {
                                    stringResource(
                                        R.string.settings_ip_lists_update_failed_fallback,
                                        message.error
                                    )
                                } else {
                                    stringResource(
                                        R.string.settings_ip_lists_update_failed,
                                        message.error
                                    )
                                }
                            }
                            is IpListUpdateMessage.Ready -> {
                                stringResource(
                                    R.string.settings_ip_lists_update_ready,
                                    message.routeCount,
                                    message.priorityRouteCount
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalBridgePortSettingsCard(
    poolStartText: String,
    poolEndText: String,
    savedMessageVisible: Boolean,
    onPoolStartChange: (String) -> Unit,
    onPoolEndChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val poolStart = poolStartText.toIntOrNull()
    val poolEnd = poolEndText.toIntOrNull()
    val isValid = LocalBridgePortPool.isValidInput(poolStart, poolEnd)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.shape,
        colors = AppCards.defaultColors(),
        elevation = AppCards.defaultElevation()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.settings_bridge_ports_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_bridge_ports_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_bridge_ports_advanced_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = poolStartText,
                onValueChange = onPoolStartChange,
                label = { Text(stringResource(R.string.settings_bridge_ports_start_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = poolStartText.isNotEmpty() && !isValid,
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = poolEndText,
                onValueChange = onPoolEndChange,
                label = { Text(stringResource(R.string.settings_bridge_ports_end_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = poolEndText.isNotEmpty() && !isValid,
                supportingText = {
                    Text(
                        if (!isValid && poolStartText.isNotEmpty() && poolEndText.isNotEmpty()) {
                            stringResource(
                                R.string.settings_bridge_ports_error,
                                LocalBridgePortPool.MIN_USER_PORT,
                                LocalBridgePortPool.MAX_USER_PORT,
                                LocalBridgePortPool.MIN_POOL_SPAN,
                                LocalBridgePortPool.MAX_POOL_SPAN
                            )
                        } else {
                            stringResource(
                                R.string.settings_bridge_ports_hint,
                                LocalBridgePortPool.DEFAULT_POOL_START,
                                LocalBridgePortPool.DEFAULT_POOL_END,
                                LocalBridgePortPool.MIN_USER_PORT,
                                LocalBridgePortPool.MAX_USER_PORT,
                                LocalBridgePortPool.MIN_POOL_SPAN,
                                LocalBridgePortPool.MAX_POOL_SPAN
                            )
                        }
                    )
                },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = isValid,
                    modifier = Modifier.weight(1f),
                    shape = AppCards.shape
                ) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_bridge_ports_reset))
                }
            }
            if (savedMessageVisible) {
                Text(
                    stringResource(R.string.settings_bridge_ports_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpListFrequencyDropdown(
    current: IpListUpdateFrequency,
    onSelect: (IpListUpdateFrequency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            readOnly = true,
            value = ipListFrequencyLabel(current),
            onValueChange = {},
            label = { Text(stringResource(R.string.settings_ip_lists_frequency_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (option in IpListUpdateFrequency.entries) {
                DropdownMenuItem(
                    text = { Text(ipListFrequencyLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ipListFrequencyLabel(frequency: IpListUpdateFrequency): String =
    when (frequency) {
        IpListUpdateFrequency.SIX_HOURS -> stringResource(R.string.settings_ip_lists_frequency_6h)
        IpListUpdateFrequency.DAILY -> stringResource(R.string.settings_ip_lists_frequency_daily)
        IpListUpdateFrequency.WEEKLY -> stringResource(R.string.settings_ip_lists_frequency_weekly)
        IpListUpdateFrequency.MANUAL -> stringResource(R.string.settings_ip_lists_frequency_manual)
    }

@Composable
private fun Android12OvpnRouteLimitField(
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_ip_lists_android_12_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            stringResource(
                R.string.settings_ip_lists_android_12_description,
                IpListRouteConfig.MAX_OPENVPN_PROFILE_BYTES / 1024
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.settings_ip_lists_android_12_route_limit_label)) },
            supportingText = {
                Text(
                    if (isError) {
                        stringResource(
                            R.string.settings_ip_lists_android_12_route_limit_error,
                            IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT,
                            IpListRouteConfig.MAX_ANDROID12_OVPN_ROUTE_LIMIT
                        )
                    } else {
                        stringResource(
                            R.string.settings_ip_lists_android_12_route_limit_hint,
                            IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                            IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT,
                            IpListRouteConfig.MAX_ANDROID12_OVPN_ROUTE_LIMIT
                        )
                    }
                )
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun IpListCoverageModeSelector(
    current: IpListCoverageMode,
    onSelect: (IpListCoverageMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_ip_lists_coverage_label),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = current == IpListCoverageMode.FAST,
                onClick = { onSelect(IpListCoverageMode.FAST) },
                label = { Text(stringResource(R.string.settings_ip_lists_coverage_fast)) }
            )
            FilterChip(
                selected = current == IpListCoverageMode.FULL,
                onClick = { onSelect(IpListCoverageMode.FULL) },
                label = { Text(stringResource(R.string.settings_ip_lists_coverage_full)) }
            )
        }
        Text(
            when (current) {
                IpListCoverageMode.FAST -> stringResource(
                    R.string.settings_ip_lists_coverage_fast_description,
                    IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FAST
                )
                IpListCoverageMode.FULL -> stringResource(
                    R.string.settings_ip_lists_coverage_full_description,
                    IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isHttpUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase(Locale.US)
    return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
}

private fun formatIpListUpdatedAt(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMs))

@Composable
private fun SessionLogoutCard(
    displayName: String?,
    role: String?,
    email: String?,
    avatarUrl: String?,
    onLogout: () -> Unit
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.shape,
        colors = AppCards.defaultColors(),
        elevation = AppCards.defaultElevation()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.account_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.account_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val name = displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.em_dash)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountAvatar(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    email = email
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SessionInfoRow(
                icon = {
                    Icon(
                        Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = stringResource(R.string.label_role),
                value = role?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_available)
            )
            SessionInfoRow(
                icon = {
                    Icon(
                        Icons.Outlined.MailOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = stringResource(R.string.label_email),
                value = email?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_available)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = AppCards.shape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sign_out),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.sign_out_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountAvatar(
    avatarUrl: String?,
    displayName: String?,
    email: String?
) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(context, avatarUrl) {
        bitmap = avatarUrl?.let { loadAvatarBitmap(context, it) }
    }

    val resolvedBitmap = bitmap
    if (resolvedBitmap != null) {
        Image(
            bitmap = resolvedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarInitials(displayName, email),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SessionInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val AVATAR_CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12h

private suspend fun loadAvatarBitmap(context: android.content.Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri?.scheme?.lowercase(Locale.US) != "https") {
        return@withContext null
    }

    val cacheFile = avatarCacheFile(context, url)
    val cached = decodeCachedAvatar(cacheFile)
    if (cached != null && isAvatarCacheFresh(cacheFile)) {
        return@withContext cached
    }

    runCatching {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 4_000
            readTimeout = 4_000
        }
        val bytes = connection.getInputStream().use { input -> input.readBytes() }
        val downloadedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (downloadedBitmap != null) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(cacheFile)) {
                    cacheFile.writeBytes(bytes)
                    tmp.delete()
                }
            }
            downloadedBitmap
        } else {
            cached
        }
    }.getOrElse { cached }
}

private fun avatarCacheFile(context: android.content.Context, url: String): File {
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { b -> "%02x".format(b) }
    return File(context.cacheDir, "avatar/$hash.bin")
}

private fun decodeCachedAvatar(file: File): Bitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()
}

private fun isAvatarCacheFresh(file: File): Boolean {
    if (!file.isFile) return false
    val ageMs = System.currentTimeMillis() - file.lastModified()
    return ageMs in 0..AVATAR_CACHE_TTL_MS
}

private fun avatarInitials(displayName: String?, email: String?): String {
    val source = displayName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: return "?"
    val initials = source
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
    return initials.ifBlank { "?" }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun getCrashFiles(context: android.content.Context): List<File> {
    val dir = File(context.noBackupFilesDir, "crash")
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return dir.listFiles()
        ?.filter { it.isFile && it.length() > 0L }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun shareCrashFiles(
    context: android.content.Context,
    files: List<File>
): String? {
    val appContext = context.applicationContext
    val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"

    val shareDir = File(appContext.cacheDir, "share/crash").apply { mkdirs() }
    val uris = ArrayList<Uri>(files.size)

    for (src in files) {
        try {
            val dst = File(shareDir, src.name)
            src.copyTo(dst, overwrite = true)

            val uri = FileProvider.getUriForFile(appContext, authority, dst)
            uris.add(uri)
        } catch (e: Exception) {
            android.util.Log.e("CrashShare", "Failed to prepare share file: ${src.absolutePath}", e)
        }
    }

    if (uris.isEmpty()) return context.getString(R.string.no_shareable_files)

    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(
        sendIntent,
        context.getString(R.string.share_logs_chooser_title)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        appContext.startActivity(chooserIntent)
        null
    } catch (e: Exception) {
        android.util.Log.e("CrashShare", "Failed to start chooser", e)
        e.message ?: context.getString(R.string.share_failed)
    }
}

private fun getDebugLogFiles(context: android.content.Context): List<File> {
    VpnDebugLogger.get()?.logFiles()?.let { files ->
        return files.filter { it.isFile && it.length() > 0L }
    }
    val dir = File(context.noBackupFilesDir, VpnDebugLogger.DIR_NAME)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
        ?.filter { it.isFile && it.length() > 0L && it.name.endsWith(".txt") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun formatDebugLogStatus(context: android.content.Context): String {
    val bytes = VpnDebugLogger.get()?.totalBytes()
        ?: getDebugLogFiles(context).sumOf { it.length() }
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private suspend fun refreshDebugLogUi(
    context: android.content.Context,
    apply: (size: String, has: Boolean, path: String, preview: String) -> Unit,
) {
    val snapshot = withContext(Dispatchers.IO) {
        val logger = VpnDebugLogger.get()
        val files = getDebugLogFiles(context)
        val path = logger?.currentFilePath()
            ?: File(context.noBackupFilesDir, "${VpnDebugLogger.DIR_NAME}/${VpnDebugLogger.CURRENT_FILE}").absolutePath
        val preview = logger?.readTail()?.ifBlank { "" } ?: ""
        Quad(
            size = formatDebugLogStatus(context),
            has = files.isNotEmpty(),
            path = path,
            preview = preview,
        )
    }
    apply(snapshot.size, snapshot.has, snapshot.path, snapshot.preview)
}

private data class Quad(
    val size: String,
    val has: Boolean,
    val path: String,
    val preview: String,
)

private fun shareDebugLogFiles(
    context: android.content.Context,
    files: List<File>
): String? {
    val appContext = context.applicationContext
    val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"

    val shareDir = File(appContext.cacheDir, "share/debug").apply { mkdirs() }
    val uris = ArrayList<Uri>(files.size)

    for (src in files) {
        try {
            val dst = File(shareDir, src.name)
            src.copyTo(dst, overwrite = true)
            uris.add(FileProvider.getUriForFile(appContext, authority, dst))
        } catch (e: Exception) {
            android.util.Log.e("DebugShare", "Failed to prepare share file: ${src.absolutePath}", e)
        }
    }

    if (uris.isEmpty()) return context.getString(R.string.no_shareable_files)

    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/plain"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(
        sendIntent,
        context.getString(R.string.share_debug_logs_chooser_title)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        appContext.startActivity(chooserIntent)
        null
    } catch (e: Exception) {
        android.util.Log.e("DebugShare", "Failed to start chooser", e)
        e.message ?: context.getString(R.string.share_failed)
    }
}
