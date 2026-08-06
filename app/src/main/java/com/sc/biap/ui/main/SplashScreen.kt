package com.sc.biap.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.R
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.HeritageDarkBackground
import com.sc.biap.theme.HeritageDarkPrimary
import com.sc.biap.theme.HeritageDarkTertiary
import com.sc.biap.ui.PerformanceProfile
import com.sc.biap.ui.rememberPerformanceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @file SplashScreen.kt
 * @description Premium splash screen with Contemporary Heritage styling.
 *
 * Modernisation notes (material3 1.4.0 stable — no Expressive components available):
 * - Colour comes from the Heritage palette, not inline hex.
 * - Spacing comes from AtelierSpacing, not raw dp.
 * - Type comes from the theme scale, with brand weight/tracking preserved via copy().
 * - Motion is spring-based rather than linear tween, and the two properties now run
 *   concurrently with a short stagger instead of end-to-end.
 * The logo card keeps deliberate 90-degree corners ("The Cut") — that is brand intent,
 * not an un-modernised leftover, so it is not rounded.
 */

// Logo sizing is artwork geometry rather than layout rhythm, so it stays explicit
// rather than being forced onto the AtelierSpacing scale.
private val LogoCardSize = 160.dp
private val LogoArtSize = 128.dp

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.92f) }
    val profile = rememberPerformanceProfile()

    LaunchedEffect(profile) {
        if (profile == PerformanceProfile.LOW_POWER) {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            delay(150)
        } else {
            // Run fade and settle together. Previously these were awaited one after the
            // other, so the scale only began once the fade had finished.
            launch {
                alphaAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                )
            }
            scaleAnim.animateTo(
                targetValue = 1f,
                // Expressive motion signature: the mark settles into place rather than
                // arriving on a fixed linear ramp.
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            delay(400)
        }
        onSplashComplete()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-bleed backdrop: draped silk under saffron light.
        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Scrim for contrast and depth.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeritageDarkBackground.copy(alpha = 0.65f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
                .padding(AtelierSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(LogoCardSize)
                    .border(AtelierSpacing.Hairline, HeritageDarkPrimary.copy(alpha = 0.25f))
                    .background(HeritageDarkBackground.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "biap Atelier Logo",
                    modifier = Modifier
                        .size(LogoArtSize)
                        .alpha(0.9f),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(AtelierSpacing.Large))

            // Wordmark: theme display scale, brand's editorial light weight retained.
            Text(
                text = "biap",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light
                ),
                color = HeritageDarkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(AtelierSpacing.Compact))

            // Tagline: theme label scale, brand's tracked-caps treatment retained.
            Text(
                text = "THE PRECISION ATELIER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 5.sp
                ),
                color = HeritageDarkTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
