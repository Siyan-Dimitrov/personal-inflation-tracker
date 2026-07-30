package com.siyandimitrov.pocketindex.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Aubergine,
    onPrimary = WarmWhite,
    primaryContainer = Lavender,
    onPrimaryContainer = AubergineDark,
    secondary = Sage,
    onSecondary = WarmWhite,
    secondaryContainer = SageSurface,
    onSecondaryContainer = Sage,
    error = Coral,
    errorContainer = CoralSurface,
    background = WarmWhite,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = MutedInk,
    outline = OutlineSoft,
)

private val DarkColors = darkColorScheme(
    primary = ColorTokens.PurpleLight,
    onPrimary = AubergineDark,
    primaryContainer = Aubergine,
    onPrimaryContainer = Lavender,
    secondary = ColorTokens.SageLight,
    onSecondary = DarkBackground,
    secondaryContainer = ColorTokens.SageDark,
    onSecondaryContainer = SageSurface,
    error = ColorTokens.CoralLight,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = ColorTokens.DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    outline = ColorTokens.DarkOutline,
)

private object ColorTokens {
    val PurpleLight = androidx.compose.ui.graphics.Color(0xFFDDB7F0)
    val SageLight = androidx.compose.ui.graphics.Color(0xFFA9D5B5)
    val SageDark = androidx.compose.ui.graphics.Color(0xFF264B32)
    val CoralLight = androidx.compose.ui.graphics.Color(0xFFFFB4AD)
    val DarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C252D)
    val DarkOutline = androidx.compose.ui.graphics.Color(0xFF554B55)
}

@Composable
fun PocketIndexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = PocketIndexTypography,
        content = content,
    )
}

