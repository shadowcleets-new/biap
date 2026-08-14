package com.sc.biap.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * @file BaselineProfileGenerator.kt
 * @description Records which classes and methods the app touches on the way up.
 *
 * A baseline profile is a list of code that Android should compile ahead of time instead of
 * interpreting and JIT-ing on first run. It is the single biggest performance lever available
 * without changing a line of app code, and it helps startup and early scrolling most.
 *
 * WHAT THIS JOURNEY CAN AND CANNOT REACH:
 *   The generator runs on a clean emulator, so the app starts with no signed-in account and
 *   no catalogue. It therefore cannot walk to the Inventory screen - it gets as far as the
 *   sign-in choice.
 *
 *   That is a smaller journey than it looks. Everything expensive about starting a Compose
 *   app happens before any of the app's own screens do: the Compose runtime and compiler
 *   output, the Material 3 theme resolving dynamic colour from the wallpaper, the splash, the
 *   navigation host, and the first composition and layout pass. Those are shared with every
 *   screen, so profiling them still speeds up reaching the catalogue.
 *
 *   What it does NOT cover is code only the catalogue runs - the size matrix, the row
 *   layouts, Coil decoding a photo. Covering those would mean signing in on the emulator with
 *   real credentials, which is not something a build should carry.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.sc.biap",
        // Repeats until the collected profile stops changing rather than a fixed count, so a
        // slow emulator does not silently produce a thinner profile than a fast one.
        stableIterations = 3,
        maxIterations = 8,
    ) {
        // 1. Cold start. This is the part that matters most and the part a profile fixes best.
        pressHome()
        startActivityAndWait()

        // 2. Let the first frame settle. The splash hands over to the auth screen, and both
        //    compositions are worth recording.
        device.waitForIdle()

        // 3. Exercise scrolling machinery on whatever is on screen. Compose's scroll and
        //    fling code is shared by every list in the app, so recording it here still pays
        //    off on the catalogue - even though the catalogue itself is out of reach.
        //
        //    Coordinate-based rather than findObject(): a selector that misses would throw and
        //    fail the whole generation, and this journey must not depend on any one label
        //    surviving a redesign.
        repeat(2) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.7).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                10,
            )
            device.waitForIdle()
        }
    }
}
