package com.sc.biap.ui.hub

// #region Imports
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.sc.biap.R
// #endregion

/**
 * @file HubIcons.kt
 * @description The drawings on the Hub, and which activity gets which.
 *
 * WHY THE MATCH IS ON WORDS, NOT ON THE EXACT NAME:
 *   Activities are the owner's to rename and add to, so a lookup keyed on the five seeded
 *   strings would lose its icon the moment somebody wrote "Block Printing" instead of
 *   "Printing". Matching on the word inside the name survives that, and it is the same rule
 *   the automatic karigar assignment already uses - so an activity that picks up a printer
 *   also shows the printing block, rather than the two disagreeing.
 *
 * An activity that matches nothing keeps a neutral tools icon rather than an empty gap: the
 * row would otherwise jump left, and a missing icon reads as a fault rather than as "this
 * one is yours".
 */

@DrawableRes
fun activityIconFor(activity: String): Int? {
    val name = activity.lowercase()
    return when {
        listOf("making", "cutting", "stitch", "tailor").any { it in name } ->
            R.drawable.ic_activity_making
        listOf("katha", "kantha").any { it in name } -> R.drawable.ic_activity_katha
        "embroider" in name -> R.drawable.ic_activity_embroidery
        listOf("dye", "dyeing", "rang").any { it in name } -> R.drawable.ic_activity_dyeing
        listOf("print", "block").any { it in name } -> R.drawable.ic_activity_printing
        else -> null
    }
}

/** The activity's drawing, or a neutral stand-in for one the owner added themselves. */
@Composable
fun ActivityIcon(
    activity: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val res = activityIconFor(activity)
    if (res != null) {
        Icon(painterResource(res), contentDescription = null, modifier = modifier, tint = tint)
    } else {
        Icon(Icons.Default.Handyman, contentDescription = null, modifier = modifier, tint = tint)
    }
}

/** A karigar is a person, so the chip says so. */
@Composable
fun KarigarIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(
        painterResource(R.drawable.ic_karigar_person),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
