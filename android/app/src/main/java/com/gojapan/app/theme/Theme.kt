package com.gojapan.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = JapanRed,
    background = RicePaper,
    surface = RicePaper,
    onBackground = Ink,
    onSurface = Ink,
)
private val DarkColors = darkColorScheme(primary = JapanRedDark)

@Composable
fun GoJapanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
