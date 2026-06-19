package com.example.biap.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GeminiDarkPrimary,
    onPrimary = GeminiDarkOnPrimary,
    primaryContainer = GeminiDarkPrimaryContainer,
    onPrimaryContainer = GeminiDarkOnPrimaryContainer,
    secondary = GeminiDarkSecondary,
    onSecondary = GeminiDarkOnSecondary,
    tertiary = GeminiDarkTertiary,
    onTertiary = GeminiDarkOnTertiary,
    background = GeminiDarkBackground,
    onBackground = GeminiDarkOnBackground,
    surface = GeminiDarkSurface,
    onSurface = GeminiDarkOnSurface,
    surfaceVariant = GeminiDarkSurfaceVariant,
    onSurfaceVariant = GeminiDarkOnSurfaceVariant,
    outline = GeminiDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = GeminiLightPrimary,
    onPrimary = GeminiLightOnPrimary,
    primaryContainer = GeminiLightPrimaryContainer,
    onPrimaryContainer = GeminiLightOnPrimaryContainer,
    secondary = GeminiLightSecondary,
    onSecondary = GeminiLightOnSecondary,
    tertiary = GeminiLightTertiary,
    onTertiary = GeminiLightOnTertiary,
    background = GeminiLightBackground,
    onBackground = GeminiLightOnBackground,
    surface = GeminiLightSurface,
    onSurface = GeminiLightOnSurface,
    surfaceVariant = GeminiLightSurfaceVariant,
    onSurfaceVariant = GeminiLightOnSurfaceVariant,
    outline = GeminiLightOutline
)

object GeminiTheme {
    val lightGlowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A73E8), // Google Blue
            Color(0xFF9C27B0), // Violet / Purple
            Color(0xFF00E5FF)  // Light Cyan
        )
    )
    val darkGlowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF9EC5FF), // Soft Blue
            Color(0xFFC5A3FF), // Soft Purple
            Color(0xFF80DEEA)  // Soft Cyan
        )
    )
}

@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
