package com.example.biap.theme

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
 * Uses PorterDuff BlendMode.DstIn for hardware-accelerated alpha blending.
 */
fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    fadeWidth: Dp = 16.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val width = size.width
        val fadeWidthPx = fadeWidth.toPx()

        if (width <= 0f || fadeWidthPx <= 0f) return@drawWithContent

        val canScrollLeft = scrollState.value > 0
        val canScrollRight = scrollState.value < scrollState.maxValue

        if (!canScrollLeft && !canScrollRight) return@drawWithContent

        val leftColorStop = if (canScrollLeft) Color.Transparent else Color.Black
        val rightColorStop = if (canScrollRight) Color.Transparent else Color.Black

        val leftFadePx = if (canScrollLeft) fadeWidthPx else 0f
        val rightFadePx = if (canScrollRight) fadeWidthPx else 0f

        val leftFadeFraction = (leftFadePx / width).coerceIn(0f, 1f)
        val rightFadeFraction = ((width - rightFadePx) / width).coerceIn(0f, 1f)

        val stops = if (leftFadeFraction < rightFadeFraction) {
            arrayOf(
                0f to leftColorStop,
                leftFadeFraction to Color.Black,
                rightFadeFraction to Color.Black,
                1f to rightColorStop
            )
        } else {
            arrayOf(
                0f to leftColorStop,
                1f to rightColorStop
            )
        }

        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = stops,
                startX = 0f,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }
