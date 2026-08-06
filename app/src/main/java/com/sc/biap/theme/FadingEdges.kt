package com.sc.biap.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a horizontal fading edge gradient when scrolling is active.
 * Uses BlendMode.DstIn for hardware-accelerated alpha blending.
 * The fade width is clamped to half the container so narrow containers
 * can never degenerate into a fully transparent gradient (BUG-21).
 */
fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    fadeWidth: Dp = 16.dp
): Modifier = this
    // ponytail: unconditional offscreen compositing; gate it only if profiling shows cost
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val width = size.width
        if (width <= 0f) return@drawWithContent

        val fadeWidthPx = fadeWidth.toPx().coerceAtMost(width / 2f)
        if (fadeWidthPx <= 0f) return@drawWithContent

        val canScrollLeft = scrollState.value > 0
        val canScrollRight = scrollState.value < scrollState.maxValue

        if (!canScrollLeft && !canScrollRight) return@drawWithContent

        val leftFadePx = if (canScrollLeft) fadeWidthPx else 0f
        val rightFadePx = if (canScrollRight) fadeWidthPx else 0f

        // With fadeWidthPx <= width/2 these fractions are always ordered correctly.
        val leftFadeFraction = leftFadePx / width
        val rightFadeFraction = 1f - (rightFadePx / width)

        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to if (canScrollLeft) Color.Transparent else Color.Black,
                    leftFadeFraction to Color.Black,
                    rightFadeFraction to Color.Black,
                    1f to if (canScrollRight) Color.Transparent else Color.Black
                ),
                startX = 0f,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }
