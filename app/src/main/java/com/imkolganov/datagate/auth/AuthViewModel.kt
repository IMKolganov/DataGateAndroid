package com.imkolganov.datagate.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.imkolganov.datagate.util.deepMessageForApiError
import com.imkolganov.datagate.util.userFriendlyApiError

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(isLoggedIn = repo.isLoggedIn())
    )
    val state: StateFlow<AuthUiState> = _state

    fun login(activity: Activity) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repo.loginWithGoogle(activity)
                _state.update { it.copy(isLoading = false, isLoggedIn = true) }
            } catch (e: Exception) {
                val msg = activity.resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun logout() {
        repo.logout()
        _state.update { it.copy(isLoggedIn = false, errorMessage = null) }
    }
}

class AuthViewModelFactory(
    private val repo: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
