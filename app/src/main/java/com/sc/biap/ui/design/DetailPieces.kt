package com.sc.biap.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import com.sc.biap.data.StockState
import com.sc.biap.data.state
import com.sc.biap.data.stats
import com.sc.biap.ui.BiapMark
import com.sc.biap.ui.main.rupees
import com.sc.biap.util.SizeUtils

/**
 * @file DetailPieces.kt
 * @description The parts of the design record: the size matrix, the karigars, the attributes.
 *
 * [TABLE OF CONTENTS]
 * 1. LOCAL CONSTANTS & CONFIG
 * 2. THE SIZE CELL
 * 3. THE MATRIX
 * 4. PILLS, BANNERS & THE NO-PHOTO BLOCK
 * 5. KARIGARS
 * 6. ATTRIBUTES
 *
 * THE MATRIX IS ALWAYS WHOLE, AND THAT IS THE POINT:
 *   This screen used to show one size at a time behind a row of chips, which made it thinner
 *   than the list it was opened from - the catalogue row already showed every size at once.
 *   The row shows the matrix as a compact horizontal band because a row has no more space;
 *   here there is a whole page, so the same matrix runs vertically with everything the band
 *   had to leave out.
 *
 * EVERY ROW IS THE SAME HEIGHT AND THE RUPEES SHARE ONE RIGHT EDGE:
 *   That is what lets the price column be read straight down, which is what makes per-size
 *   pricing legible as a ledger rather than five isolated facts.
 */

// #region 1. Local constants & config

private val CellSize = 46.dp
private val RowRadius = 20.dp
private val GroupRadius = 24.dp
private val AttributeLabelWidth = 78.dp
private val KarigarAvatar = 30.dp

// #endregion

// #region 2. The size cell

