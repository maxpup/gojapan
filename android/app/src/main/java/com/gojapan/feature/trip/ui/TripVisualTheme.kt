package com.gojapan.feature.trip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TripLight = lightColorScheme(
    primary = Color(0xFF242424), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE68B), onPrimaryContainer = Color(0xFF332D18),
    secondary = Color(0xFF365E50), secondaryContainer = Color(0xFFD9EEE3), onSecondaryContainer = Color(0xFF263D33),
    tertiary = Color(0xFF71505B), tertiaryContainer = Color(0xFFF8DFE5), onTertiaryContainer = Color(0xFF493039),
    background = Color(0xFFF6F3EE), onBackground = Color(0xFF242424),
    surface = Color(0xFFFFFCF8), onSurface = Color(0xFF242424),
    surfaceVariant = Color(0xFFE1EFF7), onSurfaceVariant = Color(0xFF64615C),
    surfaceContainerLow = Color(0xFFFFFCF8),
    surfaceContainer = Color(0xFFF0ECE5),
    outline = Color(0xFF8B8780), outlineVariant = Color(0xFFE7E1D8),
)
private val TripDark = darkColorScheme(
    primary = Color(0xFFFFE68B), onPrimary = Color(0xFF242424),
    primaryContainer = Color(0xFF50462A), onPrimaryContainer = Color(0xFFFFE9A2),
    secondaryContainer = Color(0xFF2E473D), onSecondaryContainer = Color(0xFFD9EEE3),
    tertiaryContainer = Color(0xFF513B44), onTertiaryContainer = Color(0xFFF8DFE5),
    background = Color(0xFF201F1D), onBackground = Color(0xFFF6F3EE),
    surface = Color(0xFF2A2926), onSurface = Color(0xFFF6F3EE),
    surfaceVariant = Color(0xFF30434E), onSurfaceVariant = Color(0xFFCCC6BD),
    surfaceContainerLow = Color(0xFF2A2926), surfaceContainer = Color(0xFF34322E),
    outlineVariant = Color(0xFF4C4841),
)

@Composable
internal fun TripVisualTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) TripDark else TripLight,
        typography = MaterialTheme.typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
