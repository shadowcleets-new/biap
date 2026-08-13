package com.sc.biap.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.CatalogueSummary
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import com.sc.biap.ui.BiapMark
import com.sc.biap.ui.DesignImageState
import com.sc.biap.ui.rememberDesignImage

/**
 * @file CatalogueList.kt
 * @description Everything around a catalogue row: the chrome, the list, and the empty states.
 *
 * [CatalogueRow] is the row itself and lives next door; this file is the frame it sits in -
 * the title and its status line, the search field, the attention band, the filter chips, the
 * scrolling list, and the three different kinds of nothing the screen can show.
 *
 * THE ONE SATURATED MOMENT:
 *   Only the attention band uses a fully saturated role, and only when something is actually
 *   wrong. When every size is stocked it drops to a tonal container and says so. That is the
 *   whole discipline - if a red screen is normal, a red screen means nothing.
 *
 * FILLED, NEVER OUTLINED:
 *   The chips and the search field are built by hand rather than taken from Material, because
 *   the stock FilterChip and OutlinedTextField both draw borders. On a screen made entirely of
 *   tonal containers a border reads as a different design language, and that single detail is
 *   what made the first build of this screen look unlike the design it came from.
 */

private val BandRadius = 30.dp
private val BadgeSize = 52.dp

/**
 * Every control in the header row is this size.
 *
 * Shared rather than written twice, because it already drifted once: the sync button was
 * 48dp and the avatar 32dp, which read as a misalignment rather than a hierarchy. A single
 * token is the only thing that stops that happening again.
 */
val HeaderActionSize = 48.dp

