package com.imkolganov.datagate.ui.screens.settings

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.identity.InstallationIdDataStoreProvider
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    tokenStore: TokenStore,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var crashFilesCount by remember { mutableStateOf(0) }
    var crashShareMessage by remember { mutableStateOf<String?>(null) }

    var authInfo by remember { mutableStateOf(tokenStore.getAuthInfo()) }

    LaunchedEffect(Unit) {
        authInfo = withContext(Dispatchers.IO) { tokenStore.getAuthInfo() }
    }

    var installationId by remember { mutableStateOf<String?>(null) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext

        val result = withContext(Dispatchers.IO) {
            val files = getCrashFiles(appContext)
            val id = InstallationIdDataStoreProvider.getOrCreate(appContext)
            files.size to id
        }

        crashFilesCount = result.first
        installationId = result.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

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
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Light, dark, or match the device setting.",
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
                        label = { Text("Light") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        label = { Text("Dark") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        label = { Text("System") }
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
                Text("Account", style = MaterialTheme.typography.titleMedium)

                KeyValueRow("UserId", authInfo.userId ?: "Not available")
                KeyValueRow("Role", authInfo.role ?: "Not available")
                KeyValueRow("DisplayName", authInfo.displayName ?: "Not available")
                KeyValueRow("Email", authInfo.email ?: "Not available")
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
                Text("Device", style = MaterialTheme.typography.titleMedium)

                KeyValueRow("InstallationId", installationId ?: "Loading...")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val clipboard = LocalClipboard.current
                    val scope = rememberCoroutineScope()

                    Button(
                        onClick = {
                            installationId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { value ->
                                    scope.launch {
                                        val clip = ClipData.newPlainText("installationId", value)
                                        clipboard.setClipEntry(ClipEntry(clip))
                                    }
                                    copiedMessage = "Copied"
                                }
                        },
                        enabled = !installationId.isNullOrBlank()
                    ) {
                        Text("Copy InstallationId")
                    }

                    Button(
                        onClick = {
                            authInfo.userId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { value ->
                                    scope.launch {
                                        val clip = ClipData.newPlainText("userId", value)
                                        clipboard.setClipEntry(ClipEntry(clip))
                                    }
                                    copiedMessage = "Copied"
                                }
                        },
                        enabled = !authInfo.userId.isNullOrBlank()
                    ) {
                        Text("Copy UserId")
                    }
                }

                copiedMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = {
                copiedMessage = null
                onLogout()
            }
        ) {
            Text("Logout")
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
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                KeyValueRow("Error logs", "$crashFilesCount file(s)")

                Button(
                    onClick = {
                        val files = getCrashFiles(context.applicationContext)
                        crashFilesCount = files.size

                        if (files.isEmpty()) {
                            crashShareMessage = "No error logs found"
                            return@Button
                        }

                        crashShareMessage = shareCrashFiles(context.applicationContext, files)
                    },
                    enabled = crashFilesCount > 0
                ) {
                    Text("Share Error Logs")
                }

                crashShareMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
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

    if (uris.isEmpty()) return "No shareable files."

    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(sendIntent, "Share error logs").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        appContext.startActivity(chooserIntent)
        null
    } catch (e: Exception) {
        android.util.Log.e("CrashShare", "Failed to start chooser", e)
        e.message ?: "Failed to share files."
    }
}