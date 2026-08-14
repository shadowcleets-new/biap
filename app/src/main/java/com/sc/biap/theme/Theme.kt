package com.sc.biap.theme

import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
  /**
   * Resolved from [ThemePreference] and the power state, not passed in by callers.
   *
   * AUTO follows the system, except while battery saver is on, when it drops to AMOLED -
   * true black costs an OLED panel almost nothing to display. An explicit choice is always
   * honoured, including a choice of Light while saving power: overriding somebody's stated
   * preference to save a little battery is the app deciding it knows better.
   */
  darkTheme: Boolean = isSystemInDarkTheme(),
  /**
   * Seed the palette from the device wallpaper. ON by default.
   *
   * This is what Material 3 Expressive actually does, and why every screen in Google's
   * announcement reads purple - their wallpaper was purple, not their brand. The colours
   * are supposed to be picked automatically and follow the person using the phone.
   */
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val mode by ThemePreference.mode.collectAsState()
  val powerSaving = rememberPowerSaveMode()

  // AUTO is the only mode battery saver may override. Anything explicit is left alone.
  val resolved = when (mode) {
    ThemeMode.AUTO -> if (powerSaving) ThemeMode.AMOLED else if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT
    else -> mode
  }
  val isDark = resolved != ThemeMode.LIGHT

  val base =
    when {
      // API 31+: Material derives the entire tonal system from the wallpaper.
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      // Below 31 there is no wallpaper extraction, so fall back to Material's own
      // baseline scheme rather than inventing a brand hue - picking one here would
      // reintroduce exactly the fixed colour this is meant to avoid.
      isDark -> darkColorScheme()
      else -> lightColorScheme()
    }

  /**
   * AMOLED is the dark scheme with its backgrounds pulled to true black.
   *
   * Derived rather than hand-written so it keeps the wallpaper-seeded hues - only the
   * surfaces that cover large areas go to #000000, which is what actually saves power on an
   * OLED panel, since a black pixel is an unlit pixel. The container roles stay slightly
   * lifted; flattening those too would erase the elevation the whole layout depends on.
   */
  val colorScheme = if (resolved == ThemeMode.AMOLED) {
    base.copy(
      background = Color.Black,
      surface = Color.Black,
      surfaceDim = Color.Black,
      surfaceContainerLowest = Color.Black,
      surfaceContainerLow = base.surfaceContainerLowest,
      surfaceContainer = base.surfaceContainerLow,
    )
  } else {
    base
  }

  /**
   * The system bars take their icon colour from OUR theme, not the system's.
   *
   * Edge-to-edge means the app paints behind the status and navigation bars, so the bars
   * carry no background of their own and their icons have to contrast with whatever this
   * app has drawn up there. `enableEdgeToEdge()` on its own decides that from the SYSTEM
   * dark-mode setting, which is the wrong question here: this app owns its theme, and a
   * phone in dark mode showing an app set to Light got light icons on a light background -
   * the clock and the battery effectively disappeared.
   *
   * Driven from `isDark` - the theme actually applied, after the AUTO and battery-saver
   * rules have run - so the bars follow AMOLED, a forced Light, and a system change alike.
   *
   * SideEffect rather than LaunchedEffect: this is pushing state into a non-Compose object
   * that must match the frame being drawn, and it has to run on every successful
   * composition, not once per key change.
   */
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
      }
      // Android's own translucent scrim behind the gesture bar would sit on top of the
      // app's background and read as a band the layout did not ask for.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
      }
    }
  }

  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    // Stated rather than left to the default, because it is the whole point of the language.
    //
    // Expressive replaces duration-based easing with springs, and splits them in two:
    // SPATIAL springs (position, size, shape) run at damping 0.6-0.8 so movement overshoots
    // slightly and settles - that overshoot IS the liveliness. EFFECTS springs (colour,
    // opacity) are pinned at damping 1.0 so a tint never wobbles.
    //
    // Values read out of ExpressiveMotionTokens in material3 1.5.0-alpha25:
    //   spatial  default 0.8/380   fast 0.6/800   slow 0.8/200
    //   effects  default 1.0/1600  fast 1.0/3800  slow 1.0/800
    //
    // Applied app-wide on purpose. Two motion personalities in one app reads as a bug
    // rather than a choice.
    motionScheme = MotionScheme.expressive(),
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

/**
 * Whether the device is in battery saver, kept live.
 *
 * Registered as a receiver rather than read once: battery saver turns on by itself at a
 * threshold, and a theme that only noticed at launch would sit in the wrong mode for the
 * rest of the session.
 */
@Composable
private fun rememberPowerSaveMode(): Boolean {
  val context = LocalContext.current
  val power = remember {
    context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
  }
  var saving by remember { mutableStateOf(power?.isPowerSaveMode ?: false) }

  DisposableEffect(power) {
    if (power == null) return@DisposableEffect onDispose {}
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context?, i: Intent?) {
        saving = power.isPowerSaveMode
      }
    }
    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }
  return saving
}