/**
 * The screen title: a large name with a status line under it, not a Material app-bar title.
 *
 * The design puts the catalogue's own state - how many designs, when it last synced - directly
 * under the name, because that is the first question somebody opening a stock list has. A
 * centred app-bar title has nowhere to put it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogueHeader(
    statusLine: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Portfolio",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1).sp,
            )
            Text(
                text = statusLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

/** A 48dp circular action, filled rather than a bare icon button. */
@Composable
fun CircleAction(
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(HeaderActionSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The search field, filled and pill-shaped.
 *
 * Was an OutlinedTextField, whose border is the same thing that made the chips look wrong:
 * an outline in a screen built from tonal fills reads as a different design language.
 */
@Composable
fun CatalogueSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Name, fabric, colour, size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear search",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.clickable { onQueryChange("") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttentionBand(
    summary: CatalogueSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val clear = summary.isClear

    val container = if (clear) scheme.secondaryContainer else scheme.error
    val content = if (clear) scheme.onSecondaryContainer else scheme.onError

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BandRadius))
            .background(container)
            .then(if (clear) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(BadgeSize)
                .clip(MaterialShapes.Sunny.toShape())
                .background(content.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (clear) "✓" else summary.designsNeedingAttention.toString(),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = content,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (clear) {
                    "All sizes stocked"
                } else {
                    "${summary.designsNeedingAttention} " +
                        (if (summary.designsNeedingAttention == 1) "design needs" else "designs need") +
                        " attention"
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = content,
            )
            Text(
                // Reads in designs throughout, because the line above it counts designs.
                // "sold out" rather than "out of stock" - it is unambiguous about meaning
                // nothing left at all, where "out of stock" invited the size reading.
                text = if (clear) {
                    "Nothing is sold out or running low."
                } else {
                    buildList {
                        if (summary.designsOut > 0) add("${summary.designsOut} sold out")
                        if (summary.designsLow > 0) add("${summary.designsLow} running low")
                        if (isActive) add("showing only these")
                    }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Filter chips: stock state and category on one scrolling row.
 *
 * Deliberately one row rather than two. They are alternative ways of narrowing the same list,
 * and splitting them into "state filters" and "category filters" would imply they combine in
 * some more complicated way than they do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogueFilterRow(
    filters: CatalogueFilters,
    categories: List<String>,
    archivedCount: Int,
    onToggleAttention: () -> Unit,
    onToggleArchived: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CatalogueChip(
            label = "Needs attention",
            selected = filters.attentionOnly,
            onClick = onToggleAttention,
            // State filters select in error; category filters in secondaryContainer. The
            // two kinds share a row, so the colour is what says which kind you just tapped.
            selectedContainer = MaterialTheme.colorScheme.error,
            selectedContent = MaterialTheme.colorScheme.onError,
        )
        categories.forEach { category ->
            CatalogueChip(
                label = category,
                selected = filters.category == category,
                onClick = { onSelectCategory(category) },
            )
        }
        if (archivedCount > 0) {
            CatalogueChip(
                label = "Archived · $archivedCount",
                selected = filters.showArchived,
                onClick = onToggleArchived,
            )
        }
    }
}

/**
 * A filter chip that is FILLED, not outlined.
 *
 * Material's stock FilterChip draws a border when unselected. Against a screen already made of
 * tonal containers that outline reads as a different design language - it is the one thing
 * that made the built screen look unlike the design. Built from a Surface instead so the
 * resting state is a tonal fill with no border at all.
 */
private val ChipHeight = 40.dp
private val ChipRadius = 20.dp

@Composable
private fun CatalogueChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    selectedContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) selectedContainer else scheme.surfaceContainerHigh
    val content = if (selected) selectedContent else scheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun CatalogueList(
    designs: List<Design>,
    onOpenDesign: (Design) -> Unit,
    onOpenSize: (Design, SizeInventory) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { header() }
        items(designs, key = { it.id }) { design ->
            CatalogueRow(
                design = design,
                onClick = { onOpenDesign(design) },
                onSizeClick = { size -> onOpenSize(design, size) },
            ) { size -> DesignThumbnail(design, size) }
        }
    }
}

/** The design's photo, or an honest reason there isn't one. */
@Composable
private fun DesignThumbnail(design: Design, size: Dp) {
    when (val imageState = rememberDesignImage(design.imagePath, design.imageRef).state) {
        is DesignImageState.Ready -> Image(
            painter = rememberAsyncImagePainter(imageState.model),
            contentDescription = design.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        DesignImageState.Loading -> CircularProgressIndicator(
            modifier = Modifier.size(size / 4),
            strokeWidth = 2.dp,
        )

        // A photo exists but this device could not fetch it. Deliberately distinct from
        // "no photo": that difference decides whether a design needs re-shooting or just a
        // working connection.
        DesignImageState.Unavailable -> Icon(
            Icons.Default.CloudOff,
            contentDescription = "Photo unavailable - open the design to retry",
            modifier = Modifier.size(size / 3),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DesignImageState.Absent -> Icon(
            Icons.Default.Inventory,
            contentDescription = "No photo",
            modifier = Modifier.size(size / 3),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nothing to show. Three different nothings, which need three different answers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogueEmptyState(
    query: String,
    filters: CatalogueFilters,
    onAddDesign: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val searching = query.isNotEmpty()
    val filtered = filters.isAny

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (searching) {
                Icon(
                    Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = scheme.onSurfaceVariant,
                )
            } else {
                BiapMark(size = 56.dp, color = scheme.onSurfaceVariant, showNodes = false)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = when {
                searching -> "No matches"
                filters.showArchived -> "Nothing archived"
                filtered -> "Nothing to show"
                else -> "No designs yet"
            },
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = scheme.onSurface,
            letterSpacing = (-0.8).sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                searching -> "Nothing matches \"$query\". Try a name, category, size or karigar."
                filters.showArchived -> "Designs you retire will be kept here."
                filtered -> "No design fits the filters you have on."
                else -> "The catalogue is empty. Add a design with its sizes, prices and " +
                    "karigars to start tracking stock."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )

        Spacer(Modifier.height(28.dp))

        when {
            filtered && !searching -> Button(
                onClick = onClearFilters,
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
                modifier = Modifier.height(56.dp),
            ) { Text("Clear filters") }

            !searching -> Button(
                onClick = onAddDesign,
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
                modifier = Modifier.height(64.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Add first design", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
