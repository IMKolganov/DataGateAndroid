package com.imkolganov.datagate.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Shared panels: tonal surface color, no per-card shadow (scroll-friendly on phone).
 */
object AppCards {
    val shape = RoundedCornerShape(16.dp)

    @Composable
    fun defaultColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    @Composable
    fun defaultElevation() = CardDefaults.cardElevation(defaultElevation = 0.dp)
}
