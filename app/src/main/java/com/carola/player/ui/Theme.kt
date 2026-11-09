package com.carola.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val DefaultAccent = Color(0xFFFF8A3D)
val Background = Color(0xFF0E0F13)
val SurfaceDark = Color(0xFF17181D)
val RailColor = Color(0xFF09090C)

@Composable
fun CarolaTheme(accent: Color, content: @Composable () -> Unit) {
    val animated by animateColorAsState(accent, tween(900), label = "accent")
    val onAccent = if (animated.luminance() > 0.45f) Color(0xFF111111) else Color.White
    val scheme = darkColorScheme(
        primary = animated,
        onPrimary = onAccent,
        secondary = animated,
        onSecondary = onAccent,
        secondaryContainer = animated.copy(alpha = 0.22f),
        onSecondaryContainer = Color.White,
        background = Background,
        onBackground = Color.White,
        surface = SurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF23252C),
        onSurfaceVariant = Color(0xFFB8BAC2),
        surfaceContainer = SurfaceDark,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        // Surface fija el color de texto por defecto en blanco para toda la app
        Surface(modifier = Modifier.fillMaxSize(), color = Background, contentColor = Color.White) {
            content()
        }
    }
}
