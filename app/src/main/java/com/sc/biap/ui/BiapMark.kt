package com.sc.biap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @file BiapMark.kt
 * @description The BIAP mark - "Aperture".
 *
 * Four bars interlock around a central opening inside a fixed frame: many parts, one
 * rotation, order held. The small squares on the frame edges read as points of
 * coordination. Purely orthogonal and rotationally symmetric.
 *
 * WHY IT IS DRAWN, NOT AN IMAGE:
 *   The mark is exact geometry on a 64-unit grid, so a Canvas reproduces it perfectly at
 *   any size with no asset, no density buckets, and no APK weight. It also inherits the
 *   current colour, which is what makes the single-colour rule enforceable rather than a
 *   note in a document.
 *
 * The identity forbids any clothing or tailoring reference - no needles, thread, spools,
 * hangers or stitches. This is a mechanism, not a garment.
 */

/** Below this the four coordination nodes are dropped; frame and pinwheel carry the mark. */
private const val NODE_VISIBILITY_FLOOR_DP = 24

// The spec grid. Every number below is in these units and scaled at draw time.
private const val GRID = 64f
private const val FRAME_INSET = 4f
private const val FRAME_SIDE = 56f
private const val FRAME_STROKE = 3.5f
private const val FRAME_RADIUS = 3f
private const val BAR = 6f
private const val BAR_LONG = 22f
private const val NODE = 4f

@Composable
fun BiapMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    showNodes: Boolean = size.value >= NODE_VISIBILITY_FLOOR_DP,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "BIAP" }
    ) {
        val u = this.size.minDimension / GRID   // one spec unit in pixels

        drawRoundRect(
            color = color,
            topLeft = Offset(FRAME_INSET * u, FRAME_INSET * u),
            size = Size(FRAME_SIDE * u, FRAME_SIDE * u),
            cornerRadius = CornerRadius(FRAME_RADIUS * u),
            style = Stroke(width = FRAME_STROKE * u),
        )

        // The pinwheel: each bar starts where the previous one ends, so the four read as
        // one rotating mechanism rather than four separate strokes.
        fun bar(x: Float, y: Float, w: Float, h: Float) =
            drawRect(color, Offset(x * u, y * u), Size(w * u, h * u))

        bar(18f, 18f, BAR_LONG, BAR)   // top, running right
        bar(40f, 18f, BAR, BAR_LONG)   // right, running down
        bar(24f, 40f, BAR_LONG, BAR)   // bottom, running left
        bar(18f, 24f, BAR, BAR_LONG)   // left, running up

        if (showNodes) {
            // Held back to 55% so they read as connection points, not as part of the
            // mechanism itself.
            val nodeColor = color.copy(alpha = 0.55f)
            fun node(x: Float, y: Float) =
                drawRect(nodeColor, Offset(x * u, y * u), Size(NODE * u, NODE * u))

            node(30f, 4f)
            node(56f, 30f)
            node(30f, 56f)
            node(4f, 30f)
        }
    }
}

/**
 * Mark on a filled tile, for launcher-icon-shaped contexts.
 *
 * The mark sits at 68% of the tile so the frame keeps its clear space, which the identity
 * spec sets at one bar width.
 */
@Composable
fun BiapAppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    background: Color = MaterialTheme.colorScheme.background,
    markColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        BiapMark(size = size * 0.68f, color = markColor)
    }
}
