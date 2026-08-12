package com.sc.biap.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
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

// Same derivation as `warning`: read from the APPLIED scheme, not the system setting.
val ColorScheme.success: Color
    get() = if (background.luminance() < 0.5f) HeritageDarkSuccess else HeritageLightSuccess

// Same derivation as `warning`/`success` (BUG-20): read the APPLIED scheme's
// background, not the system dark-mode setting, so a forced theme stays consistent.
val ColorScheme.glowBrush: Brush
    get() = if (background.luminance() < 0.5f) AtelierTheme.darkGlowBrush else AtelierTheme.lightGlowBrush

object AtelierTheme {
    // BUG-28 fix: brushes reference the palette constants so a palette change propagates.
    val lightGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageLightPrimary, HeritageLightSecondary, HeritageGlowGreyLight)
    )
    val darkGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageDarkPrimary, HeritageDarkSecondary, HeritageGlowCharcoalDark)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color stays OFF to protect the Contemporary Heritage brand identity.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Material generates the whole tonal palette from ONE brand seed.
  //
  // Every container role - primaryContainer, secondaryContainer, tertiaryContainer and
  // their "on" pairs - is derived on the same hue family at correct tonal distances. That
  // matters for Expressive specifically: its look comes from content sitting inside filled
  // tonal containers rather than on flat surfaces, and hand-picking a dozen tints that
  // stay harmonious AND accessible in both light and dark is work nobody wins at.
  //
  // Seeded from the brass in the mark, not from the wallpaper. Google's own Expressive
  // demos are wallpaper-seeded, which is why they all read purple; a business tool wants
  // one identity on every phone.
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      else -> rememberDynamicColorScheme(
        seedColor = BiapSeed,
        isDark = darkTheme,
        isAmoled = false,
        // Fidelity, NOT PaletteStyle.Expressive - the name is a trap. That style
        // deliberately hue-ROTATES away from the seed, which turned brass into purple on
        // device. It refers to the palette algorithm, not the M3 Expressive design system;
        // the Expressive LOOK comes from MaterialExpressiveTheme, the shapes and the
        // Emphasized type roles, none of which care which hue the palette carries.
        // Fidelity keeps primary faithful to the brand colour.
        style = PaletteStyle.Fidelity,
      )
    }

  // Same colours and type; what this adds is the expressive MotionScheme, so every
  // Material component animates on spring curves instead of standard easing.
  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

/**
 * Expressive corner scale.
 *
 * Material 3 Expressive reads as soft, generous containers rather than tight rectangles -
 * that shape language is doing as much work as the colour. These were 8/12/16dp, which
 * fought it. Pill is a true stadium: Compose caps a percent-based corner at half the
 * shorter side, so it stays a pill at any height.
 */
object AtelierShapes {
    val CornerSmall = RoundedCornerShape(12.dp)
    val CornerMedium = RoundedCornerShape(20.dp)
    val CornerLarge = RoundedCornerShape(28.dp)
    val CornerExtraLarge = RoundedCornerShape(36.dp)
    val Pill = RoundedCornerShape(percent = 50)
}
