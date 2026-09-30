package com.sc.biap.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import com.sc.biap.data.StockState
import com.sc.biap.data.state
import com.sc.biap.data.stats
import com.sc.biap.util.SizeUtils

/**
 * @file CatalogueRow.kt
 * @description One design as a line in an operations index, not a tile in a shop window.
 *
 * WHY A ROW RATHER THAN A PHOTO CARD:
 *   A two-column photo grid spends about half the screen on imagery and leaves one short line
 *   for data. Recognition needs far less than that - an 88dp thumbnail identifies a design you
 *   handle every day - and going full width buys a whole horizontal band per design, which is
 *   exactly what the size matrix needs. It also puts every unit total and rupee range on a
 *   shared alignment, so the numbers can be read straight down the list.
 *
 * STOCK IS ENCODED MORE THAN ONCE, ON PURPOSE:
 *   Fill carries it (secondaryContainer healthy, tertiaryContainer low, nothing at all for
 *   out), the numeral carries it, and corner radius tightens as stock falls. Colour alone
 *   would fail anyone who cannot separate those hues; the numeral alone would need reading
 *   rather than scanning.
 */

/**
 * The thumbnail is PORTRAIT, not square.
 *
 * These photographs are full-length shots of a garment on a model. Cropping them to a square
 * takes the head and the hem - the two ends of the thing you are trying to recognise. A 3:4
 * frame matches how the pictures are actually taken, so the whole outfit survives the crop.
 */
private val ThumbWidth = 104.dp
private val ThumbHeight = 138.dp
private val ThumbRadius = 26.dp
private val CellSize = 52.dp
private val CellWidth = 52.dp
private val CellGap = 6.dp
private val RowRadius = 28.dp


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogueRow(
    design: Design,
    onClick: () -> Unit,
    onSizeClick: (SizeInventory) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (Dp) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stats = remember(design) { design.stats() }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // The press response is shape, not just a ripple: the container tightens under the finger
    // and springs back. fastSpatialSpec is the lowest-damped token in the scheme (0.6), which
    // is what makes it read as physical rather than as a fade.
    val radius by animateDpAsState(
        targetValue = if (pressed) RowRadius - 10.dp else RowRadius,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "rowRadius",
    )

    // Needs-attention rows sit one surface step higher rather than gaining an outline - a
    // border would compete with the size cells, which are the actual signal.
    val container =
        if (stats.needsAttention && !design.isArchived) scheme.surfaceContainerHigh
        else scheme.surfaceContainer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .clip(RoundedCornerShape(ThumbRadius))
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { thumbnail(ThumbWidth) }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = design.name,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            text = design.category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stats.units.toString(),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "UNITS",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PriceRange(
                        min = stats.minPrice,
                        max = stats.maxPrice,
                        unpriced = stats.unpricedSizes,
                        modifier = Modifier.weight(1f),
                    )
                    if (design.isArchived) ArchivedTag() else AttentionTag(stats.outSizes, stats.lowSizes)
                }
            }
        }

        if (design.sizeInventories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SizeMatrix(design.sizeInventories, onSizeClick)
        }
    }
}

/**
 * The size matrix. Every size the design has, ordered along the size ladder (S, M, L, XL,
 * XXL, 3XL...) rather than by the order somebody happened to type them in.
 *
 * Past what fits, the remainder collapses into a single +N cell rather than scrolling
 * or wrapping. Scrolling hides sizes behind a gesture nobody would think to make, and wrapping
 * makes rows different heights - which destroys the shared alignment that is the whole reason
 * this is a row.
 */