/**
 * The catalogue's own cell, at the catalogue's own encoding.
 *
 * Deliberately identical to the one in a catalogue row: fill for healthy against low, a
 * radius that tightens as stock falls, and an em-dash rather than a nought when there is
 * none. A manager learns that shape in the list, and it has to mean the same thing here or
 * it teaches them twice.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailSizeCell(inventory: SizeInventory, modifier: Modifier = Modifier) {
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
    val radius = if (state == StockState.LOW) 8.dp else 14.dp

    Column(
        modifier = modifier
            .size(CellSize)
            .clip(RoundedCornerShape(radius))
            .background(container)
            .then(
                if (state == StockState.OUT) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = outlineVariant,
                            cornerRadius = CornerRadius(radius.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                                ),
                            ),
                        )
                    }
                } else {
                    Modifier
                }
            ),
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
            text = if (state == StockState.OUT) "—" else inventory.stock.toString(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = content,
            maxLines = 1,
        )
    }
}

// #endregion

/**
 * The record's own bar: back, and the two things you can do to a design.
 *
 * Circular tonal buttons rather than bare icons, matching the catalogue header - the sync
 * and avatar controls there are the same 44dp circles, and this screen is one tap away.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadingTopBar(
    archived: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Text(
            text = if (archived) "Archived design" else "Design",
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = if (archived) scheme.onSurfaceVariant else scheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (archived) {
            // Restore reads as a label rather than an icon: it is the way back out of a state
            // somebody may not have meant to enter, and an unlabelled glyph would not say so.
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Restore",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onSecondaryContainer,
                )
            }
            CircleButton(Icons.Default.Delete, "Delete permanently", onDelete, tint = scheme.error)
        } else {
            CircleButton(Icons.Default.Edit, "Edit design", onEdit)
            CircleButton(Icons.Default.Archive, "Archive", onArchive)
        }
    }
}

/**
 * The editing bar. Filled, loud, and carrying the only saturated element on the screen.
 *
 * Editing happens in place on the same screen, which risks ambiguity about which mode you
 * are in - so the signal is deliberately unmissable rather than tasteful. Back becomes
 * discard for the same reason: leaving is not saving.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditingTopBar(canSave: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(scheme.onPrimaryContainer.copy(alpha = 0.10f))
                .clickable(onClick = onDiscard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, "Discard changes", tint = scheme.onPrimaryContainer)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Editing",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onPrimaryContainer,
            )
            Text(
                "UNSAVED CHANGES",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                letterSpacing = 1.1.sp,
            )
        }
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(CircleShape)
                .background(if (canSave) scheme.primary else scheme.onPrimaryContainer.copy(alpha = 0.12f))
                .clickable(enabled = canSave, onClick = onSave)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (canSave) scheme.onPrimary else scheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Save",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = if (canSave) scheme.onPrimary else scheme.onPrimaryContainer.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

/** A section label: small, spaced caps, no rule under it. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.3.sp,
        modifier = modifier.padding(start = 4.dp),
    )
}

// #endregion

// #region 3. The matrix

/**
 * The totals above the matrix: units on the left, money on the right.
 *
 * An unpriced size outranks the stock warnings in the sub-line. A size nobody can quote is a
 * worse problem than a size running low, and it is the one the catalogue cannot show.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MatrixHeader(design: Design, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val stats = design.stats()
    val sizes = design.sizeInventories.size

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stats.units.toString(),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = scheme.onSurface,
                letterSpacing = (-1).sp,
            )
            Text(
                text = "UNITS ACROSS $sizes ${if (sizes == 1) "SIZE" else "SIZES"}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    stats.minPrice == null -> "—"
                    stats.minPrice == stats.maxPrice -> rupees(stats.minPrice!!)
                    else -> "${rupees(stats.minPrice!!)} – ${rupees(stats.maxPrice!!)}"
                },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = scheme.onSurface,
            )
            Text(
                text = when {
                    stats.unpricedSizes > 0 ->
                        "${stats.unpricedSizes} SIZE${if (stats.unpricedSizes == 1) "" else "S"} UNPRICED"
                    stats.outSizes > 0 -> "${stats.outSizes} OUT · ${stats.lowSizes} LOW"
                    stats.lowSizes > 0 -> "${stats.lowSizes} LOW"
                    else -> "ALL HEALTHY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

/**
 * One size: its cell, what it holds in words, and its price hard right.
 *
 * The price is the only thing on this row that has to align with its neighbours, so it is
 * the only thing given a fixed edge. "Price not set" replaces it entirely rather than
 * rendering a zero - a size added before its price was known is an ordinary state, and ₹0 is
 * a claim nobody made.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MatrixRow(
    inventory: SizeInventory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val state = inventory.state()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowRadius))
            // Low sits one surface step up rather than gaining a border: the cell beside it
            // is already carrying the state, and an outline would compete with it.
            .background(
                if (state == StockState.LOW) scheme.surfaceContainerHigh else scheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailSizeCell(inventory)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state == StockState.OUT) "Out of stock" else "${inventory.stock} in stock",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = scheme.onSurface,
            )
            Text(
                text = when (state) {
                    StockState.OUT -> "RESTOCK"
                    StockState.LOW -> "RUNNING LOW"
                    StockState.HEALTHY -> "HEALTHY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state == StockState.OUT) scheme.error else scheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
            )
        }

        if (inventory.price > 0L) {
            Text(
                text = rupees(inventory.price),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onSurface,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "PRICE NOT SET",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onErrorContainer,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

/** Every size, in ladder order, never a subset. */
@Composable
fun SizeMatrix(
    design: Design,
    onSizeClick: (SizeInventory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = design.sizeInventories.sortedBy { SizeUtils.getSizeSortWeight(it.size) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { inventory ->
            MatrixRow(inventory = inventory, onClick = { onSizeClick(inventory) })
        }
    }
}

// #endregion

// #region 4. Pills, banners and the no-photo block

/** The category pill: the one outlined thing on the screen, because it is a label not a control. */
@Composable
fun OutlinePill(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRoundRect(
                    color = scheme.outlineVariant,
                    cornerRadius = CornerRadius(size.height / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

/** States that need saying next to the name: sizes out, sizes unpriced. */
@Composable
fun StatePill(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(scheme.errorContainer)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onErrorContainer)
    }
}

/**
 * The one saturated moment on the record, and only when a size cannot be quoted.
 *
 * States what it costs rather than scolding: a size with no price cannot be sold or quoted.
 * It never blocks saving - refusing would only push somebody to invent a number.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnpricedBanner(sizes: List<String>, modifier: Modifier = Modifier) {
    if (sizes.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.error)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(scheme.onError.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                sizes.size.toString(),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onError,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (sizes.size == 1) {
                    "Size ${sizes.single()} has no price"
                } else {
                    "${sizes.size} sizes have no price"
                },
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = scheme.onError,
            )
            Text(
                text = if (sizes.size == 1) {
                    "It cannot be sold or quoted until a price is set."
                } else {
                    "${sizes.joinToString(", ")} cannot be sold or quoted until priced."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onError.copy(alpha = 0.85f),
            )
        }
    }
}

/** A design nobody has photographed is still a record, so it says so rather than showing a gap. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoPhotoBlock(canAdd: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            BiapMark(size = 40.dp, color = scheme.onSurfaceVariant, showNodes = false)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "No photo on this design",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (canAdd) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Add photo",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** Archiving is reversible and the record is intact, so the notice says both. */
@Composable
fun ArchivedBanner(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Archive,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "Archived. Hidden from the catalogue and from orders. Stock is frozen, not deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

// #endregion

// #region 5. Karigars

/** A karigar chip: their initial, then their name. */
@Composable
fun KarigarChip(name: String, editing: Boolean, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(start = 6.dp, end = if (editing) 8.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(KarigarAvatar)
                .clip(CircleShape)
                .background(scheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // The initial rather than a photograph: the app has never held one, and a
                // generic silhouette would say less than a letter does.
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onTertiaryContainer,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (editing) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $name",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

// #endregion

// #region 6. Attributes

/**
 * Split the custom fields back into the garments they were entered under.
 *
 * The keys carry their garment as a prefix - "Top - Fabric" - which is what lets Fabric
 * exist independently on the top, the bottom and the dupatta. Anything without a known
 * prefix is General, including fields added before the prefixes existed.
 *
 * Blank values are dropped: an empty Fabric row is a field somebody skipped, and printing
 * a dash for every one of them buries the ones that were filled in.
 */
fun groupAttributes(fields: Map<String, String>): List<Pair<String, List<Pair<String, String>>>> {
    val garments = listOf(
        "Top - " to "Top (Kameez)",
        "Bottom - " to "Bottom (Salwar)",
        "Dupatta - " to "Dupatta",
    )
    val filled = fields.filterValues { it.isNotBlank() }
    val grouped = garments.map { (prefix, title) ->
        title to filled
            .filterKeys { it.startsWith(prefix) }
            .map { (key, value) -> key.removePrefix(prefix) to value }
            .sortedBy { it.first }
    }
    val general = filled
        .filterKeys { key -> garments.none { key.startsWith(it.first) } }
        .map { it.key to it.value }
        .sortedBy { it.first }

    return (grouped + ("General" to general)).filter { it.second.isNotEmpty() }
}

/**
 * One garment's attributes, in a tonal group with a fixed label column.
 *
 * The 78dp column is what makes Fabric, Work and Colour read as a set rather than three
 * unrelated lines - the values start on one edge, so the eye runs down them.
 */
@Composable
fun AttributeGroup(
    title: String,
    rows: List<Pair<String, String>>,
    editing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(if (editing) scheme.surfaceContainerHigh else scheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            letterSpacing = 1.3.sp,
        )
        rows.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(AttributeLabelWidth),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// #endregion
