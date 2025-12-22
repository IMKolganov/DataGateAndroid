package com.imkolganov.datagate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import android.app.Activity

@Composable
fun SystemBars() {
    val view = LocalView.current
    val window = (view.context as Activity).window
    val colors = MaterialTheme.colorScheme

    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()

        val controller = WindowInsetsControllerCompat(window, view)

        // Dark theme → light icons = false
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}
