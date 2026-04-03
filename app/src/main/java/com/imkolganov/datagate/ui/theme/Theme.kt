package com.imkolganov.datagate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = AppLightColors.primary,
    onPrimary = AppLightColors.onPrimary,
    primaryContainer = AppLightColors.primaryContainer,
    onPrimaryContainer = AppLightColors.onPrimaryContainer,
    inversePrimary = AppLightColors.inversePrimary,
    secondary = AppLightColors.secondary,
    onSecondary = AppLightColors.onSecondary,
    secondaryContainer = AppLightColors.secondaryContainer,
    onSecondaryContainer = AppLightColors.onSecondaryContainer,
    tertiary = AppLightColors.tertiary,
    onTertiary = AppLightColors.onTertiary,
    tertiaryContainer = AppLightColors.tertiaryContainer,
    onTertiaryContainer = AppLightColors.onTertiaryContainer,
    background = AppLightColors.background,
    onBackground = AppLightColors.onBackground,
    surface = AppLightColors.surface,
    onSurface = AppLightColors.onSurface,
    surfaceVariant = AppLightColors.surfaceVariant,
    onSurfaceVariant = AppLightColors.onSurfaceVariant,
    inverseSurface = AppLightColors.inverseSurface,
    inverseOnSurface = AppLightColors.inverseOnSurface,
    error = AppLightColors.error,
    onError = AppLightColors.onError,
    errorContainer = AppLightColors.errorContainer,
    onErrorContainer = AppLightColors.onErrorContainer,
    outline = AppLightColors.outline,
    outlineVariant = AppLightColors.outlineVariant,
    scrim = AppLightColors.scrim,
    surfaceBright = AppLightColors.surfaceBright,
    surfaceContainer = AppLightColors.surfaceContainer,
    surfaceContainerHigh = AppLightColors.surfaceContainerHigh,
    surfaceContainerHighest = AppLightColors.surfaceContainerHighest,
    surfaceContainerLow = AppLightColors.surfaceContainerLow,
    surfaceContainerLowest = AppLightColors.surfaceContainerLowest,
    surfaceDim = AppLightColors.surfaceDim,
    primaryFixed = AppLightColors.primaryFixed,
    primaryFixedDim = AppLightColors.primaryFixedDim,
    onPrimaryFixed = AppLightColors.onPrimaryFixed,
    onPrimaryFixedVariant = AppLightColors.onPrimaryFixedVariant,
    secondaryFixed = AppLightColors.secondaryFixed,
    secondaryFixedDim = AppLightColors.secondaryFixedDim,
    onSecondaryFixed = AppLightColors.onSecondaryFixed,
    onSecondaryFixedVariant = AppLightColors.onSecondaryFixedVariant,
    tertiaryFixed = AppLightColors.tertiaryFixed,
    tertiaryFixedDim = AppLightColors.tertiaryFixedDim,
    onTertiaryFixed = AppLightColors.onTertiaryFixed,
    onTertiaryFixedVariant = AppLightColors.onTertiaryFixedVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = AppDarkColors.primary,
    onPrimary = AppDarkColors.onPrimary,
    primaryContainer = AppDarkColors.primaryContainer,
    onPrimaryContainer = AppDarkColors.onPrimaryContainer,
    inversePrimary = AppDarkColors.inversePrimary,
    secondary = AppDarkColors.secondary,
    onSecondary = AppDarkColors.onSecondary,
    secondaryContainer = AppDarkColors.secondaryContainer,
    onSecondaryContainer = AppDarkColors.onSecondaryContainer,
    tertiary = AppDarkColors.tertiary,
    onTertiary = AppDarkColors.onTertiary,
    tertiaryContainer = AppDarkColors.tertiaryContainer,
    onTertiaryContainer = AppDarkColors.onTertiaryContainer,
    background = AppDarkColors.background,
    onBackground = AppDarkColors.onBackground,
    surface = AppDarkColors.surface,
    onSurface = AppDarkColors.onSurface,
    surfaceVariant = AppDarkColors.surfaceVariant,
    onSurfaceVariant = AppDarkColors.onSurfaceVariant,
    inverseSurface = AppDarkColors.inverseSurface,
    inverseOnSurface = AppDarkColors.inverseOnSurface,
    error = AppDarkColors.error,
    onError = AppDarkColors.onError,
    errorContainer = AppDarkColors.errorContainer,
    onErrorContainer = AppDarkColors.onErrorContainer,
    outline = AppDarkColors.outline,
    outlineVariant = AppDarkColors.outlineVariant,
    scrim = AppDarkColors.scrim,
    surfaceBright = AppDarkColors.surfaceBright,
    surfaceContainer = AppDarkColors.surfaceContainer,
    surfaceContainerHigh = AppDarkColors.surfaceContainerHigh,
    surfaceContainerHighest = AppDarkColors.surfaceContainerHighest,
    surfaceContainerLow = AppDarkColors.surfaceContainerLow,
    surfaceContainerLowest = AppDarkColors.surfaceContainerLowest,
    surfaceDim = AppDarkColors.surfaceDim,
    primaryFixed = AppDarkColors.primaryFixed,
    primaryFixedDim = AppDarkColors.primaryFixedDim,
    onPrimaryFixed = AppDarkColors.onPrimaryFixed,
    onPrimaryFixedVariant = AppDarkColors.onPrimaryFixedVariant,
    secondaryFixed = AppDarkColors.secondaryFixed,
    secondaryFixedDim = AppDarkColors.secondaryFixedDim,
    onSecondaryFixed = AppDarkColors.onSecondaryFixed,
    onSecondaryFixedVariant = AppDarkColors.onSecondaryFixedVariant,
    tertiaryFixed = AppDarkColors.tertiaryFixed,
    tertiaryFixedDim = AppDarkColors.tertiaryFixedDim,
    onTertiaryFixed = AppDarkColors.onTertiaryFixed,
    onTertiaryFixedVariant = AppDarkColors.onTertiaryFixedVariant
)

@Composable
fun DataGateAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * When false (default), uses the app blue palette on all API levels — consistent and calm for most users.
     * Set true to use the system wallpaper palette on Android 12+ (more variation, less predictable).
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
