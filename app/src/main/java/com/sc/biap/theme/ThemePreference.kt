package com.sc.biap.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @file ThemePreference.kt
 * @description Which theme the owner wants, and what to do when they have not said.
 *
 * WHY [ThemeMode.AUTO] EXISTS ALONGSIDE THE THREE THEMES:
 *   "Use AMOLED on battery saver unless the owner chose otherwise" needs a state meaning
 *   "they have not chosen". Without one, the rule can only apply until the first tap and
 *   then never again, with no way back - the setting would quietly stop working and give
 *   the owner no way to restore it. AUTO is that state, and it is reachable again at any
 *   time, so the automatic behaviour is a choice rather than a one-way door.
 */
enum class ThemeMode(val label: String, val description: String) {
    AUTO(
        "Auto",
        "Follows your phone, and switches to AMOLED Black while battery saver is on",
    ),
    LIGHT("Light", "Always light"),
    DARK("Dark", "Always dark"),
    AMOLED("AMOLED Black", "True black, which costs an OLED screen almost no power"),
}

object ThemePreference {

    private const val PREFS = "biap_theme"
    private const val KEY_MODE = "theme_mode"

    private val _mode = MutableStateFlow(ThemeMode.AUTO)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun load(context: Context) {
        val stored = prefs(context).getString(KEY_MODE, null)
        _mode.value = ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.AUTO
    }

    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
