package com.sc.biap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * @file VerticalButtonGroup.kt
 * @description A connected button group stacked vertically.
 *
 * WHY THIS EXISTS:
 *   Material 3 Expressive's ButtonGroup is horizontal and cannot be turned: it accepts only
 *   an Arrangement.Horizontal, and its scope offers animateWidth rather than animateHeight,
 *   because the whole interaction is width-based - the pressed button grows sideways and
 *   its neighbours squeeze to make room. Rotating that is not a parameter, it is a
 *   different layout.
 *
 *   What IS orientation-free is the group's *shape language*: an outer corner radius on the
 *   ends of the group and a tighter inner radius where buttons meet. This takes those exact
 *   values from ButtonGroupDefaults and rotates them a quarter turn, so a vertical stack
 *   reads as one connected group in the same visual language.
 *
 *   Nothing here is invented. The radii are read from the real connected-group shapes and
 *   the gap is ButtonGroupDefaults.ConnectedSpaceBetween, so if Google retunes the spec on
 *   the next alpha, this follows automatically.
 *
 *   The press-morph still comes from each Button's own ButtonDefaults.shapes(), which is
 *   orientation-agnostic and needs nothing from here.
 */

/**
 * Shapes for a vertically connected group of [count] buttons.
 *
 * Index 0 gets the rounded top, the last gets the rounded bottom, everything between is
 * tight on both. A single-item group keeps the outer radius all round.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberVerticalButtonGroupShapes(count: Int): List<Shape> {
    // The horizontal leading button is rounded on its START corners and tight on its END
    // corners. Those two sizes are the whole spec; which edge they sit on is the rotation.
    val leading = ButtonGroupDefaults.connectedLeadingButtonShape as? RoundedCornerShape
    val outer: CornerSize = leading?.topStart ?: CornerSize(50)
    val inner: CornerSize = leading?.topEnd ?: CornerSize(8.0f)

    return List(count) { index ->
        val top = if (index == 0) outer else inner
        val bottom = if (index == count - 1) outer else inner
        RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom,
        )
    }
}

/**
 * Lays [content] out as a vertical connected group, using the group's own spacing token.
 *
 * ponytail: a Column, not a custom Layout. The horizontal ButtonGroup needs its own layout
 * to squeeze neighbours on press; a vertical stack of full-width buttons has no width to
 * trade, so there is nothing for a custom layout to do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerticalButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        content()
    }
}
