package com.imkolganov.datagate.ui.screens.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AccessViewModelFactory(
    private val repo: AccessRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccessViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccessViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
