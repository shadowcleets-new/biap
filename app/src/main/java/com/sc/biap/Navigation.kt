package com.sc.biap

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ContentTransform
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
import androidx.navigationevent.NavigationEvent
import com.sc.biap.ui.PerformanceProfile
import com.sc.biap.ui.main.MainScreen
import com.sc.biap.ui.main.SplashScreen
import com.sc.biap.ui.design.AddDesignScreen
import com.sc.biap.ui.design.DesignDetailScreen
import com.sc.biap.ui.orders.NewOrderScreen
import com.sc.biap.ui.settings.SettingsScreen
import com.sc.biap.ui.profile.AccessManagementScreen
import com.sc.biap.ui.hub.BusinessHubScreen
import com.sc.biap.ui.profile.ProfileScreen
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
    /**
     * The back GESTURE, scrubbed by the finger rather than played on release.
     *
     * Navigation 3 ships a sensible default for this, but it ignores which edge the swipe
     * came from and it does not know about this app's performance profile - so on a
     * low-power device it would run springs while every other transition here had already
     * dropped to a plain fade.
     *
     * The outgoing screen shrinks and slides TOWARDS the edge being dragged from, which is
     * what the platform does between activities: the screen you are leaving follows your
     * thumb, so the gesture reads as pulling it away rather than as an animation that
     * happens to you.
     */
    predictivePopTransitionSpec = { edge ->
      if (profile == PerformanceProfile.HIGH_FIDELITY) {
        val towardsThumb = if (edge == NavigationEvent.EDGE_LEFT) 1 else -1
        // Effects spring, damping 1.0 - the incoming screen must not overshoot while a
        // finger is still deciding whether to commit.
        val settle = spring<Float>(dampingRatio = 1f, stiffness = 1600f)
        ContentTransform(
          targetContentEnter = fadeIn(animationSpec = settle),
          initialContentExit = scaleOut(targetScale = 0.9f, animationSpec = settle) +
            slideOutHorizontally(animationSpec = spring(dampingRatio = 1f, stiffness = 1600f)) {
              (it * 0.12f).toInt() * towardsThumb
            } + fadeOut(animationSpec = settle),
        )
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
        entry<NewOrder> { key ->
          NewOrderScreen(
            orderId = key.orderId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<Profile> {
          ProfileScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<Settings> {
          SettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<BusinessHub> {
          BusinessHubScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<AccessManagement> {
          AccessManagementScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<DesignDetail> { key ->
          DesignDetailScreen(
            designId = key.designId,
            focusSize = key.focusSize,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}
