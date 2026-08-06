package com.sc.biap.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sc.biap.ui.PerformanceProfile
import com.sc.biap.ui.rememberPerformanceProfile

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

// BUG-20 fix: derive the warning color from the APPLIED scheme, not the system setting.
val ColorScheme.warning: Color
    get() = if (background.luminance() < 0.5f) HeritageDarkWarning else HeritageLightWarning

object AtelierTheme {
    // BUG-28 fix: brushes reference the palette constants so a palette change propagates.
    val lightGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageLightPrimary, HeritageLightSecondary, HeritageGlowGreyLight)
    )
    val darkGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageDarkPrimary, HeritageDarkSecondary, HeritageGlowCharcoalDark)
    )
}

@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color stays OFF to protect the Contemporary Heritage brand identity.
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

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

object AtelierShapes {
    val CornerSmall = RoundedCornerShape(8.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge = RoundedCornerShape(16.dp)
}
