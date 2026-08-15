package com.imkolganov.datagate.ui.screens.profiles

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.profiles.LocalVpnProfile
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.components.VpnServerTypeIcon
import com.imkolganov.datagate.ui.components.VpnServerTypeLabel
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.vpn.VpnStatusUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    vpnState: VpnStatusUiState,
    onConnectProfile: (String) -> Unit,
    onDisconnectVpn: () -> Unit,
    primaryFocusRequester: FocusRequester? = null,
) {
    val ui by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalVpnProfile?>(null) }
    var credsTarget by remember { mutableStateOf<LocalVpnProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalVpnProfile?>(null) }
    var showXrayPaste by remember { mutableStateOf(false) }

    val importOvpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var displayName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(idx)
            }
        }
        viewModel.importOpenVpn(uri, displayName)
    }

    val importXrayFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var displayName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(idx)
            }
        }
        viewModel.importXray(uri, displayName)
    }

    LaunchedEffect(ui.errorMessage, ui.infoMessage) {
        ui.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        ui.infoMessage?.let { name ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.profiles_imported, name)
            )
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.tvFocusBorder(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.profiles_add))
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_import_ovpn)) },
                        onClick = {
                            showAddMenu = false
                            importOvpnLauncher.launch(arrayOf("*/*", "application/x-openvpn-profile", "text/plain"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_import_xray_link)) },
                        onClick = {
                            showAddMenu = false
                            showXrayPaste = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_import_xray_file)) },
                        onClick = {
                            showAddMenu = false
                            importXrayFileLauncher.launch(arrayOf("*/*", "application/json", "text/plain"))
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (ui.profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profiles_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profiles_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        importOvpnLauncher.launch(arrayOf("*/*", "application/x-openvpn-profile", "text/plain"))
                    },
                    modifier = if (primaryFocusRequester != null) Modifier.tvFocusBorder() else Modifier,
                ) {
                    Text(stringResource(R.string.profiles_import_ovpn))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showXrayPaste = true },
                    modifier = Modifier.tvFocusBorder(),
                ) {
                    Text(stringResource(R.string.profiles_import_xray_link))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        vpnState = vpnState,
                        onConnect = { onConnectProfile(profile.id) },
                        onDisconnect = onDisconnectVpn,
                        onRename = { renameTarget = profile },
                        onEditCreds = { credsTarget = profile },
                        onDelete = { deleteTarget = profile },
                    )
                }
            }
        }
    }

    renameTarget?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.profiles_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profiles_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(profile.id, name)
                        renameTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    credsTarget?.let { profile ->
        val existing = remember(profile.id) { viewModel.credentialsFor(profile.id) }
        var username by remember(profile.id) { mutableStateOf(existing.username) }
        var password by remember(profile.id) { mutableStateOf(existing.password) }
        AlertDialog(
            onDismissRequest = { credsTarget = null },
            title = { Text(stringResource(R.string.profiles_creds_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.profiles_creds_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profiles_username_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profiles_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateCredentials(profile.id, username, password)
                        credsTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { credsTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.profiles_delete_title)) },
            text = {
                Text(stringResource(R.string.profiles_delete_message, profile.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(profile.id)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.profiles_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showXrayPaste) {
        var paste by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showXrayPaste = false },
            title = { Text(stringResource(R.string.profiles_xray_paste_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.profiles_xray_paste_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profiles_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = paste,
                        onValueChange = { paste = it },
                        label = { Text(stringResource(R.string.profiles_xray_paste_label)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showXrayPaste = false
                        viewModel.importXrayText(paste, name.ifBlank { null })
                    },
                    enabled = paste.isNotBlank(),
                ) {
                    Text(stringResource(R.string.profiles_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showXrayPaste = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: LocalVpnProfile,
    vpnState: VpnStatusUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRename: () -> Unit,
    onEditCreds: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isThisSession =
        vpnState.selectedServerName == profile.name &&
            (vpnState.isVpnConnected || vpnState.isConnectRequested)
    val canConnect =
        profile.type == VpnServerType.OpenVpn || profile.type == VpnServerType.Xray

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.shape,
        colors = AppCards.defaultColors(),
        elevation = AppCards.defaultElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                VpnServerTypeIcon(serverType = profile.type)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    VpnServerTypeLabel(serverType = profile.type)
                    when (profile.type) {
                        VpnServerType.OpenVpn -> {
                            Text(
                                text = stringResource(R.string.profiles_transport_direct),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        VpnServerType.Xray -> {
                            Text(
                                text = stringResource(R.string.profiles_transport_xray),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        VpnServerType.Unknown -> Unit
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    if (profile.type == VpnServerType.OpenVpn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profiles_edit_creds)) },
                            onClick = {
                                menuOpen = false
                                onEditCreds()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profiles_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (canConnect) {
                if (isThisSession && vpnState.isVpnConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_disconnect))
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !vpnState.isConnectRequested || vpnState.isVpnConnected,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_connect))
                    }
                }
            }
        }
    }
}
