package com.imkolganov.datagate.ui.screens.profiles

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imkolganov.datagate.profiles.LocalVpnProfile
import com.imkolganov.datagate.profiles.LocalVpnProfilesRepository
import com.imkolganov.datagate.vpn.xray.XrayCoreFacade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<LocalVpnProfile> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isImporting: Boolean = false,
)

class ProfilesViewModel(
    application: Application,
    private val repository: LocalVpnProfilesRepository,
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _ui.asStateFlow()

    val profiles: StateFlow<List<LocalVpnProfile>> =
        repository.profiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                _ui.value = _ui.value.copy(profiles = list)
            }
        }
    }

    fun clearMessages() {
        _ui.value = _ui.value.copy(errorMessage = null, infoMessage = null)
    }

    fun importOpenVpn(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isImporting = true, errorMessage = null)
            runCatching {
                repository.importOpenVpnFromUri(uri, displayName)
            }.onSuccess { profile ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    infoMessage = profile.name,
                )
            }.onFailure { t ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    errorMessage = t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }

    fun importXray(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isImporting = true, errorMessage = null)
            runCatching {
                repository.importXrayFromUri(uri, displayName, ::normalizeXray)
            }.onSuccess { profile ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    infoMessage = profile.name,
                )
            }.onFailure { t ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    errorMessage = t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }

    fun importXrayText(content: String, displayName: String?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isImporting = true, errorMessage = null)
            runCatching {
                repository.importXrayContent(content, displayName, ::normalizeXray)
            }.onSuccess { profile ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    infoMessage = profile.name,
                )
            }.onFailure { t ->
                _ui.value = _ui.value.copy(
                    isImporting = false,
                    errorMessage = t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }

    private fun normalizeXray(raw: String): String {
        if (!XrayCoreFacade.isAvailable()) {
            // JSON with outbounds can still be stored without native convert.
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val outbounds = com.imkolganov.datagate.vpn.xray.XrayConfigBuilder.extractOutbounds(trimmed)
                com.imkolganov.datagate.vpn.xray.XrayConfigBuilder.sanitizeOutboundsForRuntime(outbounds)
                return com.imkolganov.datagate.vpn.xray.XrayConfigBuilder.wrapOutboundsPreservingProfileExtras(
                    outbounds,
                    trimmed,
                )
            }
            error(getApplication<Application>().getString(com.imkolganov.datagate.R.string.profiles_error_xray_unavailable))
        }
        return XrayCoreFacade.normalizeToOutboundsConfig(raw)
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(id, name) }
                .onFailure { t ->
                    _ui.value = _ui.value.copy(errorMessage = t.message ?: t.javaClass.simpleName)
                }
        }
    }

    fun updateCredentials(id: String, username: String, password: String) {
        viewModelScope.launch {
            runCatching { repository.updateCredentials(id, username, password) }
                .onFailure { t ->
                    _ui.value = _ui.value.copy(errorMessage = t.message ?: t.javaClass.simpleName)
                }
        }
    }

    fun credentialsFor(id: String) = repository.getCredentials(id)

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { t ->
                    _ui.value = _ui.value.copy(errorMessage = t.message ?: t.javaClass.simpleName)
                }
        }
    }
}

class ProfilesViewModelFactory(
    private val application: Application,
    private val repository: LocalVpnProfilesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfilesViewModel::class.java)) {
            return ProfilesViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
