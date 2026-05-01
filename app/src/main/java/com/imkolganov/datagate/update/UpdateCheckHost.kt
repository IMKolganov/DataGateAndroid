package com.imkolganov.datagate.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun UpdateCheckHost(
    isLoggedIn: Boolean,
    http: OkHttpClient
) {
    val activity = LocalContext.current as ComponentActivity
    val appContext = activity.applicationContext
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf<GitHubLatestRelease?>(null) }
    var autoDownloadNext by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var showInstallPermissionHint by remember { mutableStateOf(false) }

    val dismissUpdateDialog = rememberUpdatedState {
        showInstallPermissionHint = false
        prompt = null
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ApkUpdateInstaller.tryContinuePendingInstall(activity) {
                    dismissUpdateDialog.value()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(prompt) {
        if (prompt == null) showInstallPermissionHint = false
    }

    val manualDialogRequest by UpdatePromptController.showUpdateDialog.collectAsState()

    LaunchedEffect(manualDialogRequest) {
        val r = manualDialogRequest ?: return@LaunchedEffect
        autoDownloadNext = UpdatePreferences.isAutoDownloadEnabled(appContext)
        prompt = r
        UpdatePromptController.consumeUpdateDialogRequest()
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            prompt = null
            return@LaunchedEffect
        }
        delay(2500)
        val release = withContext(Dispatchers.IO) {
            if (!UpdatePreferences.shouldRunCheck(appContext)) return@withContext null
            val repo = BuildConfig.GITHUB_RELEASES_REPO
            if (repo.isBlank()) return@withContext null
            val fetcher = GitHubLatestReleaseFetcher(http)
            val result = fetcher.fetchLatest(repo)
            UpdatePreferences.markCheckDone(appContext)
            val r = result.getOrNull() ?: return@withContext null
            val current = BuildConfig.VERSION_NAME
            if (!SemanticVersionCompare.isRemoteNewer(r.tagName, current)) {
                UpdatePreferences.clearCachedNewerRelease(appContext)
                return@withContext null
            }
            val dismissed = UpdatePreferences.getDismissedTag(appContext)
            if (r.tagName == dismissed) return@withContext null
            UpdatePreferences.saveCachedNewerRelease(appContext, r)
            UpdateNotificationHelper.showNewVersionAvailableIfEligible(appContext, r)
            r
        }
        if (release != null) {
            autoDownloadNext = UpdatePreferences.isAutoDownloadEnabled(appContext)
            prompt = release
        }
    }

    prompt?.let { rel ->
        AlertDialog(
            onDismissRequest = { if (!downloading) prompt = null },
            title = {
                Text(stringResource(R.string.update_dialog_title, rel.tagName))
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.update_dialog_body,
                            rel.tagName,
                            BuildConfig.VERSION_NAME
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (rel.apkDownloadUrl == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.update_no_apk_attached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showInstallPermissionHint) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.update_return_after_allow_install),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoDownloadNext,
                            onCheckedChange = { autoDownloadNext = it },
                            enabled = !downloading
                        )
                        Text(
                            stringResource(R.string.update_dialog_auto_download_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { ApkUpdateInstaller.openUrl(activity, rel.htmlUrl) },
                        enabled = !downloading
                    ) {
                        Text(stringResource(R.string.update_dialog_open_release))
                    }
                    downloadError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                val apkUrl = rel.apkDownloadUrl
                if (apkUrl != null) {
                    TextButton(
                        enabled = !downloading,
                        onClick = {
                            val url = apkUrl
                            scope.launch {
                                downloading = true
                                downloadError = null
                                UpdatePreferences.setAutoDownloadEnabled(appContext, autoDownloadNext)
                                val file = withContext(Dispatchers.IO) {
                                    ApkUpdateInstaller.downloadApkToCache(activity, http, url)
                                }
                                downloading = false
                                file.fold(
                                    onSuccess = { apk ->
                                        downloadError = null
                                        when (val result = ApkUpdateInstaller.startInstall(activity, apk)) {
                                            ApkUpdateInstaller.InstallUiResult.InstallerStarted -> {
                                                showInstallPermissionHint = false
                                                prompt = null
                                            }
                                            ApkUpdateInstaller.InstallUiResult.OpenedInstallPermissionSettings -> {
                                                showInstallPermissionHint = true
                                            }
                                            ApkUpdateInstaller.InstallUiResult.Failed -> {
                                                showInstallPermissionHint = false
                                                downloadError =
                                                    appContext.getString(R.string.update_install_failed)
                                            }
                                        }
                                    },
                                    onFailure = { e ->
                                        downloadError =
                                            e.message ?: appContext.getString(R.string.update_download_failed)
                                    }
                                )
                            }
                        }
                    ) {
                        Text(
                            if (downloading) {
                                stringResource(R.string.update_downloading)
                            } else {
                                stringResource(R.string.update_dialog_download)
                            }
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            ApkUpdateInstaller.openUrl(activity, rel.htmlUrl)
                            scope.launch {
                                UpdatePreferences.setAutoDownloadEnabled(appContext, autoDownloadNext)
                                UpdatePreferences.dismissRelease(appContext, rel.tagName)
                            }
                            prompt = null
                        }
                    ) {
                        Text(stringResource(R.string.update_dialog_open_release))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        scope.launch {
                            UpdatePreferences.setAutoDownloadEnabled(appContext, autoDownloadNext)
                            UpdatePreferences.dismissRelease(appContext, rel.tagName)
                        }
                        prompt = null
                    }
                ) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        )
    }
}
