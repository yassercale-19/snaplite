package com.snaplite.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF16006B),
    secondary = Color(0xFF00A88F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F5EC),
    onSecondaryContainer = Color(0xFF00201A),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1B1A21),
    surface = Color(0xFFFBF9FF),
    onSurface = Color(0xFF1B1A21),
    surfaceVariant = Color(0xFFEAE6F2),
    onSurfaceVariant = Color(0xFF48454F),
    surfaceContainer = Color(0xFFF1EDF8),
    surfaceContainerHighest = Color(0xFFEAE6F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B2FF),
    onPrimary = Color(0xFF2A1C9E),
    primaryContainer = Color(0xFF4133C9),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Color(0xFF4FDCC2),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045),
    onSecondaryContainer = Color(0xFFC8F5EC),
    background = Color(0xFF111016),
    onBackground = Color(0xFFE6E1EC),
    surface = Color(0xFF111016),
    onSurface = Color(0xFFE6E1EC),
    surfaceVariant = Color(0xFF2B2A33),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceContainer = Color(0xFF1B1A21),
    surfaceContainerHighest = Color(0xFF2B2A33),
)

@Composable
fun SnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
