package com.example.evaluacin_consolidado2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AlertCuscoColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Surface,
    primaryContainer = PaleTeal,
    onPrimaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    error = Critical,
    errorContainer = PaleCritical,
)

@Composable
fun AlertCuscoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AlertCuscoColors, typography = Typography, content = content)
}
