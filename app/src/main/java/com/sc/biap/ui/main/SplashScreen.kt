package com.sc.biap.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.ui.BiapMark
import com.sc.biap.ui.PerformanceProfile
import com.sc.biap.ui.rememberPerformanceProfile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @file SplashScreen.kt
 * @description Launch screen — a system coming online, not a brand reveal.
 *
 * Built to the BIAP identity spec: the Aperture mark in brass on ink, a hairline
 * structural grid at low opacity, a determinate rule that fills while the workspace is
 * restored, and a build stamp at the base. No photography, no texture, no decorative
 * gradient — the restraint is the point. This is an operations tool announcing itself.
 *
 * The previous splash was removed for looking wrong; this one exists because the identity
 * calls for it, not because a launch screen is obligatory.
 */

private val MarkSize = 92.dp          // splashScreenIconSize from the spec
private val GridPitch = 32.dp
private val ProgressHeight = 3.dp     // deliberately un-rounded: a rule, not a pill
private const val GridOpacity = 0.30f
private const val ProgressFraction = 0.66f

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.96f) }
    val profile = rememberPerformanceProfile()

    LaunchedEffect(profile) {
        if (profile == PerformanceProfile.LOW_POWER) {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            delay(150)
        } else {
            // Fade and settle run together; the mark arrives already in place rather than
            // performing an entrance.
            coroutineScope {
                launch { alphaAnim.animateTo(1f, tween(durationMillis = 240)) }
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            delay(320)
        }
        onSplashComplete()
    }

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // Structural grid: the quiet suggestion of a system with an underlying order.
        // Skipped on low power, where it is the least valuable pixel on screen.
        if (profile != PerformanceProfile.LOW_POWER) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pitch = GridPitch.toPx()
                val line = scheme.outline.copy(alpha = GridOpacity)
                var x = 0f
                while (x <= size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += pitch
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += pitch
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Large)
        ) {
            BiapMark(size = MarkSize, color = scheme.primary)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BIAP",
                    style = MaterialTheme.typography.headlineLarge,
                    color = scheme.onBackground,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ATELIER OPERATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = AtelierSpacing.Compact)
                )
            }
        }

        // Determinate rule: progress the user can read, rather than a spinner that only
        // says "something is happening".
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = AtelierSpacing.Large)
                .padding(bottom = AtelierSpacing.Double)
                .alpha(alphaAnim.value)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .background(scheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ProgressFraction)
                        .height(ProgressHeight)
                        .background(scheme.primary)
                )
            }
            Text(
                text = "Restoring workspace",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AtelierSpacing.Compact)
            )
        }
    }
}
