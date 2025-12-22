package com.imkolganov.datagate.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.imkolganov.datagate.stats.StatsApiClient

class StatsViewModelFactory(
    private val api: StatsApiClient,
    private val externalIdProvider: () -> String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StatsViewModel(api, externalIdProvider) as T
    }
}
