package com.imkolganov.datagate.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens the same update dialog as [UpdateCheckHost] (Home banner, notification tap,
 * or Settings → Check now).
 */
object UpdatePromptController {

    private val _showUpdateDialog = MutableStateFlow<GitHubLatestRelease?>(null)
    val showUpdateDialog: StateFlow<GitHubLatestRelease?> = _showUpdateDialog.asStateFlow()

    fun requestUpdateDialog(release: GitHubLatestRelease) {
        _showUpdateDialog.value = release
    }

    fun consumeUpdateDialogRequest() {
        _showUpdateDialog.value = null
    }
}
