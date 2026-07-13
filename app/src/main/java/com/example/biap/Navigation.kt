package com.example.biap

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.biap.ui.PerformanceProfile
import com.example.biap.ui.main.MainScreen
import com.example.biap.ui.main.SplashScreen
import com.example.biap.ui.design.AddDesignScreen
import com.example.biap.ui.design.DesignDetailScreen
import com.example.biap.ui.rememberPerformanceProfile

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Splash)
  val profile = rememberPerformanceProfile()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    transitionSpec = {
      if (profile == PerformanceProfile.HIGH_FIDELITY) {
        val springOffset = spring<IntOffset>(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
        val springFade = spring<Float>(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
        slideInHorizontally(animationSpec = springOffset) { it } + fadeIn(animationSpec = springFade) togetherWith
                slideOutHorizontally(animationSpec = springOffset) { -it } + fadeOut(animationSpec = springFade)
      } else {
        fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 150))
      }
    },
    popTransitionSpec = {
      if (profile == PerformanceProfile.HIGH_FIDELITY) {
        val springOffset = spring<IntOffset>(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
        val springFade = spring<Float>(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
        slideInHorizontally(animationSpec = springOffset) { -it } + fadeIn(animationSpec = springFade) togetherWith
                slideOutHorizontally(animationSpec = springOffset) { it } + fadeOut(animationSpec = springFade)
      } else {
        fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 150))
      }
    },
    entryProvider =
      entryProvider {
        entry<Splash> {
          SplashScreen(
            onSplashComplete = {
              backStack.removeLastOrNull()
              backStack.add(Main)
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<AddDesign> {
          AddDesignScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<DesignDetail> { key ->
          DesignDetailScreen(
            designId = key.designId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}
