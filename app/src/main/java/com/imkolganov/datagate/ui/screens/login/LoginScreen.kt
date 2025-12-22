package com.imkolganov.datagate.ui.screens.login

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.auth.AuthViewModel

@Composable
fun LoginScreenContent(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DataGate", style = MaterialTheme.typography.headlineSmall)

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = onLoginClick) {
                            Text("Sign in with Google")
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state = viewModel.state.collectAsState().value
    val context = LocalContext.current
    val activity = context as? Activity

    LoginScreenContent(
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        onLoginClick = {
            if (activity != null) {
                viewModel.login(activity)
            }
        }
    )
}
