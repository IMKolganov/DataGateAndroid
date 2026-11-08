package com.imkolganov.datagate.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.imkolganov.datagate.R
import com.imkolganov.datagate.network.ClientIdentityEvents

/**
 * Shown once per app run when the backend refuses this build's client identity.
 * The session stays as it is: the user only needs a newer app version.
 */
@Composable
fun ClientOutdatedDialog() {
    var visible by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ClientIdentityEvents.outdated.collect {
            if (!dismissed) visible = true
        }
    }

    if (!visible) return
    AlertDialog(
        onDismissRequest = {
            visible = false
            dismissed = true
        },
        title = { Text(stringResource(R.string.client_outdated_title)) },
        text = {
            Text(
                stringResource(R.string.client_outdated_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    visible = false
                    dismissed = true
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}
