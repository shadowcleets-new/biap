package com.sc.biap.ui.main

import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.sc.biap.ui.rememberPerformanceProfile
import com.sc.biap.ui.PerformanceProfile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.R
import kotlinx.coroutines.delay

/**
 * @file SplashScreen.kt
 * @description Premium, high-definition splash screen with Contemporary Heritage styling.
 */

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.95f) }
    val profile = rememberPerformanceProfile()

    LaunchedEffect(profile) {
        if (profile == PerformanceProfile.LOW_POWER) {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            delay(150)
        } else {
            // Animate the logo fade-in and subtle scale-up
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600)
            )
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 600)
            )
            delay(500)
        }
        onSplashComplete()
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Full bleed vertical background image of draped silk under saffron light
        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark luxury scrim to enhance contrast and depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131313).copy(alpha = 0.65f))
        )

        // Main Brand Display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stylized logo card representing "The Cut" (strict 90-degree sharp corners)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(1.dp, Color(0xFFF5F2E9).copy(alpha = 0.25f))
                    .background(Color(0xFF131313).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "biap Atelier Logo",
                    modifier = Modifier
                        .size(128.dp)
                        .alpha(0.9f),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App title in Noto Serif style (Serif)
            Text(
                text = "biap",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light,
                fontSize = 42.sp,
                color = Color(0xFFF5F2E9), // Canvas Cream
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline in tracked Space Grotesk style
            Text(
                text = "THE PRECISION ATELIER",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 5.sp, // Tracked caps
                color = Color(0xFF8C8A84), // Dust grey
                textAlign = TextAlign.Center
            )
        }
    }
}
