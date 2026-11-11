package com.gojapan.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {
    @Test fun lightAndDarkSemanticTextMeetWcagAA() {
        listOf(LightColors, DarkColors).forEach { c ->
            listOf(
                c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondary to c.secondary, c.onSecondaryContainer to c.secondaryContainer,
                c.onTertiary to c.tertiary, c.onTertiaryContainer to c.tertiaryContainer,
                c.onBackground to c.background, c.onSurface to c.surface,
                c.onSurfaceVariant to c.surfaceVariant, c.onError to c.error,
                c.onErrorContainer to c.errorContainer, c.inverseOnSurface to c.inverseSurface,
                c.onSurface to c.surfaceContainer, c.onSurface to c.surfaceContainerHigh,
            ).forEach { (text, background) ->
                val contrast = (maxOf(luminance(text), luminance(background)) + .05) /
                    (minOf(luminance(text), luminance(background)) + .05)
                assertTrue("Text contrast $contrast is below 4.5:1", contrast >= 4.5)
            }
        }
    }
    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= .04045f) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
}
