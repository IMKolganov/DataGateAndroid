package com.imkolganov.datagate.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.theme.ThemeMode
import java.util.Locale
import com.imkolganov.datagate.update.ApkUpdateInstaller
import com.imkolganov.datagate.update.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    tokenStore: TokenStore,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit
) {
    val context = LocalContext.current
    val uiLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val noErrorLogsLabel = stringResource(R.string.no_error_logs)
    var crashFilesCount by remember { mutableStateOf(0) }
    var crashShareMessage by remember { mutableStateOf<String?>(null) }
    var githubUpdatesEnabled by remember { mutableStateOf(true) }
    var autoDownloadSuggest by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var authInfo by remember { mutableStateOf(tokenStore.getAuthInfo()) }

    LaunchedEffect(Unit) {
        authInfo = withContext(Dispatchers.IO) { tokenStore.getAuthInfo() }
    }

    LaunchedEffect(Unit) {
        crashFilesCount = withContext(Dispatchers.IO) {
            getCrashFiles(context.applicationContext).size
        }
    }

    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        githubUpdatesEnabled = withContext(Dispatchers.IO) {
            UpdatePreferences.isCheckEnabled(appCtx)
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
            onLogout = onLogout
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
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LanguageDropdown(
                    current = appLocale,
                    uiLocale = uiLocale,
                    onSelect = onAppLocaleChange
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
                                context.getString(R.string.project_website_url)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.settings_link_website))
                    }
                    TextButton(
                        onClick = {
                            ApkUpdateInstaller.openUrl(
                                context,
                                context.getString(R.string.project_telegram_url)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    current: AppLocale,
    uiLocale: Locale,
    onSelect: (AppLocale) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = when (current) {
        AppLocale.SYSTEM -> stringResource(R.string.language_system)
        else -> current.displayLabel(uiLocale)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            readOnly = true,
            value = currentLabel,
            onValueChange = {},
            label = { Text(stringResource(R.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (option in AppLocale.pickerOrder) {
                val optionLabel = when (option) {
                    AppLocale.SYSTEM -> stringResource(R.string.language_system)
                    else -> option.displayLabel(uiLocale)
                }
                DropdownMenuItem(
                    text = { Text(optionLabel) },
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
private fun SessionLogoutCard(
    displayName: String?,
    role: String?,
    email: String?,
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
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

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