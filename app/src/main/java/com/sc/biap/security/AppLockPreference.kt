package com.sc.biap.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @file AppLockPreference.kt
 * @description Whether the owner wants the app lock on at all.
 *
 * SEPARATE FROM [AppLock] ON PURPOSE:
 *   AppLock answers "is the catalogue hidden right now", which is process state and dies with
 *   the process. This answers "should it ever hide", which is a decision the owner made and
 *   must survive a reboot. Mixing the two would mean a preference that quietly resets itself.
 *
 * DEFAULTS TO ON:
 *   The app holds cost prices, margins and karigar records. Somebody who never opens settings
 *   should still get the lock; turning it off has to be a deliberate act, not the consequence
 *   of not knowing the setting exists.
 *
 * Plain SharedPreferences rather than the encrypted store: this is a boolean about UI
 * behaviour, not a secret. Encrypting it would imply the answer is worth hiding, and an
 * attacker who can read app-private storage has already lost you more than this flag.
 */
object AppLockPreference {

    private const val PREFS = "biap_app_lock"
    private const val KEY_ENABLED = "lock_enabled"

    private val _enabled = MutableStateFlow(true)

    /** Observable so the settings switch and the lock itself never disagree. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun load(context: Context): Boolean {
        val stored = prefs(context).getBoolean(KEY_ENABLED, true)
        _enabled.value = stored
        return stored
    }

    fun set(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
        // Take effect now rather than at next launch. Switching it off while staring at a
        // lock you cannot dismiss would be a trap.
        if (!enabled) AppLock.disable() else AppLock.enable(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
