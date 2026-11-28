package com.sc.biap.ui.components

// #region Imports
import androidx.compose.foundation.text.input.InputTransformation
// #endregion

// #region Pure rules
/**
 * Input rules for the state-based text fields (`TextFieldState` + `InputTransformation`).
 *
 * Each rule takes what the field would hold after an edit and returns what it should hold
 * instead, or null to reject the edit outright. Kept as plain functions so they are unit
 * tested without a Compose runtime.
 */
object InputFilters {

    /** Digits only, at most [max] of them. Anything longer is rejected, not truncated. */
    fun digitsUpTo(text: String, max: Int): String? =
        text.filter(Char::isDigit).takeIf { it.length <= max }

    /** Digits only, cut to [max]. For fields where a long paste should still land. */
    fun digitsTruncated(text: String, max: Int): String =
        text.filter(Char::isDigit).take(max)

    /** Exactly one leading '+', then up to three digits. */
    fun dialCode(text: String): String = "+" + digitsTruncated(text, 3)

    /** A rupee amount: digits and one '.', at most two decimals and nine characters. */
    fun price(text: String): String? {
        if (text.count { it == '.' } > 1) return null
        val clean = text.filter { it.isDigit() || it == '.' }
        if (clean.length > 9) return null
        val dot = clean.indexOf('.')
        if (dot != -1 && clean.length - dot - 1 > 2) return null
        return clean
    }
}
// #endregion

// #region Compose adapter
/** Applies a [InputFilters] rule: replaces the edit with the cleaned text, or reverts it. */
fun filterInput(rule: (String) -> String?): InputTransformation = InputTransformation {
    val proposed = asCharSequence().toString()
    when (val clean = rule(proposed)) {
        null -> revertAllChanges()
        proposed -> Unit
        else -> replace(0, length, clean)
    }
}
// #endregion