@Composable
private fun SizeMatrix(sizes: List<SizeInventory>, onSizeClick: (SizeInventory) -> Unit) {
    // Ordered by the size ladder, not by the order they happened to be typed in. Raw entry
    // order put 3XL and 5XL ahead of L, which reads as broken rather than as a choice.
    // SizeUtils already owns this ladder - the detail screen has always sorted this way.
    val ordered = remember(sizes) { sizes.sortedBy { SizeUtils.getSizeSortWeight(it.size) } }

    // How many cells fit is MEASURED, not assumed. A hardcoded count plus a fixed cell width
    // overflows the moment the phone is narrower than the one it was designed on - and these
    // cells are deliberately fixed-width, so they cannot shrink to compensate.
    BoxWithConstraints {
        val slot = CellWidth + CellGap
        val capacity = ((maxWidth + CellGap) / slot).toInt().coerceAtLeast(1)

        val visible: List<SizeInventory>
        val hidden: Int
        if (ordered.size <= capacity) {
            visible = ordered
            hidden = 0
        } else {
            // One slot has to pay for the "+N" cell itself.
            visible = ordered.take((capacity - 1).coerceAtLeast(1))
            hidden = ordered.size - visible.size
        }

        // Fixed-width cells, left aligned - NOT stretched to fill the row. Stretching makes a
        // design with three sizes draw three fat slabs and one with six draw six narrow ones,
        // so the cells stop being comparable between rows. A fixed cell means a glance down
        // the list compares like with like, which is the entire job of this strip.
        Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            visible.forEach { inventory ->
                SizeCell(inventory = inventory, onClick = { onSizeClick(inventory) })
            }
            if (hidden > 0) OverflowCell(hidden)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizeCell(
    inventory: SizeInventory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val state = inventory.state()
    val outlineVariant = scheme.outlineVariant

    val container = when (state) {
        StockState.HEALTHY -> scheme.secondaryContainer
        StockState.LOW -> scheme.tertiaryContainer
        StockState.OUT -> Color.Transparent
    }
    val content = when (state) {
        StockState.HEALTHY -> scheme.onSecondaryContainer
        StockState.LOW -> scheme.onTertiaryContainer
        StockState.OUT -> scheme.outline
    }
    // Radius tightens as stock falls - a third signal underneath fill and numeral.
    val restingRadius = when (state) {
        StockState.LOW -> 8.dp
        else -> 14.dp
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (pressed) restingRadius + 8.dp else restingRadius,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cellRadius",
    )

    val density = LocalDensity.current
    val dashedOutline = remember(density) {
        with(density) {
            Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
    }

    Column(
        modifier = modifier
            .size(width = CellWidth, height = CellSize)
            .clip(RoundedCornerShape(radius))
            .background(container)
            // Out of stock is drawn as an absence: no fill, a dashed edge, an em-dash. A
            // solid border would read as just another filled state.
            .then(
                if (state == StockState.OUT) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = outlineVariant,
                            cornerRadius = CornerRadius(radius.toPx()),
                            // Hoisted: this used to build a PathEffect and a Stroke inside
                            // the draw call, so every out-of-stock cell allocated two objects
                            // on every frame it was on screen. The dash pattern depends only
                            // on density, so it never needed to be rebuilt.
                            style = dashedOutline,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = inventory.size.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.75f),
            maxLines = 1,
            letterSpacing = 0.6.sp,
        )
        Text(
            // An em-dash rather than a nought: zero is a number you have, and this is a gap.
            text = if (state == StockState.OUT) "—" else inventory.stock.toString(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun OverflowCell(hidden: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = CellWidth, height = CellSize)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$hidden",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The price, and an honest account of what is missing from it.
 *
 * A size with no price is a gap in data entry, not a free garment, so it never enters the
 * range. But silently dropping it would be its own kind of lie - the range would look
 * complete when it is not. So the count of unpriced sizes rides along underneath, quietly,
 * where it prompts without shouting.
 */
@Composable
private fun PriceRange(min: Long?, max: Long?, unpriced: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = when {
                min == null || max == null -> "Not priced yet"
                min == max -> rupees(min)
                else -> "${rupees(min)} – ${rupees(max)}"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (min == null) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unpriced > 0 && min != null) {
            Text(
                text = if (unpriced == 1) "1 size unpriced" else "$unpriced sizes unpriced",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AttentionTag(out: Int, low: Int) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when {
        out > 0 -> Triple("$out OUT", scheme.errorContainer, scheme.onErrorContainer)
        low > 0 -> Triple("$low LOW", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> Triple("HEALTHY", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
    Tag(label, container, content)
}

@Composable
private fun ArchivedTag() {
    val scheme = MaterialTheme.colorScheme
    Tag("ARCHIVED", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
}

@Composable
private fun Tag(label: String, container: Color, content: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Rupees, from a value stored in PAISE, grouped the Indian way - 1,50,000 not 150,000.
 *
 * The store is paise throughout, so this divides by 100 first. Getting that wrong shows every
 * price a hundred times too large, which is exactly the kind of error that looks like a
 * formatting nicety and reads as a catastrophe.
 *
 * Grouped by hand rather than with a locale formatter so the workshop's own figures keep the
 * same shape whatever language the phone is set to. Paise are shown only when there are any -
 * a price of exactly 1,150 should not read as 1,150.00 down a whole column.
 */
fun rupees(paise: Long): String {
    val whole = paise / 100
    val remainder = (paise % 100).toInt()

    val digits = whole.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
    return if (remainder == 0) "₹$grouped" else "₹$grouped.%02d".format(remainder)
}

