package com.gojapan.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal val LightColors = with(TravelColors) { lightColorScheme(
    primary = green, onPrimary = white, primaryContainer = paleGreen, onPrimaryContainer = ink,
    secondary = green, onSecondary = white, secondaryContainer = paleGreen, onSecondaryContainer = ink,
    tertiary = mutedInk, onTertiary = white, tertiaryContainer = paleGreen, onTertiaryContainer = ink,
    background = paper, onBackground = ink, surface = paper, onSurface = ink,
    surfaceVariant = paleGreen, onSurfaceVariant = mutedInk,
    surfaceDim = paleGreen, surfaceBright = white,
    surfaceContainerLowest = white, surfaceContainerLow = white, surfaceContainer = white,
    surfaceContainerHigh = paleGreen, surfaceContainerHighest = paleGreen,
    outline = outline, outlineVariant = outlineVariant,
    error = error, onError = white, errorContainer = errorContainer, onErrorContainer = error,
    inverseSurface = nightSurface, inverseOnSurface = nightInk, inversePrimary = nightGreen,
    surfaceTint = green,
) }
internal val DarkColors = with(TravelColors) { darkColorScheme(
    primary = nightGreen, onPrimary = onNightGreen, primaryContainer = nightContainer, onPrimaryContainer = nightInk,
    secondary = nightGreen, onSecondary = onNightGreen, secondaryContainer = nightContainer, onSecondaryContainer = nightInk,
    tertiary = nightMuted, onTertiary = night, tertiaryContainer = nightContainer, onTertiaryContainer = nightInk,
    background = night, onBackground = nightInk, surface = nightSurface, onSurface = nightInk,
    surfaceVariant = nightContainer, onSurfaceVariant = nightMuted,
    surfaceDim = night, surfaceBright = nightContainer,
    surfaceContainerLowest = night, surfaceContainerLow = nightSurface, surfaceContainer = nightSurface,
    surfaceContainerHigh = nightContainer, surfaceContainerHighest = nightContainer,
    outline = nightOutline, outlineVariant = nightOutlineVariant,
    error = nightError, onError = nightErrorContainer, errorContainer = nightErrorContainer, onErrorContainer = nightError,
    inverseSurface = paper, inverseOnSurface = ink, inversePrimary = green,
    surfaceTint = nightGreen,
) }

/** Fixed semantic colors keep learning feedback consistent; system appearance still selects light/dark. */
@Composable
fun GoJapanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) { content() }
    }
}
