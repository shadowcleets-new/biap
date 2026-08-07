package com.sc.biap

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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sc.biap.ui.PerformanceProfile
import com.sc.biap.ui.main.MainScreen
import com.sc.biap.ui.main.SplashScreen
import com.sc.biap.ui.design.AddDesignScreen
import com.sc.biap.ui.design.DesignDetailScreen
import com.sc.biap.ui.rememberPerformanceProfile

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Splash)
  val profile = rememberPerformanceProfile()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Scope ViewModels to the NAV ENTRY, not the Activity.
    //
    // NavDisplay's default is listOf(rememberSaveableStateHolderNavEntryDecorator()) only -
    // no ViewModelStore decorator. Without this, viewModel { } inside an entry resolves
    // against the Activity's store, so every screen's ViewModel is an Activity-lifetime
    // singleton that outlives the entry that created it. That caused two bugs:
    //
    //   * AddDesignScreen: saveDesign() sets isSaved = true and nothing ever resets it, so
    //     the second visit composed with isSaved already true, LaunchedEffect(state.isSaved)
    //     fired immediately and popped the entry on its first frame - the screen appeared
    //     not to open at all.
    //   * DesignDetailScreen: viewModel { } keys on the class, so opening design B reused
    //     design A's DesignDetailViewModel and showed the wrong design.
    //
    // Adding the decorator gives each entry its own ViewModelStore, cleared when the entry
    // pops. The default must be listed explicitly - passing this parameter replaces it.
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
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
