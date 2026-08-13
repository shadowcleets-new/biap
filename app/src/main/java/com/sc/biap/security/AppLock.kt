package com.sc.biap.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @file AppLock.kt
 * @description Whether the catalogue is currently hidden behind the device lock.
 *
 * WHY THIS EXISTS:
 *   The app holds cost prices, margins and karigar records. A phone left on a workbench,
 *   lent to someone, or lost should not hand all of that over to whoever picks it up. The
 *   device is already protected at boot, but a phone in daily use spends most of its time
 *   unlocked in someone's hand.
 *
 * THREE THINGS THIS DELIBERATELY GETS RIGHT:
 *
 *   1. It never locks a user out. If the device has no biometric enrolled AND no PIN,
 *      pattern or password, there is nothing to authenticate against, so the lock disables
 *      itself rather than making the catalogue permanently unreachable. Authentication
 *      accepts the device credential as well as a fingerprint, so a phone whose sensor
 *      fails is not a phone that has lost its data.
 *
 *   2. It does not prompt when you pick a photo. Choosing an image or taking one hands the
 *      screen to another app, which looks exactly like leaving BIAP. Without [beginTrustedFlow]
 *      the fingerprint prompt would appear every single time a photo is attached - which is
 *      the fastest way to make someone turn a security feature off.
 *
 *   3. It has a grace period. Glancing at a notification and coming straight back is not a
 *      security event, and treating it as one trains people to resent the lock.
 *
 * State lives in the process, never on disk: locking is a UI gate over data that is already
 * encrypted at rest, and a flag on disk would add nothing an attacker could not flip.
 */
object AppLock {

    /** Returning within this window skips re-authentication. */
    private const val GRACE_PERIOD_MS = 60_000L

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAt = 0L

    /**
     * Set while a system picker or the camera is in front of us. Those look identical to
     * the app being backgrounded, but the user never left - they are still completing a
     * task inside BIAP.
     */
    private var trustedFlowDepth = 0

    private var enabled = false

    /** The lifecycle observer is attached once per process, regardless of [enabled]. */
    private var observing = false

    /**
     * Turn the lock off for this process and unlock immediately.
     *
     * Called when the owner switches it off in Settings. Unlocking here matters: leaving the
     * gate up after disabling it would strand them behind a lock they just removed.
     */
    fun disable() {
        enabled = false
        _isLocked.value = false
        backgroundedAt = 0L
    }

    /** Turn the lock back on. Does not lock the current session - they are looking at it. */
    fun enable(context: Context) {
        if (!canAuthenticate(context)) return
        enabled = true
        backgroundedAt = 0L
    }

    /**
     * Begin gating the app, if this device can actually authenticate.
     *
     * Safe to call more than once; only the first call takes effect.
     */
    fun install(context: Context) {
        if (observing) return
        if (!canAuthenticate(context)) return

        // Honour the owner's choice. Defaults to on, so this only skips the lock for someone
        // who deliberately turned it off in Settings.
        val wanted = AppLockPreference.load(context)
        observing = true
        enabled = wanted
        // Starts locked: a cold start is exactly the case this protects against.
        _isLocked.value = wanted

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (trustedFlowDepth > 0) return
                backgroundedAt = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                // Re-checked here rather than only at install: the owner can switch the lock
                // off mid-session, and a stale observer would keep re-locking them.
                if (!enabled) return
                if (trustedFlowDepth > 0) return
                // backgroundedAt of 0 means we were never away; nothing to re-lock for.
                if (backgroundedAt == 0L) return
                if (System.currentTimeMillis() - backgroundedAt > GRACE_PERIOD_MS) {
                    _isLocked.value = true
                }
            }
        })
    }

    /** True when the device offers either a biometric or a PIN/pattern/password. */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unlock() {
        _isLocked.value = false
        backgroundedAt = 0L
    }

    /**
     * Mark that a system screen we launched is about to take over, so returning from it is
     * not mistaken for the user having left the app. Always pair with [endTrustedFlow].
     *
     * Counted rather than a boolean: two overlapping launches must not have the first one
     * to finish clear the flag while the second is still on screen.
     */
    fun beginTrustedFlow() {
        trustedFlowDepth++
    }

    fun endTrustedFlow() {
        if (trustedFlowDepth > 0) trustedFlowDepth--
        // Returning from a trusted flow counts as being present, so a photo taken slowly
        // does not trigger a lock the moment the picker closes.
        backgroundedAt = System.currentTimeMillis()
    }

    internal val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
}
