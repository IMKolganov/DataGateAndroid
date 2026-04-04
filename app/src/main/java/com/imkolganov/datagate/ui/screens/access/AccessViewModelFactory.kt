package com.imkolganov.datagate.ui.screens.access

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AccessViewModelFactory(
    private val repo: AccessRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccessViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccessViewModel(repo, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
