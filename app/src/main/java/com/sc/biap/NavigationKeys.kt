package com.sc.biap

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Main : NavKey
@Serializable data object AddDesign : NavKey
/**
 * [focusSize] opens the design scrolled to and highlighting one size row.
 *
 * Null means "just open it". Set when a size cell in the catalogue is tapped, so the answer to
 * "why is XL out?" is one tap away rather than a scroll and a hunt.
 */
@Serializable data class DesignDetail(
    val designId: String,
    val focusSize: String? = null,
) : NavKey
/** Picking pieces off the catalogue to make an order. Reached from the Orders tab's button. */
@Serializable data object NewOrder : NavKey
@Serializable data object Profile : NavKey
@Serializable data object AccessManagement : NavKey
@Serializable data object Settings : NavKey
