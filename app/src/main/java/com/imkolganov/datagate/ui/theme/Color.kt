package com.imkolganov.datagate.ui.theme

import androidx.compose.ui.graphics.Color

/* --- Legacy names (dark / non-brand) — kept for any reference --- */
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Light theme: blue-forward, cool-tinted surfaces (avoid flat “office gray”).
 * Aligned with Material 3 expressiveness: clear primary, soft containers, readable neutrals with a blue bias.
 */
object AppLightColors {
    val primary = Color(0xFF1565C0)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFD3E4FD)
    val onPrimaryContainer = Color(0xFF001C38)

    val secondary = Color(0xFF3E5F8A)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFD7E2F5)
    val onSecondaryContainer = Color(0xFF101C2C)

    val tertiary = Color(0xFF006874)
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFB2EBF2)
    val onTertiaryContainer = Color(0xFF001F24)

    val background = Color(0xFFF5F8FF)
    val onBackground = Color(0xFF191C20)

    val surface = Color(0xFFF8FAFF)
    val onSurface = Color(0xFF191C20)
    val surfaceVariant = Color(0xFFE2EAF5)
    val onSurfaceVariant = Color(0xFF3E4754)

    val outline = Color(0xFF707F8F)
    val outlineVariant = Color(0xFFC0CAD6)

    /** M3 surface ladder + inverse/scrim — must be set explicitly or Material defaults read pink/purple. */
    val surfaceDim = Color(0xFFE8EDF7)
    val surfaceBright = Color(0xFFFBFCFF)
    val surfaceContainerLowest = Color(0xFFE6EBF5)
    val surfaceContainerLow = Color(0xFFEEF2FA)
    val surfaceContainer = Color(0xFFF0F4FB)
    val surfaceContainerHigh = Color(0xFFF4F7FD)
    val surfaceContainerHighest = Color(0xFFF7F9FE)

    val inverseSurface = Color(0xFF2C3038)
    val inverseOnSurface = Color(0xFFEFF1F5)
    val inversePrimary = Color(0xFFAAC7FF)

    val scrim = Color(0x52000000)

    val primaryFixed = Color(0xFFD3E4FD)
    val primaryFixedDim = Color(0xFFB8CCEB)
    val onPrimaryFixed = Color(0xFF051C36)
    val onPrimaryFixedVariant = Color(0xFF384A63)

    val secondaryFixed = Color(0xFFD7E2F5)
    val secondaryFixedDim = Color(0xFFBBC8DF)
    val onSecondaryFixed = Color(0xFF101C2C)
    val onSecondaryFixedVariant = Color(0xFF3E4754)

    val tertiaryFixed = Color(0xFFB2EBF2)
    val tertiaryFixedDim = Color(0xFF96D4DE)
    val onTertiaryFixed = Color(0xFF001F24)
    val onTertiaryFixedVariant = Color(0xFF1D444B)

    val error = Color(0xFFBA1A1A)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF410002)
}

/**
 * Dark theme: same blue family, comfortable contrast (when not using dynamic wallpaper colors).
 */
object AppDarkColors {
    val primary = Color(0xFF9ECAFF)
    val onPrimary = Color(0xFF003258)
    val primaryContainer = Color(0xFF284777)
    val onPrimaryContainer = Color(0xFFD3E4FD)

    val secondary = Color(0xFFB8C8E8)
    val onSecondary = Color(0xFF223148)
    val secondaryContainer = Color(0xFF3A4A63)
    val onSecondaryContainer = Color(0xFFD7E2F5)

    val tertiary = Color(0xFF4FD8EB)
    val onTertiary = Color(0xFF00363C)
    val tertiaryContainer = Color(0xFF004F58)
    val onTertiaryContainer = Color(0xFFB2EBF2)

    val background = Color(0xFF101418)
    val onBackground = Color(0xFFE1E2E8)

    val surface = Color(0xFF101418)
    val onSurface = Color(0xFFE1E2E8)
    val surfaceVariant = Color(0xFF3E4754)
    val onSurfaceVariant = Color(0xFFC1C8D4)

    val outline = Color(0xFF8C9199)
    val outlineVariant = Color(0xFF43474E)

    val surfaceDim = Color(0xFF080A0D)
    val surfaceBright = Color(0xFF1A1E26)
    val surfaceContainerLowest = Color(0xFF060708)
    val surfaceContainerLow = Color(0xFF14181C)
    val surfaceContainer = Color(0xFF181C22)
    val surfaceContainerHigh = Color(0xFF1E232C)
    val surfaceContainerHighest = Color(0xFF242A34)

    val inverseSurface = Color(0xFFE1E2E8)
    val inverseOnSurface = Color(0xFF191C20)
    val inversePrimary = Color(0xFF284777)

    val scrim = Color(0x52000000)

    val primaryFixed = Color(0xFFD3E4FD)
    val primaryFixedDim = Color(0xFFAAC7FF)
    val onPrimaryFixed = Color(0xFF051C36)
    val onPrimaryFixedVariant = Color(0xFF384A63)

    val secondaryFixed = Color(0xFFD7E2F5)
    val secondaryFixedDim = Color(0xFFB8C8E8)
    val onSecondaryFixed = Color(0xFF101C2C)
    val onSecondaryFixedVariant = Color(0xFF3E4754)

    val tertiaryFixed = Color(0xFFB2EBF2)
    val tertiaryFixedDim = Color(0xFF4FD8EB)
    val onTertiaryFixed = Color(0xFF001F24)
    val onTertiaryFixedVariant = Color(0xFF1D444B)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)
}
