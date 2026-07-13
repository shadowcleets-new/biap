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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.ColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = HeritageDarkPrimary,
    onPrimary = HeritageDarkOnPrimary,
    primaryContainer = HeritageDarkPrimaryContainer,
    onPrimaryContainer = HeritageDarkOnPrimaryContainer,
    secondary = HeritageDarkSecondary,
    onSecondary = HeritageDarkOnSecondary,
    tertiary = HeritageDarkTertiary,
    onTertiary = HeritageDarkOnTertiary,
    background = HeritageDarkBackground,
    onBackground = HeritageDarkOnBackground,
    surface = HeritageDarkSurface,
    onSurface = HeritageDarkOnSurface,
    surfaceVariant = HeritageDarkSurfaceVariant,
    onSurfaceVariant = HeritageDarkOnSurfaceVariant,
    outline = HeritageDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = HeritageLightPrimary,
    onPrimary = HeritageLightOnPrimary,
    primaryContainer = HeritageLightPrimaryContainer,
    onPrimaryContainer = HeritageLightOnPrimaryContainer,
    secondary = HeritageLightSecondary,
    onSecondary = HeritageLightOnSecondary,
    tertiary = HeritageLightTertiary,
    onTertiary = HeritageLightOnTertiary,
    background = HeritageLightBackground,
    onBackground = HeritageLightOnBackground,
    surface = HeritageLightSurface,
    onSurface = HeritageLightOnSurface,
    surfaceVariant = HeritageLightSurfaceVariant,
    onSurfaceVariant = HeritageLightOnSurfaceVariant,
    outline = HeritageLightOutline
)

val ColorScheme.warning: Color
    @Composable
    get() = if (isSystemInDarkTheme()) HeritageDarkWarning else HeritageLightWarning

object AtelierTheme {
    val lightGlowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1C1B18), // Warm Charcoal
            Color(0xFF8C7A5C), // Muted Bronze
            Color(0xFFD1CFC7)  // Light Outline Grey
        )
    )
    val darkGlowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF5F2E9), // Canvas Cream
            Color(0xFFB8A78A), // Soft Bronze Accent
            Color(0xFF3C3D40)  // Muted Charcoal
        )
    )
}

@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set dynamic color override to false to protect premium brand identity
  dynamicColor: Boolean = false,
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


object AtelierShapes {
    val CornerSmall = RoundedCornerShape(8.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge = RoundedCornerShape(16.dp)
}

